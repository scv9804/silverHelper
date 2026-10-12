package com.silverhelper.pipeline.support;

import com.silverhelper.pipeline.domain.AiProvider;
import com.silverhelper.pipeline.domain.Source;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.json.JsonMapper;

/**
 * 통합 테스트용 대체 구현. 공공 API와 AI를 호출하지 않는다.
 * 파이프라인은 같은 원천·provider의 구현이 여럿이면 @Order가 앞선 것을 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PipelineTestConfig {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

	@Bean
	@Order(0)
	FixtureWelfareClient fixtureNationalClient() {
		return new FixtureWelfareClient(Source.NATIONAL, Fixtures.text("national-list.xml"),
				Map.of("WLF00001164", Fixtures.text("national-detail-WLF00001164.xml")));
	}

	@Bean
	@Order(0)
	FixtureWelfareClient fixtureLocalClient() {
		return new FixtureWelfareClient(Source.LOCAL, Fixtures.text("local-list.xml"), Map.of());
	}

	@Bean
	@Primary
	FixtureGov24Client fixtureGov24Client(JsonMapper jsonMapper) {
		return new FixtureGov24Client(jsonMapper);
	}

	@Bean
	@Order(0)
	ScriptedExtractor claudeTestExtractor(JsonMapper jsonMapper) {
		return new ScriptedExtractor(AiProvider.CLAUDE, jsonMapper);
	}

	@Bean
	@Order(0)
	ScriptedExtractor gptTestExtractor(JsonMapper jsonMapper) {
		return new ScriptedExtractor(AiProvider.GPT, jsonMapper);
	}

}
