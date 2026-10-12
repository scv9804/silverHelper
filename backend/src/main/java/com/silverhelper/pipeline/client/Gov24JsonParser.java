package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정부24(odcloud) JSON 응답 파서 (public-api.md §4, §6.2).
 * 필터를 걸면 totalCount는 전체 건수 그대로라서 페이징 종료는 matchCount로 판단한다.
 * 오류는 {@code {"code":-4,"msg":"..."}} 형식으로 온다.
 */
public final class Gov24JsonParser {

	private Gov24JsonParser() {
	}

	public static SourcePage parse(JsonMapper jsonMapper, int httpStatus, String body) {
		if (httpStatus == 429) {
			throw new QuotaExceededException(Source.GOV24, "요청 한도 초과 (HTTP 429)");
		}
		JsonNode root;
		try {
			root = jsonMapper.readTree(body);
		}
		catch (Exception e) {
			throw new PublicApiException(Source.GOV24, PublicApiException.Kind.PARSE, String.valueOf(httpStatus),
					"JSON을 읽을 수 없습니다");
		}
		if (root == null || !root.isObject()) {
			throw new PublicApiException(Source.GOV24, PublicApiException.Kind.PARSE, String.valueOf(httpStatus),
					"객체가 아닌 응답");
		}
		if (!root.has("data")) {
			// 인증 오류: HTTP 200 {"code":-4,...} 또는 HTTP 401 {"code":-401,...}
			PublicApiException.Kind kind = root.has("code") ? PublicApiException.Kind.GATEWAY : PublicApiException.Kind.HTTP;
			String code = root.has("code") ? root.get("code").asString() : String.valueOf(httpStatus);
			String msg = root.has("msg") ? root.get("msg").asString() : "알 수 없는 오류";
			throw new PublicApiException(Source.GOV24, kind, code, msg);
		}
		if (httpStatus >= 400) {
			throw new PublicApiException(Source.GOV24, PublicApiException.Kind.HTTP, String.valueOf(httpStatus), "HTTP 오류");
		}
		long matchCount = root.has("matchCount") ? root.get("matchCount").asLong() : root.path("totalCount").asLong();
		List<SourceRecord> items = new ArrayList<>();
		for (JsonNode item : root.get("data")) {
			Map<String, Object> fields = new LinkedHashMap<>();
			for (Map.Entry<String, JsonNode> e : item.properties()) {
				JsonNode v = e.getValue();
				if (v == null || v.isNull()) {
					continue;
				}
				if (v.isNumber()) {
					fields.put(e.getKey(), v.numberValue());
				}
				else if (v.isString()) {
					if (!v.asString().isBlank()) {
						fields.put(e.getKey(), v.asString());
					}
				}
				else {
					fields.put(e.getKey(), v.toString());
				}
			}
			items.add(new SourceRecord(fields, item.toString()));
		}
		return new SourcePage(matchCount, items);
	}

}
