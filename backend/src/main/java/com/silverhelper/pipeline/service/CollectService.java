package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.client.ApiQuota;
import com.silverhelper.pipeline.client.Gov24Client;
import com.silverhelper.pipeline.client.PublicApiException;
import com.silverhelper.pipeline.client.QuotaExceededException;
import com.silverhelper.pipeline.client.SourcePage;
import com.silverhelper.pipeline.client.SourceRecord;
import com.silverhelper.pipeline.client.WelfareClient;
import com.silverhelper.pipeline.config.PipelineProperties;
import com.silverhelper.pipeline.domain.CollectRunStatus;
import com.silverhelper.pipeline.domain.RawKind;
import com.silverhelper.pipeline.domain.ReviewReason;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.repository.BenefitStore;
import com.silverhelper.pipeline.repository.BenefitStore.Existing;
import com.silverhelper.pipeline.repository.BenefitStore.Target;
import com.silverhelper.pipeline.repository.CollectRunStore;
import com.silverhelper.pipeline.repository.RawStore;
import com.silverhelper.pipeline.repository.ReviewStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 원천 1개 수집: 목록 전체 → 정규화 해시로 신규/변경/사라짐 판정 → 저장 → 상세(복지로)·지원조건(정부24) → 그룹 병합.
 * <ul>
 *   <li>목록은 매번 전체를 받는다(A 1회, B 5회, C 11회). 목록을 끝까지 받은 경우에만 사라짐을 판정한다</li>
 *   <li>상세는 신규이거나 목록 내용이 바뀐 항목만, 시니어 우선으로 오늘 남은 한도만큼 받는다. 못 받은 것은 다음 날 이어서</li>
 *   <li>한도 초과(22)나 키 오류를 만나면 이 원천의 남은 호출을 그만둔다</li>
 *   <li>항목 저장은 항목마다 트랜잭션. 중간에 멈춰도 저장된 항목은 남고 다음 실행이 이어받는다</li>
 * </ul>
 */
@Service
public class CollectService {

	private static final Logger log = LoggerFactory.getLogger(CollectService.class);

	public record Report(Source source, CollectRunStatus status, CollectRunStore.Counts counts, List<Long> newIds,
			int detailsPending, List<String> errors) {
	}

	private static final class Tally {
		int apiCalls;
		int seen;
		int created;
		int changed;
		int removed;
		final List<String> errors = new ArrayList<>();

		CollectRunStore.Counts counts() {
			return new CollectRunStore.Counts(apiCalls, seen, created, changed, removed);
		}
	}

	private record ListResult(List<SourceRecord> items, boolean complete) {
	}

	private final Map<Source, WelfareClient> welfareClients = new EnumMap<>(Source.class);
	private final Gov24Client gov24Client;
	private final ApiQuota quota;
	private final BenefitStore benefits;
	private final RawStore raws;
	private final CollectRunStore runs;
	private final ReviewStore reviews;
	private final GroupMerger groupMerger;
	private final PipelineProperties properties;
	private final JsonMapper jsonMapper;
	private final Clock clock;
	private final TransactionTemplate tx;

	public CollectService(ObjectProvider<WelfareClient> welfareClients, Gov24Client gov24Client, ApiQuota quota,
			BenefitStore benefits, RawStore raws, CollectRunStore runs, ReviewStore reviews, GroupMerger groupMerger,
			PipelineProperties properties, JsonMapper jsonMapper, Clock clock, TransactionTemplate tx) {
		// 같은 원천 클라이언트가 여럿이면 @Order가 앞선 것 (테스트 대체 구현)
		welfareClients.orderedStream().forEach(c -> this.welfareClients.putIfAbsent(c.source(), c));
		this.gov24Client = gov24Client;
		this.quota = quota;
		this.benefits = benefits;
		this.raws = raws;
		this.runs = runs;
		this.reviews = reviews;
		this.groupMerger = groupMerger;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.tx = tx;
	}

	public Report collect(Source source) {
		OffsetDateTime startedAt = OffsetDateTime.now(clock);
		long runId = runs.start(source, startedAt);
		Tally tally = new Tally();
		List<Long> newIds = new ArrayList<>();
		CollectRunStatus status = CollectRunStatus.SUCCEEDED;
		int detailsPending = 0;
		try {
			ListResult list = fetchList(source, tally);
			if (!list.complete()) {
				status = CollectRunStatus.PARTIAL;
			}
			Set<String> seen = saveList(source, list.items(), tally, newIds);
			if (!tally.errors.isEmpty()) {
				status = CollectRunStatus.PARTIAL;
			}
			if (list.complete() && !seen.isEmpty() && tally.errors.isEmpty()) {
				markRemoved(source, seen, tally);
			}
			detailsPending = source == Source.GOV24 ? refreshConditions(tally) : refreshDetails(source, tally);
			if (detailsPending > 0) {
				status = CollectRunStatus.PARTIAL;
			}
			benefits.recomputePrimaries();
			GroupMerger.Result merge = groupMerger.mergePending();
			if (list.items().isEmpty() && !list.complete()) {
				status = CollectRunStatus.FAILED;
			}
			log.info("{} 수집 {}: 목록 {}건, 신규 {}, 변경 {}, 사라짐 {}, 상세 남음 {}, 병합 {}, 병합 후보 {}, 호출 {}",
					source, status, tally.seen, tally.created, tally.changed, tally.removed, detailsPending,
					merge.merged(), merge.candidates(), tally.apiCalls);
		}
		catch (RuntimeException e) {
			status = CollectRunStatus.FAILED;
			tally.errors.add(e.getMessage());
			log.error("{} 수집 실패", source, e);
		}
		String error = tally.errors.isEmpty() ? null : String.join("\n", tally.errors.subList(0, Math.min(20, tally.errors.size())));
		runs.finish(runId, status, tally.counts(), error, OffsetDateTime.now(clock));
		return new Report(source, status, tally.counts(), newIds, detailsPending, List.copyOf(tally.errors));
	}

