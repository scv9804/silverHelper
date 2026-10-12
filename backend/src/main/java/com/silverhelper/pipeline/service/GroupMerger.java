package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.domain.ReviewReason;
import com.silverhelper.pipeline.repository.BenefitStore;
import com.silverhelper.pipeline.repository.BenefitStore.GroupMember;
import com.silverhelper.pipeline.repository.GroupCandidateStore;
import com.silverhelper.pipeline.repository.ReviewStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 혜택을 기존 그룹에 묶을지 판단한다 (ADR 0002 §3, public-api.md §10).
 * <ul>
 *   <li>자동 병합: 다른 원천이고, 정규화한 이름이 같고, 지역(시도·시군구)이 같고, 전국 사업이면 소관부처도 같을 때</li>
 *   <li>후보: 이름은 같은데 위 조건이 확실하지 않을 때(한쪽만 지역 사업 등) → benefit_group_candidate + review_item(GROUP_CANDIDATE)</li>
 *   <li>무시: 같은 원천끼리, 또는 둘 다 지역 사업인데 지역이 다를 때(예: 양주시 효행장려지원 ↔ 울진군 효행장려지원)</li>
 * </ul>
 */
@Service
public class GroupMerger {

	private static final Logger log = LoggerFactory.getLogger(GroupMerger.class);

	public record Result(int merged, int candidates) {
	}

	private final BenefitStore benefits;
	private final GroupCandidateStore candidates;
	private final ReviewStore reviews;
	private final Clock clock;

	public GroupMerger(BenefitStore benefits, GroupCandidateStore candidates, ReviewStore reviews, Clock clock) {
		this.benefits = benefits;
		this.candidates = candidates;
		this.reviews = reviews;
		this.clock = clock;
	}

	@Transactional
	public Result mergeNew(Collection<Long> newBenefitIds) {
		if (newBenefitIds.isEmpty()) {
			return new Result(0, 0);
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		List<GroupMember> all = benefits.loadGroupMembers();
		Map<String, List<GroupMember>> byName = all.stream()
				.collect(Collectors.groupingBy(m -> normalizeName(m.name())));
		Map<Long, GroupMember> byId = all.stream().collect(Collectors.toMap(GroupMember::id, m -> m));

		int merged = 0;
		int candidateCount = 0;
		for (Long id : newBenefitIds) {
			GroupMember self = byId.get(id);
			if (self == null) {
				continue;
			}
			List<GroupMember> sameName = byName.getOrDefault(normalizeName(self.name()), List.of()).stream()
					.filter(o -> o.id() != self.id() && o.source() != self.source() && o.groupId() != self.groupId())
					.toList();
			Optional<GroupMember> autoTarget = sameName.stream()
					.filter(o -> sameRegion(self, o) && sameAgency(self, o))
					.min(Comparator.comparingInt((GroupMember o) -> o.source().primaryRank()).thenComparingLong(GroupMember::id));
			if (autoTarget.isPresent()) {
				long from = self.groupId();
				long to = autoTarget.get().groupId();
				benefits.mergeGroups(from, to, now);
				log.info("그룹 자동 병합: 혜택 {} → 그룹 {} ({})", self.id(), to, self.name());
				// 같은 실행 안의 다음 판단이 옮겨진 그룹을 보도록 갱신
				byId.replaceAll((k, m) -> m.groupId() == from ? withGroup(m, to) : m);
				byName.replaceAll((k, list) -> list.stream().map(m -> m.groupId() == from ? withGroup(m, to) : m).toList());
				merged++;
				continue;
			}
			for (GroupMember other : sameName) {
				if (regionConflict(self, other)) {
					continue;
				}
				Optional<Long> candidateId = candidates.insert(self.id(), other.id(), "NAME_EXACT", null, now);
				if (candidateId.isPresent()) {
					candidateCount++;
					reviews.insertIfAbsent(self.id(), null, ReviewReason.GROUP_CANDIDATE, candidateId.get(), null, now);
				}
			}
		}
		if (merged > 0) {
			benefits.recomputePrimaries();
		}
		return new Result(merged, candidateCount);
	}

	/** 공백·가운뎃점·괄호를 지우고 소문자로 (ADR 0002 §3) */
	static String normalizeName(String name) {
		if (name == null) {
			return "";
		}
		return name.replaceAll("[\\s·ㆍ・‧()\\[\\]{}「」『』<>（）]", "").toLowerCase();
	}

	private static boolean sameRegion(GroupMember a, GroupMember b) {
		return Objects.equals(blankToNull(a.ctpvName()), blankToNull(b.ctpvName()))
				&& Objects.equals(blankToNull(a.sggName()), blankToNull(b.sggName()));
	}

	/** 지역 사업은 지역이 같으면 같은 기관으로 본다. 전국 사업은 소관부처(첫 단어)가 같아야 한다 */
	private static boolean sameAgency(GroupMember a, GroupMember b) {
		if (blankToNull(a.ctpvName()) != null) {
			return true;
		}
		String ra = agencyRoot(a.agencyName());
		String rb = agencyRoot(b.agencyName());
		return ra != null && ra.equals(rb);
	}

	/** 둘 다 지역이 있는데 다르면 다른 사업이다 */
	private static boolean regionConflict(GroupMember a, GroupMember b) {
		String ca = blankToNull(a.ctpvName());
		String cb = blankToNull(b.ctpvName());
		if (ca == null || cb == null) {
			return false;
		}
		if (!ca.equals(cb)) {
			return true;
		}
		String sa = blankToNull(a.sggName());
		String sb = blankToNull(b.sggName());
		return sa != null && sb != null && !sa.equals(sb);
	}

	private static String agencyRoot(String agency) {
		if (agency == null || agency.isBlank()) {
			return null;
		}
		return agency.trim().split("\\s+")[0];
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static GroupMember withGroup(GroupMember m, long groupId) {
		return new GroupMember(m.id(), m.source(), m.name(), m.ctpvName(), m.sggName(), m.agencyName(), groupId);
	}

}
