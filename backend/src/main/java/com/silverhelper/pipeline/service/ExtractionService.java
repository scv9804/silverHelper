package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.ai.AiOutput;
import com.silverhelper.pipeline.ai.EligibilityExtractor;
import com.silverhelper.pipeline.ai.EligibilitySchema;
import com.silverhelper.pipeline.ai.SourceText;
import com.silverhelper.pipeline.ai.RuleBasedEligibilityExtractor;
import com.silverhelper.pipeline.config.PipelineProperties;
import com.silverhelper.pipeline.domain.AiProvider;
import com.silverhelper.pipeline.domain.EligibilityResult;
import com.silverhelper.pipeline.domain.RawKind;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.repository.AiExtractionStore;
import com.silverhelper.pipeline.repository.AiExtractionStore.ExtractionRow;
import com.silverhelper.pipeline.repository.AiExtractionStore.NewExtraction;
import com.silverhelper.pipeline.repository.AiExtractionStore.PendingWork;
import com.silverhelper.pipeline.repository.BenefitStore;
import com.silverhelper.pipeline.repository.EligibilityStore;
import com.silverhelper.pipeline.repository.RawStore;
import com.silverhelper.pipeline.repository.RawStore.RawRow;
import com.silverhelper.pipeline.repository.ReviewStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 원문이 바뀐 혜택만 AI로 자격 조건을 추출하고 교차검증한다.
 * <ul>
 *   <li>대상: ACTIVE이고 verified_hash ≠ content_hash이며 현재 원문에 대한 판정이 아직 없는 혜택 (시니어 우선, 실행당 상한)</li>
 *   <li>같은 원문 × 모델 × 프롬프트 버전 결과가 이미 있으면 AI를 다시 부르지 않는다</li>
 *   <li>통과하면 benefit_eligibility upsert + verified_hash 갱신, 아니면 review_item. 기존 공개 조건은 검토 전까지 그대로</li>
 *   <li>AI 호출이 예외로 실패하면 아무것도 저장하지 않고 다음 실행에 다시 시도한다</li>
 * </ul>
 */
@Service
public class ExtractionService {

