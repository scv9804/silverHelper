package com.silverhelper.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverhelper.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Flyway 마이그레이션이 적용되고 주요 제약조건이 동작하는지 확인한다 (ADR 0002)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"spring.ai.anthropic.api-key=test-key",
		"spring.ai.openai.api-key=test-key"
})
@Transactional
class SchemaMigrationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void 마이그레이션이_모두_성공한다() {
		Integer failed = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success = false", Integer.class);
		Integer v1 = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where version = '1'", Integer.class);

		assertThat(failed).isZero();
		assertThat(v1).isEqualTo(1);
	}

	@Test
	void 원천과_원천ID가_같은_혜택은_중복_저장되지_않는다() {
		long groupId = insertGroup();
		insertBenefit(groupId, "NATIONAL", "WLF00001138");

		assertThatThrownBy(() -> insertBenefit(groupId, "NATIONAL", "WLF00001138"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 같은_원천ID라도_원천이_다르면_따로_저장된다() {
		long groupId = insertGroup();
		insertBenefit(groupId, "NATIONAL", "WLF00001138");
		insertBenefit(groupId, "LOCAL", "WLF00001138");

		Integer count = jdbc.queryForObject("select count(*) from benefit where source_id = 'WLF00001138'", Integer.class);
		assertThat(count).isEqualTo(2);
	}

	@Test
	void 정의되지_않은_원천은_거부된다() {
		long groupId = insertGroup();

		assertThatThrownBy(() -> insertBenefit(groupId, "UNKNOWN", "X1"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 장기요양_등급은_1에서_6까지만_허용된다() {
		long extractionId = insertExtraction(insertBenefit(insertGroup(), "NATIONAL", "WLF1"));

		assertThatThrownBy(() -> jdbc.update("""
				insert into benefit_eligibility (benefit_id, extraction_id, ltc_grades, verified_at)
				select benefit_id, id, '{1,7}', now() from ai_extraction where id = ?
				""", extractionId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 대기중_검토항목은_혜택과_사유별로_하나만_둔다() {
		long benefitId = insertBenefit(insertGroup(), "NATIONAL", "WLF1");
		String sql = "insert into review_item (benefit_id, reason) values (?, 'MODEL_MISMATCH')";
		jdbc.update(sql, benefitId);

		assertThatThrownBy(() -> jdbc.update(sql, benefitId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long insertGroup() {
		return jdbc.queryForObject("insert into benefit_group default values returning id", Long.class);
	}

	private long insertBenefit(long groupId, String source, String sourceId) {
		return jdbc.queryForObject("""
				insert into benefit (source, source_id, group_id, name, source_url, content_hash,
				                     first_collected_at, last_collected_at)
				values (?, ?, ?, '테스트 혜택', 'https://www.bokjiro.go.kr', repeat('a', 64), now(), now())
				returning id
				""", Long.class, source, sourceId, groupId);
	}

	private long insertExtraction(long benefitId) {
		long rawId = jdbc.queryForObject("""
				insert into benefit_raw (benefit_id, kind, content_hash, payload, raw_body, first_seen_at, last_seen_at)
				values (?, 'DETAIL', repeat('a', 64), '{}'::jsonb, '<wantedDtl/>', now(), now())
				returning id
				""", Long.class, benefitId);
		return jdbc.queryForObject("""
				insert into ai_extraction (benefit_id, raw_id, provider, model, prompt_version, schema_valid)
				values (?, ?, 'CLAUDE', 'test-model', 'v1', true)
				returning id
				""", Long.class, benefitId, rawId);
	}

}
