package com.silverhelper.chat.config;

import com.silverhelper.chat.service.AnswerModel;
import com.silverhelper.chat.service.DeterministicModels;
import com.silverhelper.chat.service.EmbeddingModel;
import com.silverhelper.chat.service.OpenAiModels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods=false)
public class ChatModelConfig {
    @Bean
    EmbeddingModel embeddingModel(@Value("${spring.ai.openai.api-key:}") String key, JsonMapper mapper) {
        return key.isBlank()?new DeterministicModels():new OpenAiModels(key,mapper);
    }
    @Bean
    AnswerModel answerModel(@Value("${spring.ai.openai.api-key:}") String key, JsonMapper mapper) {
        return key.isBlank()?new DeterministicModels():new OpenAiModels(key,mapper);
    }
}
