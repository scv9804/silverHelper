package com.silverhelper.pipeline.ai;

/**
 * 추출기 응답. content는 모델이 낸 문자열 그대로이고, JSON 스키마 검증은 호출한 쪽({@link EligibilitySchema})이 한다.
 */
public record AiOutput(String model, String content, Integer inputTokens, Integer outputTokens) {
}