	private ListResult fetchList(Source source, Tally tally) {
		List<SourceRecord> items = new ArrayList<>();
		int pageSize = switch (source) {
			case NATIONAL -> properties.publicData().nationalPageSize();
			case LOCAL -> properties.publicData().localPageSize();
			case GOV24 -> properties.publicData().gov24PageSize();
		};
		long total = Long.MAX_VALUE;
		int page = 1;
		try {
			while (items.size() < total) {
				SourcePage result = source == Source.GOV24
						? gov24Client.serviceList(page, pageSize)
						: welfareClient(source).list(page, pageSize);
				tally.apiCalls++;
				total = result.totalCount();
				if (result.items().isEmpty()) {
					return new ListResult(items, items.size() >= total && !items.isEmpty());
				}
				items.addAll(result.items());
				page++;
			}
			return new ListResult(items, !items.isEmpty() && items.size() >= total);
		}
		catch (PublicApiException e) {
			if (!(e instanceof QuotaExceededException)) {
				tally.apiCalls++;
			}
			tally.errors.add("목록 " + page + "페이지: " + e.getMessage());
			log.warn("{} 목록 {}페이지 실패, 받은 {}건까지만 처리: {}", source, page, items.size(), e.getMessage());
			return new ListResult(items, false);
		}
	}

	private Set<String> saveList(Source source, List<SourceRecord> items, Tally tally, List<Long> newIds) {
		Map<String, Existing> existing = benefits.loadBySource(source);
		Set<String> seen = new LinkedHashSet<>();
		OffsetDateTime now = OffsetDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		for (SourceRecord item : items) {
			if (!SourceMapper.accepts(source, item)) {
				continue;
			}
			String sourceId = SourceMapper.sourceId(source, item);
			if (!seen.add(sourceId)) {
				continue;
			}
			tally.seen++;
			try {
				tx.executeWithoutResult(s -> saveListItem(source, sourceId, item, existing.get(sourceId), now, today, tally, newIds));
			}
			catch (RuntimeException e) {
				tally.errors.add(sourceId + " 저장 실패: " + e.getMessage());
				log.warn("{} {} 저장 실패", source, sourceId, e);
			}
		}
		return seen;
	}

	private void saveListItem(Source source, String sourceId, SourceRecord item, Existing existing,
			OffsetDateTime now, LocalDate today, Tally tally, List<Long> newIds) {
		String hash = ContentHasher.hash(source, RawKind.LIST, item.fields());
		String payload = jsonMapper.writeValueAsString(item.fields());
		var listFields = SourceMapper.listFields(source, item);
		if (existing == null) {
			long id = benefits.insert(source, sourceId, listFields, hash, now);
			raws.upsert(id, RawKind.LIST, hash, payload, item.rawBody(), now);
			if (source == Source.GOV24) {
				// 정부24는 목록에 원문이 있어 목록 해시가 곧 AI 입력 해시다
				benefits.applyDetail(id, SourceMapper.detailFields(source, item), now, today);
			}
			tally.created++;
			newIds.add(id);
			return;
		}
		boolean changed = !hash.equals(existing.listHash());
		benefits.updateList(existing.id(), listFields, now, today);
		raws.upsert(existing.id(), RawKind.LIST, hash, payload, item.rawBody(), now);
		if (changed) {
			tally.changed++;
			if (source == Source.GOV24) {
				benefits.applyDetail(existing.id(), SourceMapper.detailFields(source, item), now, today);
				benefits.setContentHash(existing.id(), hash);
			}
			else {
				// 목록 버전 A→B→A도 상세를 다시 받아야 한다. 상세 저장 때 해시를 복원한다.
				benefits.invalidateDetail(existing.id());
			}
		}
		if ("REMOVED".equals(existing.status())) {
			reviews.closePending(existing.id(), ReviewReason.SOURCE_REMOVED, "원천 목록에 다시 나타남", now);
		}
	}

