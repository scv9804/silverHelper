package com.silverhelper.pipeline.client;

import java.util.List;
import java.util.Map;

/**
 * 원천 응답의 항목 1건. fields는 요소(필드) 이름 → 값.
 * 값은 문자열·숫자, 또는 반복 요소(applmetList 등)면 {@code List<Map<String, String>>}. 값이 없는 필드는 빠진다.
 *
 * @param rawBody 이 항목의 원문 (XML 조각 또는 JSON 객체 문자열)
 */
public record SourceRecord(Map<String, Object> fields, String rawBody) {

	public String text(String name) {
		Object value = fields.get(name);
		if (value == null || value instanceof List<?>) {
			return null;
		}
		String s = value.toString();
		return s.isBlank() ? null : s;
	}

	@SuppressWarnings("unchecked")
	public List<Map<String, String>> list(String name) {
		Object value = fields.get(name);
		return value instanceof List<?> l ? (List<Map<String, String>>) l : List.of();
	}

}
