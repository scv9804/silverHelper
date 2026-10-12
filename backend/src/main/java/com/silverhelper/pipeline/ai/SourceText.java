package com.silverhelper.pipeline.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 입력 원문. 키는 사람이 읽는 항목명(서비스명, 지원대상, 선정기준 …), 값은 정규화 전 원문.
 * 관리자 검토 화면의 sourceText로도 그대로 쓴다.
 */
public record SourceText(Map<String, String> fields) {

	public SourceText {
		fields = new LinkedHashMap<>(fields);
	}

	/** 모든 항목을 한 문자열로. 규칙 추출과 프롬프트에 쓴다 */
	public String joined() {
		return fields.entrySet().stream()
				.map(e -> "[" + e.getKey() + "]\n" + e.getValue())
				.collect(Collectors.joining("\n\n"));
	}

	public String get(String key) {
		return fields.get(key);
	}

}
