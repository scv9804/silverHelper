package com.silverhelper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.service.ExtractionService;
import com.silverhelper.pipeline.support.Fixtures;
import com.silverhelper.pipeline.support.PipelineIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 교차검증: 일치 → 공개, 불일치·스키마 오류·구조화 충돌 → 검토 대기열. 기존 공개 조건은 검토 전까지 유지
class CrossValidationFlowTests extends PipelineIntegrationTest {

	static final String AGE_65 = json(65);
	static final String AGE_70 = json(70);

	static String json(Integer minAge) {
		return """
				{"minAge": %s, "maxAge": null, "ltcRequired": null, "ltcGrades": null, "incomeMaxMedianPct": null,
				 "requiresBasicLivelihood": null, "requiresNearPoverty": null, "requiresBasicPension": null,
				 "requiresDisability": null, "requiresVeteran": null, "requiresSingleHousehold": null,
				 "conditionsSummary": "%s세 이상"}
				""".formatted(minAge, minAge);
	}

	@Test
	void 두_모델이_다르면_MODEL_MISMATCH로_보내고_공개하지_않는다() {
		claudeTestExtractor.respond(t -> AGE_65);
		gptTestExtractor.respond(t -> AGE_70);

		ExtractionService.Report report = collectAndExtract(Source.NATIONAL);

		assertThat(report.reviewed()).isEqualTo(3);
		long id = benefitId(Source.NATIONAL, "WLF00001164");
		assertThat(benefit(Source.NATIONAL, "WLF00001164").get("verified_hash")).isNull();
		assertThat(count("select count(*) from benefit_eligibility")).isZero();
		assertThat(reviews(id)).singleElement().satisfies(r -> {
			assertThat(r.get("reason")).isEqualTo("MODEL_MISMATCH");
			assertThat(r.get("claude_extraction_id")).isNotNull();
			assertThat(r.get("gpt_extraction_id")).isNotNull();
			assertThat(r.get("detail").toString()).contains("minAge");
		});

		// 같은 원문은 판정이 났으므로 다시 AI로 보내지 않는다
		assertThat(extractionService.processPending().processed()).isZero();
	}

	@Test
	void 스키마를_어기면_SCHEMA_INVALID이고_원본과_오류를_남긴다() {
		gptTestExtractor.respond(t -> "{\"minAge\": \"예순다섯\"}");

		collectAndExtract(Source.NATIONAL);

		long id = benefitId(Source.NATIONAL, "WLF00001164");
		assertThat(reviews(id)).singleElement().satisfies(r -> assertThat(r.get("reason")).isEqualTo("SCHEMA_INVALID"));
		Map<String, Object> gpt = jdbc.queryForMap(
				"select schema_valid, error_message, result::text as result from ai_extraction where benefit_id = ? and provider = 'GPT'", id);
		assertThat(gpt.get("schema_valid")).isEqualTo(false);
		assertThat(gpt.get("error_message")).isNotNull();
		assertThat(gpt.get("result").toString()).contains("예순다섯");
	}

	@Test
	void JSON이_아닌_응답도_검토로_보낸다() {
		claudeTestExtractor.respond(t -> "죄송하지만 판단할 수 없습니다");

		collectAndExtract(Source.NATIONAL);

		long id = benefitId(Source.NATIONAL, "WLF00001164");
		assertThat(reviews(id)).singleElement().satisfies(r -> assertThat(r.get("reason")).isEqualTo("SCHEMA_INVALID"));
		assertThat(jdbc.queryForObject("select result from ai_extraction where benefit_id = ? and provider = 'CLAUDE'", String.class, id))
				.isNull();
	}

	@Test
	void 구조화_필드와_충돌하면_STRUCTURED_MISMATCH() {
		// 홀몸 어르신 건강음료: 정부24 JA0110 = 65인데 두 모델이 70세라고 함
		claudeTestExtractor.respond(t -> AGE_70);
		gptTestExtractor.respond(t -> AGE_70);

		collectAndExtract(Source.GOV24);

		long id = benefitId(Source.GOV24, "300000000105");
		assertThat(reviews(id)).singleElement().satisfies(r -> {
			assertThat(r.get("reason")).isEqualTo("STRUCTURED_MISMATCH");
			assertThat(r.get("detail").toString()).contains("\"structured\": 65");
		});
		// 원문에 연령 언급이 없는 장기요양 감경(JA0110=19)도 AI가 70이라고 했으므로 충돌
		assertThat(reviews(benefitId(Source.GOV24, "135200005031"))).singleElement()
				.satisfies(r -> assertThat(r.get("reason")).isEqualTo("STRUCTURED_MISMATCH"));
	}

	@Test
	void 원문이_바뀌어_재검증에_실패해도_이전_공개_조건은_유지된다() {
		collectAndExtract(Source.NATIONAL);
		long id = benefitId(Source.NATIONAL, "WLF00001164");
		Object verified = benefit(Source.NATIONAL, "WLF00001164").get("verified_hash");
		assertThat(jdbc.queryForObject("select min_age from benefit_eligibility where benefit_id = ?", Integer.class, id)).isEqualTo(65);

		fixtureNationalClient.setList(fixtureNationalClient.list().replace("안정적인 소득기반을", "더 안정적인 소득기반을"));
		fixtureNationalClient.setDetail("WLF00001164", Fixtures.text("national-detail-WLF00001164.xml").replace("만 65세", "만 70세"));
		claudeTestExtractor.respond(t -> AGE_70);
		gptTestExtractor.respond(t -> AGE_65);
		collectAndExtract(Source.NATIONAL);

		Map<String, Object> b = benefit(Source.NATIONAL, "WLF00001164");
		assertThat(b.get("verified_hash")).isEqualTo(verified);
		assertThat(b.get("content_hash")).isNotEqualTo(verified);
		assertThat(jdbc.queryForObject("select min_age from benefit_eligibility where benefit_id = ?", Integer.class, id)).isEqualTo(65);
		assertThat(reviews(id)).singleElement().satisfies(r -> assertThat(r.get("reason")).isEqualTo("MODEL_MISMATCH"));
	}

	@Test
	void 다음_원문이_검증을_통과하면_대기중이던_검토를_닫는다() {
		claudeTestExtractor.respond(t -> AGE_65);
		gptTestExtractor.respond(t -> AGE_70);
		collectAndExtract(Source.NATIONAL);
		long id = benefitId(Source.NATIONAL, "WLF00001164");

		fixtureNationalClient.setList(fixtureNationalClient.list().replace("안정적인 소득기반을", "더 안정적인 소득기반을"));
		fixtureNationalClient.setDetail("WLF00001164", Fixtures.text("national-detail-WLF00001164.xml").replace("기초연금액을 지급", "기초연금을 지급"));
		gptTestExtractor.respond(t -> AGE_65);
		collectAndExtract(Source.NATIONAL);

		Map<String, Object> b = benefit(Source.NATIONAL, "WLF00001164");
		assertThat(b.get("verified_hash")).isEqualTo(b.get("content_hash"));
		assertThat(reviews(id)).singleElement().satisfies(r -> assertThat(r.get("status")).isEqualTo("RESOLVED"));
	}

}
