package com.silverhelper.pipeline.config;

import com.silverhelper.pipeline.ai.EligibilitySchema;
import com.silverhelper.pipeline.client.ApiQuota;
import com.silverhelper.pipeline.client.Gov24Client;
import com.silverhelper.pipeline.client.RestGov24Client;
import com.silverhelper.pipeline.client.RestWelfareClient;
import com.silverhelper.pipeline.client.WelfareClient;
import com.silverhelper.pipeline.domain.Source;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공 API 클라이언트와 파이프라인 공통 빈.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineConfig {

	private static final Logger log = LoggerFactory.getLogger(PipelineConfig.class);

	@Bean
	WelfareClient nationalWelfareClient(PipelineProperties properties, Environment env, ApiQuota quota) {
		return new RestWelfareClient(Source.NATIONAL, restClientBuilder(properties), properties.publicData().nationalBaseUrl(),
				apiKey(properties, env), quota);
	}

	@Bean
	WelfareClient localWelfareClient(PipelineProperties properties, Environment env, ApiQuota quota) {
		return new RestWelfareClient(Source.LOCAL, restClientBuilder(properties), properties.publicData().localBaseUrl(),
				apiKey(properties, env), quota);
	}

	@Bean
	Gov24Client gov24Client(PipelineProperties properties, Environment env, ApiQuota quota, JsonMapper jsonMapper) {
		return new RestGov24Client(restClientBuilder(properties), properties.publicData().gov24BaseUrl(),
				apiKey(properties, env), quota, jsonMapper);
	}

	@Bean
	EligibilitySchema eligibilitySchema(JsonMapper jsonMapper) {
		return new EligibilitySchema(jsonMapper);
	}

	private static RestClient.Builder restClientBuilder(PipelineProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.publicData().connectTimeout())
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.publicData().readTimeout());
		return RestClient.builder().requestFactory(factory);
	}

	/** pipeline.public-data.api-key가 비어 있으면 PUBLIC_DATA_API_KEY (.env) */
	private static String apiKey(PipelineProperties properties, Environment env) {
		String key = properties.publicData().apiKey();
		if (!StringUtils.hasText(key)) {
			key = env.getProperty("PUBLIC_DATA_API_KEY", "");
		}
		if (!StringUtils.hasText(key)) {
			log.warn("PUBLIC_DATA_API_KEY가 비어 있어 공공 API 호출은 실패합니다");
		}
		return key.trim();
	}

}
