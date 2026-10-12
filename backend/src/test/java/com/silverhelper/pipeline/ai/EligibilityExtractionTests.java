package com.silverhelper.pipeline.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.pipeline.client.WelfareXmlParser;
import com.silverhelper.pipeline.domain.AiProvider;
import com.silverhelper.pipeline.domain.EligibilityResult;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.service.SourceMapper;
import com.silverhelper.pipeline.support.Fixtures;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// 규칙 기반 가짜 추출기와 출력 JSON 스키마 검증
class EligibilityExtractionTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final EligibilitySchema schema = new EligibilitySchema(jsonMapper);

	@Test
	void 연령_장기요양등급_기초연금_규칙() {
		EligibilityResult r = RuleBasedEligibilityExtractor.extractResult(text(
				"서비스명", "어르신 돌봄",
				"지원대상", "65세 이상 장기요양 1~5등급 또는 인지지원등급 판정자 중 기초연금 수급자"));

		assertThat(r.minAge()).isEqualTo(65);
		assertThat(r.ltcRequired()).isTrue();
		assertThat(r.ltcGrades()).containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(r.requiresBasicPension()).isTrue();
		assertThat(r.conditionsSummary()).contains("65세 이상").contains("기초연금 수급자");
	}

	@Test
	void 수급자와_차상위가_함께_나오면_어느_쪽도_필수가_아니다() {
		EligibilityResult r = RuleBasedEligibilityExtractor.extractResult(text(
				"지원대상", "65세 이상 기초생활수급자, 차상위계층 홀몸 어르신"));

		assertThat(r.requiresBasicLivelihood()).isNull();
		assertThat(r.requiresNearPoverty()).isNull();
		assertThat(r.requiresSingleHousehold()).isTrue();
	}

	@Test
	void 언급이_없으면_모두_null() {
		EligibilityResult r = RuleBasedEligibilityExtractor.extractResult(text("지원대상", "원양어업 허가를 받은 자"));

		assertThat(r).isEqualTo(EligibilityResult.EMPTY);
	}

	@Test
	void 실제_원문_기초연금() {
		var detail = WelfareXmlParser.parseDetail(Source.NATIONAL, Fixtures.bytes("national-detail-WLF00001164.xml"));

		EligibilityResult r = RuleBasedEligibilityExtractor.extractResult(SourceMapper.sourceText(Source.NATIONAL, detail.fields()));

		assertThat(r.minAge()).isEqualTo(65);
	}

	@Test
	void 실제_원문_장기요양_본인부담금_감경은_등급_보유가_필요하다() {
		var detail = WelfareXmlParser.parseDetail(Source.NATIONAL, Fixtures.bytes("national-detail-WLF00001177.xml"));

		EligibilityResult r = RuleBasedEligibilityExtractor.extractResult(SourceMapper.sourceText(Source.NATIONAL, detail.fields()));

		assertThat(r.ltcRequired()).isTrue();
		assertThat(r.ltcGrades()).isNull();
	}

	@Test
	void 가짜_추출기_출력은_스키마를_통과한다() {
		AiOutput out = new RuleBasedEligibilityExtractor(AiProvider.CLAUDE, jsonMapper)
				.extract(text("지원대상", "만 70세 이상, 기준 중위소득 50% 이하"));

		EligibilitySchema.Validation v = schema.validate(out.content());

		assertThat(v.valid()).as(v.error()).isTrue();
		assertThat(v.result().minAge()).isEqualTo(70);
		assertThat(v.result().incomeMaxMedianPct()).isEqualTo(50);
		assertThat(out.model()).isEqualTo(RuleBasedEligibilityExtractor.MODEL);
	}

	@Test
	void 코드블록으로_감싼_JSON도_읽는다() {
		String json = jsonMapper.writeValueAsString(EligibilityResult.EMPTY);

		assertThat(schema.validate("```json\n" + json + "\n```").valid()).isTrue();
	}

	@Test
	void 스키마_위반을_잡는다() {
		assertThat(schema.validate("이건 JSON이 아님").valid()).isFalse();
		// 메시지는 JVM 로캘을 따른다. 빠진 필드 이름이 들어 있는지만 본다
		assertThat(schema.validate("{\"minAge\": 65}").error()).contains("maxAge").contains("conditionsSummary");

		Map<String, Object> bad = base();
		bad.put("ltcGrades", List.of(1, 7));
		assertThat(schema.validate(jsonMapper.writeValueAsString(bad)).valid()).isFalse();

		Map<String, Object> extra = base();
		extra.put("unknown", true);
		assertThat(schema.validate(jsonMapper.writeValueAsString(extra)).valid()).isFalse();

		Map<String, Object> reversed = base();
		reversed.put("minAge", 80);
		reversed.put("maxAge", 65);
		EligibilitySchema.Validation v = schema.validate(jsonMapper.writeValueAsString(reversed));
		assertThat(v.valid()).isFalse();
		assertThat(v.json()).isNotNull();
	}

	private Map<String, Object> base() {
		Map<String, Object> m = new LinkedHashMap<>();
		for (String field : EligibilityResult.COMPARED_FIELDS.keySet()) {
			m.put(field, null);
		}
		m.put("conditionsSummary", null);
		return m;
	}

	private static SourceText text(String... kv) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			fields.put(kv[i], kv[i + 1]);
		}
		return new SourceText(fields);
	}

}
