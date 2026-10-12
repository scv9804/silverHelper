package com.silverhelper.pipeline.repository;

import com.silverhelper.pipeline.domain.RawKind;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * benefit_raw: 해시가 다른 원문 버전마다 1행. 같은 내용을 다시 받으면 last_seen_at만 갱신한다 (ADR 0002 §4).
 */
@Repository
public class RawStore {

	public record RawRow(long id, long benefitId, RawKind kind, String contentHash, String payload) {
	}

	private static final RowMapper<RawRow> MAPPER = (rs, i) -> new RawRow(rs.getLong("id"), rs.getLong("benefit_id"),
			RawKind.valueOf(rs.getString("kind")), rs.getString("content_hash"), rs.getString("payload"));

	private final NamedParameterJdbcTemplate jdbc;

	public RawStore(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long upsert(long benefitId, RawKind kind, String contentHash, String payloadJson, String rawBody, OffsetDateTime now) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("benefitId", benefitId)
				.addValue("kind", kind.name())
				.addValue("hash", contentHash)
				.addValue("payload", payloadJson)
				.addValue("rawBody", rawBody == null ? "" : rawBody)
				.addValue("now", now);
		return jdbc.queryForObject("""
				insert into benefit_raw (benefit_id, kind, content_hash, payload, raw_body, first_seen_at, last_seen_at)
				values (:benefitId, :kind, :hash, cast(:payload as jsonb), :rawBody, :now, :now)
				on conflict (benefit_id, kind, content_hash) do update set last_seen_at = excluded.last_seen_at
				returning id
				""", params, Long.class);
	}

	public Optional<RawRow> findById(long id) {
		return jdbc.query("select id, benefit_id, kind, content_hash, payload::text as payload from benefit_raw where id = :id",
				Map.of("id", id), MAPPER).stream().findFirst();
	}

	public Optional<RawRow> findLatest(long benefitId, RawKind kind) {
		return jdbc.query("""
				select id, benefit_id, kind, content_hash, payload::text as payload from benefit_raw
				 where benefit_id = :benefitId and kind = :kind
				 order by last_seen_at desc, id desc limit 1
				""", Map.of("benefitId", benefitId, "kind", kind.name()), MAPPER).stream().findFirst();
	}

	public Optional<RawRow> find(long benefitId, RawKind kind, String contentHash) {
		return jdbc.query("""
				select id, benefit_id, kind, content_hash, payload::text as payload from benefit_raw
				 where benefit_id = :benefitId and kind = :kind and content_hash = :hash
				""", Map.of("benefitId", benefitId, "kind", kind.name(), "hash", contentHash), MAPPER).stream().findFirst();
	}

}
