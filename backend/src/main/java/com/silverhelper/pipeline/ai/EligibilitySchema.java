package com.silverhelper.pipeline.ai;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.silverhelper.pipeline.domain.EligibilityResult;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 출력 JSON 스키마와 검증. 프롬프트에도 이 스키마를 그대로 넣는다.
 * 필드 의미는 docs/specs/openapi.yaml Eligibility와 같다. null = 조건 없음 또는 원문에 언급 없음.
 */
public class EligibilitySchema {

	public static final String JSON = """
			{
			  "$schema": "https://json-schema.org/draft/2020-12/schema",
			  "type": "object",
			  "additionalProperties": false,
			  "required": ["minAge", "maxAge", "ltcRequired", "ltcGrades", "incomeMaxMedianPct",
			               "requiresBasicLivelihood", "requiresNearPoverty", "requiresBasicPension",
			               "requiresDisability", "requiresVeteran", "requiresSingleHousehold", "conditionsSummary"],
			  "properties": {
			    "minAge": { "type": ["integer", "null"], "minimum": 0, "maximum": 120, "description": "최소 연령(세, 포함)" },
			    "maxAge": { "type": ["integer", "null"], "minimum": 0, "maximum": 120, "description": "최대 연령(세, 포함)" },
			    "ltcRequired": { "type": ["boolean", "null"], "description": "노인장기요양 등급이 있어야 하면 true" },
			    "ltcGrades": {
			      "type": ["array", "null"], "uniqueItems": true,
			      "items": { "type": "integer", "minimum": 1, "maximum": 6 },
			      "description": "허용 장기요양 등급. 1~5등급, 6 = 인지지원등급. 등급 무관이면 null"
			    },
			    "incomeMaxMedianPct": { "type": ["integer", "null"], "minimum": 0, "maximum": 1000, "description": "기준 중위소득 % 상한" },
			    "requiresBasicLivelihood": { "type": ["boolean", "null"], "description": "기초생활수급자여야 하면 true" },
			    "requiresNearPoverty": { "type": ["boolean", "null"], "description": "차상위계층이어야 하면 true" },
			    "requiresBasicPension": { "type": ["boolean", "null"], "description": "기초연금 수급자여야 하면 true" },
			    "requiresDisability": { "type": ["boolean", "null"], "description": "등록 장애인이어야 하면 true" },
			    "requiresVeteran": { "type": ["boolean", "null"], "description": "국가보훈대상자여야 하면 true" },
			    "requiresSingleHousehold": { "type": ["boolean", "null"], "description": "1인 가구(독거)여야 하면 true" },
			    "conditionsSummary": { "type": ["string", "null"], "maxLength": 300, "description": "자격 조건 한 줄 요약" }
			  }
			}
			""";

	public record Validation(boolean valid, JsonNode json, EligibilityResult result, String error) {
	}

	private final JsonMapper jsonMapper;
	private final Schema schema;

	public EligibilitySchema(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
		this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(JSON);
	}

	/**
	 * 모델 출력 문자열을 검증한다. 코드 블록(```json)으로 감싸 와도 벗겨서 본다.
	 * json은 JSON으로 읽을 수 있으면 스키마가 틀려도 채운다(ai_extraction.result에 원본으로 남기기 위해).
	 */
	public Validation validate(String content) {
		if (content == null || content.isBlank()) {
			return new Validation(false, null, null, "빈 응답");
		}
		JsonNode json;
		try {
			json = jsonMapper.readTree(stripFence(content));
		}
		catch (Exception e) {
			return new Validation(false, null, null, "JSON이 아님: " + abbreviate(content));
		}
		List<String> errors = new ArrayList<>(schema.validate(json).stream().map(Error::toString).toList());
		if (errors.isEmpty()) {
			JsonNode min = json.get("minAge");
			JsonNode max = json.get("maxAge");
			if (min.isIntegralNumber() && max.isIntegralNumber() && min.asInt() > max.asInt()) {
				errors.add("minAge가 maxAge보다 큼");
			}
		}
		if (!errors.isEmpty()) {
			return new Validation(false, json, null, String.join("; ", errors));
		}
		return new Validation(true, json, jsonMapper.treeToValue(json, EligibilityResult.class), null);
	}

	private static String stripFence(String content) {
		String s = content.trim();
		if (s.startsWith("```")) {
			int firstNewline = s.indexOf('\n');
			int lastFence = s.lastIndexOf("```");
			if (firstNewline > 0 && lastFence > firstNewline) {
				return s.substring(firstNewline + 1, lastFence).trim();
			}
		}
		return s;
	}

	private static String abbreviate(String s) {
		return s.length() <= 200 ? s : s.substring(0, 200) + "…";
	}

}
