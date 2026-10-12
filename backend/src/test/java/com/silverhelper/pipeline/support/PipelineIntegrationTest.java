package com.silverhelper.pipeline.support;

import com.silverhelper.TestcontainersConfiguration;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.service.CollectService;
import com.silverhelper.pipeline.service.ExtractionService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 파이프라인 통합 테스트 공통 설정 (Testcontainers PostgreSQL).
 * 모든 하위 클래스가 같은 설정을 써서 스프링 컨텍스트와 컨테이너를 공유한다. 테스트마다 테이블을 비운다.
 */
@Import({TestcontainersConfiguration.class, PipelineTestConfig.class})
@SpringBootTest(properties = {
		"spring.ai.anthropic.api-key=test-key",
		"spring.ai.openai.api-key=test-key",
		"pipeline.ai.real-enabled=false"
})
@AutoConfigureMockMvc
public abstract class PipelineIntegrationTest {

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected CollectService collectService;

	@Autowired
	protected ExtractionService extractionService;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected FixtureWelfareClient fixtureNationalClient;

	@Autowired
	protected FixtureWelfareClient fixtureLocalClient;

	@Autowired
	protected FixtureGov24Client fixtureGov24Client;

	@Autowired
	protected ScriptedExtractor claudeTestExtractor;

	@Autowired
	protected ScriptedExtractor gptTestExtractor;

	@BeforeEach
	void resetPipelineState() {
		jdbc.execute("""
				truncate benefit_eligibility, review_item, benefit_group_candidate, ai_extraction, benefit_raw,
				         benefit_tag, benefit_reference, benefit, benefit_group, collect_run, api_daily_usage
				restart identity cascade
				""");
		clock.reset();
		fixtureNationalClient.reset();
		fixtureLocalClient.reset();
		fixtureGov24Client.reset();
		claudeTestExtractor.reset();
		gptTestExtractor.reset();
	}

	/** 수집 1회 + AI 처리. 다음 실행과 시각이 겹치지 않게 시계를 1시간 흘린다 */
	protected ExtractionService.Report collectAndExtract(Source source) {
		collectService.collect(source);
		ExtractionService.Report report = extractionService.processPending();
		clock.advance(Duration.ofHours(1));
		return report;
	}

	protected long benefitId(Source source, String sourceId) {
		return jdbc.queryForObject("select id from benefit where source = ? and source_id = ?", Long.class,
				source.name(), sourceId);
	}

	protected Map<String, Object> benefit(Source source, String sourceId) {
		return jdbc.queryForMap("select * from benefit where source = ? and source_id = ?", source.name(), sourceId);
	}

	protected List<Map<String, Object>> reviews(long benefitId) {
		return jdbc.queryForList("select * from review_item where benefit_id = ? order by id", benefitId);
	}

	protected int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

}
