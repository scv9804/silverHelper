package com.silverhelper.pipeline.repository;

import com.silverhelper.pipeline.domain.AiProvider;
import com.silverhelper.pipeline.domain.Source;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ai_extraction: 원문 버전 × 모델 × 프롬프트 버전마다 1행 (ADR 0002 §7). 같은 입력은 다시 AI로 보내지 않는다.
 */
@Repository
public class AiExtractionStore {

	public record ExtractionRow(long id, long benefitId, long rawId, AiProvider provider, String model,
			String promptVersion, String resultJson, boolean schemaValid, String errorMessage, OffsetDateTime createdAt) {
	}

	public record NewExtraction(long benefitId, long rawId, AiProvider provider, String model, String promptVersion,
			String resultJson, boolean schemaValid, String errorMessage, Integer inputTokens, Integer outputTokens) {
	}

	/** AI 처리 대기: 혜택과 AI 입력 원문 */
	public record PendingWork(long benefitId, Source source, long rawId) {
	}

	private static final String COLUMNS = """
			id, benefit_id, raw_id, provider, model, prompt_version, result::text as result, schema_valid, error_message, created_at""";

	private static final RowMapper<ExtractionRow> MAPPER = (rs, i) -> new ExtractionRow(rs.getLong("id"),
			rs.getLong("benefit_id"), rs.getLong("raw_id"), AiProvider.valueOf(rs.getString("provider")),
			rs.getString("model"), rs.getString("prompt_version"), rs.getString("result"), rs.getBoolean("schema_valid"),
			rs.getString("error_message"), rs.getObject("created_at", OffsetDateTime.class));

	private final NamedParameterJdbcTemplate jdbc;

	public AiExtractionStore(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<ExtractionRow> find(long rawId, AiProvider provider, String promptVersion) {
		return jdbc.query("select " + COLUMNS + " from ai_extraction where raw_id = :rawId and provider = :provider and prompt_version = :pv",
				Map.of("rawId", rawId, "provider", provider.name(), "pv", promptVersion), MAPPER).stream().findFirst();
	}

	public Optional<ExtractionRow> findById(long id) {
		return jdbc.query("select " + COLUMNS + " from ai_extraction where id = :id", Map.of("id", id), MAPPER)
				.stream().findFirst();
	}

	public List<ExtractionRow> findByIds(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.query("select " + COLUMNS + " from ai_extraction where id in (:ids) order by provider, id",
				Map.of("ids", ids), MAPPER);
	}

	public List<ExtractionRow> findByRaw(long rawId) {
		return jdbc.query("select " + COLUMNS + " from ai_extraction where raw_id = :rawId order by provider, id",
				Map.of("rawId", rawId), MAPPER);
	}

	/** 동시에 같은 행을 넣으려 하면 먼저 들어간 행을 돌려준다 */
	public ExtractionRow insert(NewExtraction e, OffsetDateTime now) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("benefitId", e.benefitId())
				.addValue("rawId", e.rawId())
				.addValue("provider", e.provider().name())
				.addValue("model", e.model())
				.addValue("pv", e.promptVersion())
				.addValue("result", e.resultJson())
				.addValue("valid", e.schemaValid())
				.addValue("error", e.errorMessage())
				.addValue("in", e.inputTokens())
				.addValue("out", e.outputTokens())
				.addValue("now", now);
		jdbc.update("""
				insert into ai_extraction (benefit_id, raw_id, provider, model, prompt_version, result, schema_valid,
				                           error_message, input_tokens, output_tokens, created_at)
				values (:benefitId, :rawId, :provider, :model, :pv, cast(:result as jsonb), :valid,
				        :error, cast(:in as int), cast(:out as int), :now)
				on conflict (raw_id, provider, prompt_version) do nothing
				""", params);
		return find(e.rawId(), e.provider(), e.promptVersion()).orElseThrow();
	}

	/**
	 * AI 처리 대기 목록. 공개 대상(ACTIVE)이고 확정된 해시와 현재 해시가 다르며, 현재 AI 입력 원문에 대해
	 * 아직 판정(검토 항목)이 없는 혜택. 시니어 우선.
	 * 복지로는 상세 원문(DETAIL)이 있어야 대상이 된다(상세를 아직 못 받았으면 다음 실행에서).
	 */
	public List<PendingWork> findPending(int limit) {
		return jdbc.query("""
				select b.id as benefit_id, b.source, r.id as raw_id
				  from benefit b
				  join benefit_raw r on r.benefit_id = b.id and r.content_hash = b.content_hash
				   and r.kind = case when b.source = 'GOV24' then 'LIST' else 'DETAIL' end
				 where b.status = 'ACTIVE'
				   and b.verified_hash is distinct from b.content_hash
				   and not exists (select 1 from review_item ri
				                    where ri.raw_id = r.id
				                      and ri.reason in ('MODEL_MISMATCH', 'SCHEMA_INVALID', 'STRUCTURED_MISMATCH'))
				 order by %s desc, b.id
				 limit :limit
				""".formatted(BenefitStore.SENIOR_PRIORITY), Map.of("limit", limit),
				(rs, i) -> new PendingWork(rs.getLong("benefit_id"), Source.valueOf(rs.getString("source")), rs.getLong("raw_id")));
	}

}
