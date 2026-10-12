package com.silverhelper.pipeline.service;

import com.silverhelper.common.exception.BusinessException;
import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.PageResponse;
import com.silverhelper.pipeline.domain.EligibilityResult;
import com.silverhelper.pipeline.domain.ReviewReason;
import com.silverhelper.pipeline.domain.ReviewStatus;
import com.silverhelper.pipeline.dto.ApproveReviewRequest;
import com.silverhelper.pipeline.dto.EligibilityDto;
import com.silverhelper.pipeline.dto.RejectReviewRequest;
import com.silverhelper.pipeline.dto.ReviewDetailResponse;
import com.silverhelper.pipeline.dto.ReviewSummaryResponse;
import com.silverhelper.pipeline.repository.AiExtractionStore;
import com.silverhelper.pipeline.repository.AiExtractionStore.ExtractionRow;
import com.silverhelper.pipeline.repository.BenefitStore;
import com.silverhelper.pipeline.repository.EligibilityStore;
import com.silverhelper.pipeline.repository.GroupCandidateStore;
import com.silverhelper.pipeline.repository.RawStore;
import com.silverhelper.pipeline.repository.RawStore.RawRow;
import com.silverhelper.pipeline.repository.ReviewStore;
import com.silverhelper.pipeline.repository.ReviewStore.ReviewRow;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 검토 대기열 (openapi.yaml /api/admin/reviews/**).
 * <ul>
 *   <li>AI 판정 사유 승인: eligibility(또는 기준 추출 결과)를 확정 조건으로 저장하고 verified_hash를 검토 대상 원문 해시로</li>
 *   <li>GROUP_CANDIDATE 승인: 두 혜택을 같은 그룹으로 합친다. 반려하면 후보만 REJECTED</li>
 *   <li>SOURCE_REMOVED 승인: 사라짐을 확인한 것으로 기록만 한다</li>
 *   <li>반려: 확정 조건을 바꾸지 않는다. 혜택은 이전 상태 그대로</li>
 * </ul>
 */
@Service
public class ReviewService {

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	private final ReviewStore reviews;
	private final BenefitStore benefits;
	private final RawStore raws;
	private final AiExtractionStore extractions;
	private final EligibilityStore eligibilities;
	private final GroupCandidateStore candidates;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public ReviewService(ReviewStore reviews, BenefitStore benefits, RawStore raws, AiExtractionStore extractions,
			EligibilityStore eligibilities, GroupCandidateStore candidates, JsonMapper jsonMapper, Clock clock) {
		this.reviews = reviews;
		this.benefits = benefits;
		this.raws = raws;
		this.extractions = extractions;
		this.eligibilities = eligibilities;
		this.candidates = candidates;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<ReviewSummaryResponse> list(ReviewStatus status, ReviewReason reason, int page, int size) {
		ReviewStatus s = status == null ? ReviewStatus.PENDING : status;
		long total = reviews.count(s, reason);
		List<ReviewSummaryResponse> content = reviews.list(s, reason, page * size, size).stream()
				.map(ReviewSummaryResponse::from).toList();
		return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
	}

	@Transactional(readOnly = true)
	public ReviewDetailResponse detail(long id) {
		ReviewRow r = reviews.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
		BenefitStore.Summary benefit = benefits.findSummary(r.benefitId()).orElseThrow();
		Optional<RawRow> raw = r.rawId() != null
				? raws.findById(r.rawId())
				: raws.find(benefit.id(), SourceMapper.aiInputKind(benefit.source()), benefit.contentHash());
		Map<String, String> sourceText = raw
				.map(x -> SourceMapper.sourceText(benefit.source(), jsonMapper.readValue(x.payload(), MAP)).fields())
				.orElse(Map.of());
		EligibilityDto current = eligibilities.find(r.benefitId()).map(c -> EligibilityDto.from(c.eligibility())).orElse(null);

		List<Long> ids = Stream.of(r.claudeExtractionId(), r.gptExtractionId()).filter(x -> x != null).toList();
		List<ExtractionRow> rows = !ids.isEmpty() ? extractions.findByIds(ids)
				: r.rawId() != null ? extractions.findByRaw(r.rawId()) : List.of();
		List<ReviewDetailResponse.Extraction> extractionDtos = rows.stream()
				.map(e -> new ReviewDetailResponse.Extraction(e.id(), e.provider(), e.model(), e.promptVersion(),
						e.schemaValid(), e.resultJson() == null ? null : jsonMapper.readTree(e.resultJson()),
						e.errorMessage(), e.createdAt()))
				.toList();

		ReviewDetailResponse.GroupCandidate group = r.groupCandidateId() == null ? null
				: candidates.findById(r.groupCandidateId())
						.map(c -> new ReviewDetailResponse.GroupCandidate(c.targetId(), c.targetName(), c.targetSource(),
								c.method(), c.score()))
						.orElse(null);

		return new ReviewDetailResponse(r.id(), r.benefitId(), r.benefitName(), r.source(), r.reason(), r.status(),
				r.createdAt(), r.resolvedAt(), r.resolvedBy(), benefit.sourceUrl(), sourceText, current, extractionDtos,
				differences(r.detailJson()), group, r.resolutionNote());
	}

	@Transactional
	public ReviewSummaryResponse approve(long id, ApproveReviewRequest request, String actor) {
		ReviewRow r = lockPending(id);
		OffsetDateTime now = OffsetDateTime.now(clock);
		switch (r.reason()) {
			case GROUP_CANDIDATE -> mergeCandidate(r, now);
			case SOURCE_REMOVED -> {
				// 원천에서 사라진 것을 확인. 혜택은 REMOVED로 남는다
			}
			default -> confirmEligibility(r, request, now);
		}
		reviews.resolve(id, ReviewStatus.APPROVED, request.note(), actor, now);
		if (r.reason() == ReviewReason.GROUP_CANDIDATE) {
			openNextCandidate(r.benefitId(), now);
		}
		return ReviewSummaryResponse.from(reviews.findById(id).orElseThrow());
	}

	@Transactional
	public ReviewSummaryResponse reject(long id, RejectReviewRequest request, String actor) {
		ReviewRow r = lockPending(id);
		OffsetDateTime now = OffsetDateTime.now(clock);
		reviews.resolve(id, ReviewStatus.REJECTED, request.note(), actor, now);
		if (r.reason() == ReviewReason.GROUP_CANDIDATE && r.groupCandidateId() != null) {
			candidates.decide(r.groupCandidateId(), "REJECTED", now);
			openNextCandidate(r.benefitId(), now);
		}
		return ReviewSummaryResponse.from(reviews.findById(id).orElseThrow());
	}

	private ReviewRow lockPending(long id) {
		ReviewRow r = reviews.lock(id).orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
		if (r.status() != ReviewStatus.PENDING) {
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_RESOLVED);
		}
		return r;
	}

	private void confirmEligibility(ReviewRow r, ApproveReviewRequest request, OffsetDateTime now) {
		Long baseId = request.baseExtractionId() != null ? request.baseExtractionId()
				: r.claudeExtractionId() != null ? r.claudeExtractionId() : r.gptExtractionId();
		if (baseId == null) {
			throw invalid("기준으로 삼을 추출 결과가 없습니다.");
		}
		ExtractionRow base = extractions.findById(baseId)
				.filter(e -> e.benefitId() == r.benefitId() && (r.rawId() == null || e.rawId() == r.rawId()))
				.orElseThrow(() -> invalid("baseExtractionId가 이 검토 항목의 추출 결과가 아닙니다."));

		EligibilityResult result;
		Map<String, String> fieldSources;
		if (request.eligibility() != null) {
			result = request.eligibility().toResult();
			fieldSources = fieldSources(result, "MANUAL");
		}
		else {
			if (!base.schemaValid() || base.resultJson() == null) {
				throw invalid("기준 추출 결과가 스키마 검증에 실패했습니다. eligibility를 직접 입력해 주세요.");
			}
			result = jsonMapper.readValue(base.resultJson(), EligibilityResult.class);
			fieldSources = fieldSources(result, "AI");
		}
		if (result.minAge() != null && result.maxAge() != null && result.minAge() > result.maxAge()) {
			throw invalid("minAge가 maxAge보다 클 수 없습니다.");
		}
		RawRow raw = raws.findById(r.rawId() != null ? r.rawId() : base.rawId()).orElseThrow();
		eligibilities.upsert(r.benefitId(), base.id(), result, jsonMapper.writeValueAsString(fieldSources), now);
		benefits.setVerifiedHash(r.benefitId(), raw.contentHash(), now);
	}

	private void mergeCandidate(ReviewRow r, OffsetDateTime now) {
		if (r.groupCandidateId() == null) {
			throw invalid("병합 후보가 없습니다.");
		}
		GroupCandidateStore.Candidate c = candidates.findById(r.groupCandidateId()).orElseThrow();
		long from = benefits.findSummary(c.benefitId()).orElseThrow().groupId();
		long to = benefits.findSummary(c.targetId()).orElseThrow().groupId();
		benefits.mergeGroups(from, to, now);
		benefits.recomputePrimaries();
		candidates.decide(c.id(), "MERGED", now);
	}

	/** 혜택당 대기 중인 GROUP_CANDIDATE 검토는 하나라서, 처리하면 다음 후보를 연다 */
	private void openNextCandidate(long benefitId, OffsetDateTime now) {
		candidates.findPendingWithoutReview(benefitId).stream().findFirst()
				.ifPresent(c -> reviews.insertIfAbsent(benefitId, null, ReviewReason.GROUP_CANDIDATE, c.id(), null, now));
	}

	private List<ReviewDetailResponse.Difference> differences(String detailJson) {
		if (detailJson == null) {
			return List.of();
		}
		JsonNode diffs = jsonMapper.readTree(detailJson).path("differences");
		List<ReviewDetailResponse.Difference> result = new ArrayList<>();
		for (JsonNode d : diffs) {
			result.add(new ReviewDetailResponse.Difference(d.path("field").asString(), nullable(d.get("claude")),
					nullable(d.get("gpt")), nullable(d.get("structured"))));
		}
		return result;
	}

	private static JsonNode nullable(JsonNode node) {
		return node == null || node.isNull() ? null : node;
	}

	private static Map<String, String> fieldSources(EligibilityResult result, String source) {
		Map<String, String> sources = new LinkedHashMap<>();
		if ("MANUAL".equals(source)) {
			// 모든 조건이 null이어도 직접 입력 승인이라는 사실을 남긴다.
			sources.put("_approvalSource", "MANUAL");
		}
		EligibilityResult.COMPARED_FIELDS.keySet().stream().sorted()
				.filter(f -> result.value(f) != null)
				.forEach(f -> sources.put(f, source));
		if (result.conditionsSummary() != null) {
			sources.put("conditionsSummary", source);
		}
		return sources;
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(ErrorCode.INVALID_REQUEST, message);
	}

}
