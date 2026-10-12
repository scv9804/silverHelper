package com.silverhelper.pipeline.ai;

import com.silverhelper.pipeline.domain.AiProvider;

/**
 * 원문에서 자격 조건을 JSON으로 추출한다. Claude용 1개, GPT용 1개가 등록되어 교차검증한다.
 * API 키가 없으면 {@link RuleBasedEligibilityExtractor}, 있으면 {@link ChatClientEligibilityExtractor}.
 * 전송 오류는 예외로 던진다(저장하지 않고 다음 실행에 다시 시도).
 */
public interface EligibilityExtractor {

	AiProvider provider();

	/** ai_extraction.prompt_version. 같은 원문 × 모델 × 버전은 다시 돌리지 않는다 */
	String promptVersion();

	AiOutput extract(SourceText text);

}
