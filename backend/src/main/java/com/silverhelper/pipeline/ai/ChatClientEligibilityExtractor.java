package com.silverhelper.pipeline.ai;

import com.silverhelper.pipeline.domain.AiProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Spring AI ChatClient로 실제 모델(Claude 또는 GPT)을 부르는 추출기.
 * spring.ai.anthropic.api-key / spring.ai.openai.api-key가 있을 때만 등록된다 (PipelineAiConfig).
 * 출력은 JSON 스키마를 프롬프트로 강제하고, 검증은 {@link EligibilitySchema}가 한다.
 */
public class ChatClientEligibilityExtractor implements EligibilityExtractor {

	static final String SYSTEM_PROMPT = """
			너는 한국 공공 복지 서비스 원문에서 신청 자격 조건만 뽑아내는 추출기다.
			규칙:
			- 원문에 명시된 내용만 쓴다. 추측하지 않는다.
			- 원문에 언급이 없거나 제한이 없으면 null이다. false는 원문이 명시적으로 '해당하지 않아야 한다'고 할 때만 쓴다.
			- 여러 조건 중 하나만 충족하면 되는 경우(예: '기초생활수급자 또는 차상위계층')는 어느 하나도 필수가 아니므로 각각 null로 둔다.
			- 연령은 만 나이 기준 정수. '65세 이상'은 minAge=65, '65세 미만'은 maxAge=64.
			- 장기요양 등급: 1~5등급은 숫자, 인지지원등급은 6.
			- 소득 기준이 '기준 중위소득 N% 이하'로 명시된 경우만 incomeMaxMedianPct=N.
			- conditionsSummary는 보호자가 이해할 수 있는 한 줄(100자 이내) 요약.
			- 응답은 아래 JSON 스키마를 만족하는 JSON 객체 하나만 출력한다. 설명, 코드 블록 표시를 붙이지 않는다.

			JSON 스키마:
			""" + EligibilitySchema.JSON;

	private final AiProvider provider;
	private final ChatClient chatClient;
	private final String promptVersion;

	public ChatClientEligibilityExtractor(AiProvider provider, ChatClient chatClient, String promptVersion) {
		this.provider = provider;
		this.chatClient = chatClient;
		this.promptVersion = promptVersion;
	}

	@Override
	public AiProvider provider() {
		return provider;
	}

	@Override
	public String promptVersion() {
		return promptVersion;
	}

	@Override
	public AiOutput extract(SourceText text) {
		ChatResponse response = chatClient.prompt()
				.system(SYSTEM_PROMPT)
				.user("다음 원문에서 자격 조건을 추출해라.\n\n" + text.joined())
				.call()
				.chatResponse();
		if (response == null || response.getResult() == null) {
			throw new IllegalStateException(provider + " 응답이 비어 있습니다");
		}
		ChatResponseMetadata metadata = response.getMetadata();
		Usage usage = metadata == null ? null : metadata.getUsage();
		String model = metadata == null || metadata.getModel() == null || metadata.getModel().isBlank()
				? provider.name().toLowerCase()
				: metadata.getModel();
		return new AiOutput(model, response.getResult().getOutput().getText(),
				usage == null ? null : usage.getPromptTokens(),
				usage == null ? null : usage.getCompletionTokens());
	}

}
