package com.silverhelper.pipeline.config;

import com.silverhelper.pipeline.ai.ChatClientEligibilityExtractor;
import com.silverhelper.pipeline.ai.EligibilityExtractor;
import com.silverhelper.pipeline.ai.RuleBasedEligibilityExtractor;
import com.silverhelper.pipeline.domain.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자격 조건 추출기 선택. provider마다 하나씩 등록한다.
 * <ul>
 *   <li>spring.ai.anthropic.api-key(또는 openai)가 있고 pipeline.ai.real-enabled=true → Spring AI ChatClient로 실제 모델 호출</li>
 *   <li>키가 비어 있으면 → 규칙 기반 가짜 추출기 (현재 기본)</li>
 * </ul>
 * ChatModel 빈은 키가 있을 때만 꺼낸다(ObjectProvider)라서 키가 없어도 이 설정 때문에 실패하지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class PipelineAiConfig {

	private static final Logger log = LoggerFactory.getLogger(PipelineAiConfig.class);

	@Bean
	EligibilityExtractor claudeEligibilityExtractor(PipelineProperties properties, Environment env,
			ObjectProvider<AnthropicChatModel> chatModel, JsonMapper jsonMapper) {
		return create(AiProvider.CLAUDE, "spring.ai.anthropic.api-key", properties, env, chatModel, jsonMapper);
	}

	@Bean
	EligibilityExtractor gptEligibilityExtractor(PipelineProperties properties, Environment env,
			ObjectProvider<OpenAiChatModel> chatModel, JsonMapper jsonMapper) {
		return create(AiProvider.GPT, "spring.ai.openai.api-key", properties, env, chatModel, jsonMapper);
	}

	private static EligibilityExtractor create(AiProvider provider, String keyProperty, PipelineProperties properties,
			Environment env, ObjectProvider<? extends ChatModel> chatModel, JsonMapper jsonMapper) {
		if (properties.ai().realEnabled() && StringUtils.hasText(env.getProperty(keyProperty))) {
			ChatModel model = chatModel.getIfAvailable();
			if (model != null) {
				log.info("{} 추출기: Spring AI ({})", provider, model.getClass().getSimpleName());
				return new ChatClientEligibilityExtractor(provider, ChatClient.create(model), properties.ai().promptVersion());
			}
			log.warn("{} 키는 있지만 ChatModel 빈이 없어 가짜 추출기를 씁니다", provider);
		}
		else {
			log.info("{} 추출기: 규칙 기반 가짜 ({} 비어 있음 또는 pipeline.ai.real-enabled=false)", provider, keyProperty);
		}
		return new RuleBasedEligibilityExtractor(provider, jsonMapper);
	}

}
