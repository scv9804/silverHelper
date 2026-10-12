package com.silverhelper.pipeline.repository;

import com.silverhelper.pipeline.domain.Source;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** benefit_group_candidate: 사람이 확정할 그룹 병합 후보 */
@Repository
public class GroupCandidateStore {

	public record Candidate(long id, long benefitId, long targetId, String targetName, Source targetSource,
			String method, BigDecimal score, String status) {
	}

	private static final String SELECT = """
			select c.id, c.benefit_id, c.target_id, t.name as target_name, t.source as target_source,
			       c.method, c.score, c.status
			  from benefit_group_candidate c join benefit t on t.id = c.target_id
			""";

	private static final RowMapper<Candidate> MAPPER = (rs, i) -> new Candidate(rs.getLong("id"), rs.getLong("benefit_id"),
			rs.getLong("target_id"), rs.getString("target_name"), Source.valueOf(rs.getString("target_source")),
			rs.getString("method"), rs.getBigDecimal("score"), rs.getString("status"));

	private final NamedParameterJdbcTemplate jdbc;

	public GroupCandidateStore(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 이미 있는 쌍이면 empty */
	public Optional<Long> insert(long benefitId, long targetId, String method, BigDecimal score, OffsetDateTime now) {
		return jdbc.queryForList("""
				insert into benefit_group_candidate (benefit_id, target_id, method, score, created_at)
				values (:benefitId, :targetId, :method, :score, :now)
				on conflict (benefit_id, target_id) do nothing
				returning id
				""", new MapSqlParameterSource()
				.addValue("benefitId", benefitId)
				.addValue("targetId", targetId)
				.addValue("method", method)
				.addValue("score", score)
				.addValue("now", now), Long.class).stream().findFirst();
	}

	public Optional<Candidate> findById(long id) {
		return jdbc.query(SELECT + " where c.id = :id", Map.of("id", id), MAPPER).stream().findFirst();
	}

	/** 아직 검토 항목이 없는 대기 후보 (혜택당 대기 중인 GROUP_CANDIDATE 검토는 하나뿐이라 순서대로 연다) */
	public List<Candidate> findPendingWithoutReview(long benefitId) {
		return jdbc.query(SELECT + """
				 where c.benefit_id = :id and c.status = 'PENDING'
				   and not exists (select 1 from review_item r where r.group_candidate_id = c.id)
				 order by c.id
				""", Map.of("id", benefitId), MAPPER);
	}

	public void decide(long id, String status, OffsetDateTime now) {
		jdbc.update("update benefit_group_candidate set status = :status, decided_at = :now where id = :id",
				Map.of("id", id, "status", status, "now", now));
	}

}