	private void markRemoved(Source source, Set<String> seen, Tally tally) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		List<Long> removed = tx.execute(s -> {
			List<Long> ids = benefits.markRemoved(source, seen, now);
			for (Long id : ids) {
				reviews.insertIfAbsent(id, null, ReviewReason.SOURCE_REMOVED, null, null, now);
			}
			return ids;
		});
		tally.removed = removed == null ? 0 : removed.size();
	}

	/** 복지로 상세. 남은 대상 수를 돌려준다 */
	private int refreshDetails(Source source, Tally tally) {
		WelfareClient client = welfareClient(source);
		List<Target> targets = benefits.findRefreshTargets(source, RawKind.DETAIL, Integer.MAX_VALUE);
		int done = 0;
		for (Target target : targets) {
			if (quota.remaining(source) <= 0) {
				break;
			}
			SourceRecord detail;
			try {
				detail = client.detail(target.sourceId());
				tally.apiCalls++;
			}
			catch (QuotaExceededException e) {
				tally.errors.add("상세 중단: " + e.getMessage());
				break;
			}
			catch (PublicApiException e) {
				tally.apiCalls++;
				tally.errors.add(target.sourceId() + " 상세: " + e.getMessage());
				if (e.stopsSource()) {
					break;
				}
				continue;
			}
			try {
				tx.executeWithoutResult(s -> saveDetail(source, target.id(), detail));
				done++;
			}
			catch (RuntimeException e) {
				tally.errors.add(target.sourceId() + " 상세 저장 실패: " + e.getMessage());
				log.warn("{} {} 상세 저장 실패", source, target.sourceId(), e);
			}
		}
		return targets.size() - done;
	}

	private void saveDetail(Source source, long benefitId, SourceRecord detail) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		String hash = ContentHasher.hash(source, RawKind.DETAIL, detail.fields());
		benefits.applyDetail(benefitId, SourceMapper.detailFields(source, detail), now, now.toLocalDate());
		raws.upsert(benefitId, RawKind.DETAIL, hash, jsonMapper.writeValueAsString(detail.fields()), detail.rawBody(), now);
		benefits.setContentHash(benefitId, hash);
	}

	/**
	 * 정부24 지원조건(구조화). 대상이 전체 페이지 수보다 많으면 전체를 페이지로 받고(첫 적재 11회),
	 * 적으면 1건씩 받는다. 남은 대상 수를 돌려준다.
	 */
	private int refreshConditions(Tally tally) {
		List<Target> targets = benefits.findRefreshTargets(Source.GOV24, RawKind.CONDITIONS, Integer.MAX_VALUE);
		if (targets.isEmpty()) {
			return 0;
		}
		Map<String, Long> remaining = new HashMap<>();
		targets.forEach(t -> remaining.put(t.sourceId(), t.id()));
		int perPage = properties.publicData().gov24PageSize();
		int bulkCalls = (int) Math.ceil((double) Math.max(tally.seen, 1) / perPage);
		try {
			if (targets.size() > bulkCalls) {
				long total = Long.MAX_VALUE;
				int fetched = 0;
				for (int page = 1; fetched < total; page++) {
					SourcePage result = gov24Client.supportConditions(page, perPage);
					tally.apiCalls++;
					total = result.totalCount();
					if (result.items().isEmpty()) {
						break;
					}
					fetched += result.items().size();
					result.items().forEach(r -> saveConditions(r, remaining));
				}
				// 지원조건이 없는 서비스: 빈 조건으로 기록해 매일 다시 찾지 않게 한다
				List.copyOf(remaining.keySet()).forEach(id -> saveConditions(emptyConditions(id), remaining));
			}
			else {
				for (Target target : targets) {
					SourcePage result = gov24Client.supportConditionsOf(target.sourceId());
					tally.apiCalls++;
					SourceRecord found = result.items().stream()
							.filter(r -> target.sourceId().equals(r.text("서비스ID")))
							.findFirst()
							.orElse(emptyConditions(target.sourceId()));
					saveConditions(found, remaining);
				}
			}
		}
		catch (PublicApiException e) {
			if (!(e instanceof QuotaExceededException)) {
				tally.apiCalls++;
			}
			tally.errors.add("지원조건 중단: " + e.getMessage());
		}
		return remaining.size();
	}

	private void saveConditions(SourceRecord record, Map<String, Long> remaining) {
		Long benefitId = remaining.remove(record.text("서비스ID"));
		if (benefitId == null) {
			return;
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		tx.executeWithoutResult(s -> raws.upsert(benefitId, RawKind.CONDITIONS, ContentHasher.hash(Source.GOV24, RawKind.CONDITIONS, record.fields()),
				jsonMapper.writeValueAsString(record.fields()), record.rawBody(), now));
	}

	private static SourceRecord emptyConditions(String serviceId) {
		return new SourceRecord(Map.of("서비스ID", serviceId), "{}");
	}

	private WelfareClient welfareClient(Source source) {
		WelfareClient client = welfareClients.get(source);
		if (client == null) {
			throw new IllegalStateException(source + " 클라이언트가 없습니다");
		}
		return client;
	}

}