	private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);
	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	public record Report(int processed, int published, int reviewed, int failed, int aiCalls) {
	}

	private enum Outcome {
		PUBLISHED, REVIEWED
	}

	private final Map<AiProvider, EligibilityExtractor> extractors = new EnumMap<>(AiProvider.class);
	private final AiExtractionStore extractions;
	private final RawStore raws;
	private final BenefitStore benefits;
	private final EligibilityStore eligibilities;
	private final ReviewStore reviews;
	private final EligibilitySchema schema;
	private final PipelineProperties properties;
	private final JsonMapper jsonMapper;
	private final Clock clock;
	private final TransactionTemplate tx;
	private int aiCalls;

	public ExtractionService(ObjectProvider<EligibilityExtractor> extractors, AiExtractionStore extractions, RawStore raws,
			BenefitStore benefits, EligibilityStore eligibilities, ReviewStore reviews, EligibilitySchema schema,
			PipelineProperties properties, JsonMapper jsonMapper, Clock clock, TransactionTemplate tx) {
		// 같은 provider가 여럿이면 @Order가 앞선 것 (테스트 대체 구현)
		extractors.orderedStream().forEach(e -> this.extractors.putIfAbsent(e.provider(), e));
		for (AiProvider p : AiProvider.values()) {
			if (!this.extractors.containsKey(p)) {
				throw new IllegalStateException(p + " 추출기가 등록되지 않았습니다");
			}
		}
		this.extractions = extractions;
		this.raws = raws;
		this.benefits = benefits;
		this.eligibilities = eligibilities;
		this.reviews = reviews;
		this.schema = schema;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.tx = tx;
	}

	public synchronized Report processPending() {
		int revoked = benefits.revokeFakeVerifications();
		if (revoked > 0) {
			log.warn("가짜 추출로 확정된 혜택 {}건의 공개를 회수하고 재처리 대기로 전환", revoked);
		}
		if (extractors.values().stream().anyMatch(RuleBasedEligibilityExtractor.class::isInstance)) {
			log.warn("실제 AI 두 제공자가 준비되지 않아 추출과 공개를 보류합니다");
			return new Report(0, 0, 0, 0, 0);
		}
		List<PendingWork> pending = extractions.findPending(properties.ai().maxPerRun());
		int published = 0;
		int reviewed = 0;
		int failed = 0;
		aiCalls = 0;
		for (PendingWork work : pending) {
			try {
				if (process(work) == Outcome.PUBLISHED) {
					published++;
				}
				else {
					reviewed++;
				}
			}
			catch (RuntimeException e) {
				failed++;
				log.warn("혜택 {} AI 처리 실패, 다음 실행에 다시 시도: {}", work.benefitId(), e.getMessage());
			}
		}
		Report report = new Report(pending.size(), published, reviewed, failed, aiCalls);
		log.info("AI 추출: 대상 {}, 공개 {}, 검토 대기 {}, 실패 {}, AI 호출 {} (Claude {}, GPT {})", report.processed(),
				published, reviewed, failed, aiCalls, describe(AiProvider.CLAUDE), describe(AiProvider.GPT));
		return report;
	}

	private Outcome process(PendingWork work) {
		RawRow raw = raws.findById(work.rawId()).orElseThrow();
		SourceText text = SourceMapper.sourceText(work.source(), readMap(raw.payload()));
		ExtractionRow claude = obtain(AiProvider.CLAUDE, work, text);
		ExtractionRow gpt = obtain(AiProvider.GPT, work, text);
		if (RuleBasedEligibilityExtractor.MODEL.equals(claude.model())
				|| RuleBasedEligibilityExtractor.MODEL.equals(gpt.model())) {
			throw new IllegalStateException("가짜 추출 결과는 공개할 수 없습니다");
		}
		StructuredFacts facts = structuredFacts(work.benefitId(), work.source());
		CrossValidator.Decision decision = CrossValidator.decide(validResult(claude), validResult(gpt), facts);

		OffsetDateTime now = OffsetDateTime.now(clock);
		tx.executeWithoutResult(s -> {
			if (decision.publish()) {
				eligibilities.upsert(work.benefitId(), claude.id(), validResult(claude),
						jsonMapper.writeValueAsString(decision.fieldSources()), now);
				benefits.setVerifiedHash(work.benefitId(), raw.contentHash(), now);
				reviews.closeStaleExtractionReviews(work.benefitId(), null, "새 원문이 교차검증을 통과해 자동으로 닫음", now);
			}
			else {
				reviews.closeStaleExtractionReviews(work.benefitId(), decision.reason(), "새 원문으로 다시 판정함", now);
				reviews.upsertExtractionReview(work.benefitId(), raw.id(), decision.reason(), claude.id(), gpt.id(),
						detailJson(decision, claude, gpt), now);
			}
		});
		return decision.publish() ? Outcome.PUBLISHED : Outcome.REVIEWED;
	}

	private ExtractionRow obtain(AiProvider provider, PendingWork work, SourceText text) {
		EligibilityExtractor extractor = extractors.get(provider);
		ExtractionRow cached = extractions.find(work.rawId(), provider, extractor.promptVersion()).orElse(null);
		if (cached != null) {
			if (RuleBasedEligibilityExtractor.MODEL.equals(cached.model())) {
				throw new IllegalStateException("가짜 추출 결과는 재사용할 수 없습니다. 실제 모델의 프롬프트 버전을 올려야 합니다");
			}
			return cached;
		}
		return extractions.find(work.rawId(), provider, extractor.promptVersion()).orElseGet(() -> {
			AiOutput output = extractor.extract(text);
			aiCalls++;
			EligibilitySchema.Validation v = schema.validate(output.content());
			return extractions.insert(new NewExtraction(work.benefitId(), work.rawId(), provider, output.model(),
					extractor.promptVersion(), v.json() == null ? null : v.json().toString(), v.valid(), v.error(),
					output.inputTokens(), output.outputTokens()), OffsetDateTime.now(clock));
		});
	}

	/** 저장된 결과가 스키마를 통과했으면 값, 아니면 null */
	private EligibilityResult validResult(ExtractionRow row) {
		if (!row.schemaValid() || row.resultJson() == null) {
			return null;
		}
		return jsonMapper.readValue(row.resultJson(), EligibilityResult.class);
	}

	private StructuredFacts structuredFacts(long benefitId, Source source) {
		if (source == Source.GOV24) {
			return raws.findLatest(benefitId, RawKind.CONDITIONS)
					.map(r -> StructuredFacts.ofGov24Conditions(readMap(r.payload())))
					.orElse(StructuredFacts.NONE);
		}
		return StructuredFacts.ofLifeCodes(benefits.lifeCodes(benefitId));
	}

	private String detailJson(CrossValidator.Decision decision, ExtractionRow claude, ExtractionRow gpt) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("differences", decision.differences());
		Map<String, String> errors = new LinkedHashMap<>();
		if (claude.errorMessage() != null) {
			errors.put("CLAUDE", claude.errorMessage());
		}
		if (gpt.errorMessage() != null) {
			errors.put("GPT", gpt.errorMessage());
		}
		if (!errors.isEmpty()) {
			detail.put("schemaErrors", errors);
		}
		return jsonMapper.writeValueAsString(detail);
	}

	private Map<String, Object> readMap(String json) {
		return jsonMapper.readValue(json, MAP);
	}

	private String describe(AiProvider provider) {
		EligibilityExtractor e = extractors.get(provider);
		return e.getClass().getSimpleName() + "/" + e.promptVersion();
	}

}
