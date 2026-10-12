package com.silverhelper.pipeline.repository;

import com.silverhelper.pipeline.domain.ReviewReason;
import com.silverhelper.pipeline.domain.ReviewStatus;
import com.silverhelper.pipeline.domain.Source;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * review_item: 검토 대기열. PENDING 항목은 혜택·사유별로 하나만 있다(부분 유일 인덱스 uk_review_pending).
 */
@Repository
public class ReviewStore {

	public record ReviewRow(long id, long benefitId, String benefitName, Source source, Long rawId, ReviewReason reason,
			Long claudeExtractionId, Long gptExtractionId, Long groupCandidateId, String detailJson, ReviewStatus status,
			String resolutionNote, String resolvedBy, OffsetDateTime resolvedAt, OffsetDateTime createdAt) {
	}

	private static final String SELECT = """
			select r.id, r.benefit_id, b.name as benefit_name, b.source, r.raw_id, r.reason, r.claude_extraction_id,
			       r.gpt_extraction_id, r.group_candidate_id, r.detail::text as detail, r.status, r.resolution_note,
			       r.resolved_by, r.resolved_at, r.created_at
			  from review_item r join benefit b on b.id = r.benefit_id
			""";

	private static final RowMapper<ReviewRow> MAPPER = (rs, i) -> new ReviewRow(rs.getLong("id"), rs.getLong("benefit_id"),
			rs.getString("benefit_name"), Source.valueOf(rs.getString("source")), longOrNull(rs, "raw_id"),
			ReviewReason.valueOf(rs.getString("reason")), longOrNull(rs, "claude_extraction_id"),
			longOrNull(rs, "gpt_extraction_id"), longOrNull(rs, "group_candidate_id"), rs.getString("detail"),
			ReviewStatus.valueOf(rs.getString("status")), rs.getString("resolution_note"), rs.getString("resolved_by"),
			rs.getObject("resolved_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class));

	private final NamedParameterJdbcTemplate jdbc;

	public ReviewStore(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<ReviewRow> list(ReviewStatus status, ReviewReason reason, int offset, int limit) {
		return jdbc.query(SELECT + where(reason) + " order by r.created_at, r.id limit :limit offset :offset",
				filter(status, reason).addValue("limit", limit).addValue("offset", offset), MAPPER);
	}

	public long count(ReviewStatus status, ReviewReason reason) {
		return jdbc.queryForObject("select count(*) from review_item r" + where(reason), filter(status, reason), Long.class);
	}

	public Optional<ReviewRow> findById(long id) {
		return jdbc.query(SELECT + " where r.id = :id", Map.of("id", id), MAPPER).stream().findFirst();
	}

	/** 승인·반려 중 동시 처리를 막기 위해 잠근다 (트랜잭션 안에서 호출) */
	public Optional<ReviewRow> lock(long id) {
		return jdbc.query(SELECT + " where r.id = :id for update of r", Map.of("id", id), MAPPER).stream().findFirst();
	}

	public List<ReviewRow> findPending(long benefitId) {
		return jdbc.query(SELECT + " where r.benefit_id = :id and r.status = 'PENDING' order by r.id",
				Map.of("id", benefitId), MAPPER);
	}

	/**
	 * AI 판정 실패 항목. 같은 혜택·사유로 대기 중인 항목이 있으면 새 원문·추출 결과로 바꾼다.
	 */
	public void upsertExtractionReview(long benefitId, long rawId, ReviewReason reason, Long claudeId, Long gptId,
			String detailJson, OffsetDateTime now) {
		jdbc.update("""
				insert into review_item (benefit_id, raw_id, reason, claude_extraction_id, gpt_extraction_id, detail, created_at)
				values (:benefitId, :rawId, :reason, cast(:claudeId as bigint), cast(:gptId as bigint), cast(:detail as jsonb), :now)
				on conflict (benefit_id, reason) where status = 'PENDING' do update
				   set raw_id = excluded.raw_id, claude_extraction_id = excluded.claude_extraction_id,
				       gpt_extraction_id = excluded.gpt_extraction_id, detail = excluded.detail
				""", new MapSqlParameterSource()
				.addValue("benefitId", benefitId)
				.addValue("rawId", rawId)
				.addValue("reason", reason.name())
				.addValue("claudeId", claudeId)
				.addValue("gptId", gptId)
				.addValue("detail", detailJson)
				.addValue("now", now));
	}

	/** 같은 혜택·사유로 대기 중인 항목이 있으면 아무것도 하지 않는다. 새로 만들었으면 true */
	public boolean insertIfAbsent(long benefitId, Long rawId, ReviewReason reason, Long groupCandidateId,
			String detailJson, OffsetDateTime now) {
		return jdbc.update("""
				insert into review_item (benefit_id, raw_id, reason, group_candidate_id, detail, created_at)
				values (:benefitId, cast(:rawId as bigint), :reason, cast(:candidateId as bigint), cast(:detail as jsonb), :now)
				on conflict (benefit_id, reason) where status = 'PENDING' do nothing
				""", new MapSqlParameterSource()
				.addValue("benefitId", benefitId)
				.addValue("rawId", rawId)
				.addValue("reason", reason.name())
				.addValue("candidateId", groupCandidateId)
				.addValue("detail", detailJson)
				.addValue("now", now)) > 0;
	}

	public void resolve(long id, ReviewStatus status, String note, String resolvedBy, OffsetDateTime now) {
		jdbc.update("""
				update review_item set status = :status, resolution_note = :note, resolved_by = :by, resolved_at = :now
				 where id = :id
				""", new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("status", status.name())
				.addValue("note", note)
				.addValue("by", resolvedBy)
				.addValue("now", now));
	}

	/**
	 * 대기 중인 AI 판정 항목 중 keep 사유가 아닌 것을 RESOLVED로 닫는다(새 원문으로 다시 판정했으므로).
	 * keep이 null이면 모두 닫는다.
	 */
	public int closeStaleExtractionReviews(long benefitId, ReviewReason keep, String note, OffsetDateTime now) {
		return jdbc.update("""
				update review_item set status = 'RESOLVED', resolution_note = :note, resolved_by = 'pipeline', resolved_at = :now
				 where benefit_id = :benefitId and status = 'PENDING'
				   and reason in ('MODEL_MISMATCH', 'SCHEMA_INVALID', 'STRUCTURED_MISMATCH')
				   and reason <> coalesce(:keep, '')
				""", new MapSqlParameterSource()
				.addValue("benefitId", benefitId)
				.addValue("keep", keep == null ? null : keep.name())
				.addValue("note", note)
				.addValue("now", now));
	}

	public int closePending(long benefitId, ReviewReason reason, String note, OffsetDateTime now) {
		return jdbc.update("""
				update review_item set status = 'RESOLVED', resolution_note = :note, resolved_by = 'pipeline', resolved_at = :now
				 where benefit_id = :benefitId and status = 'PENDING' and reason = :reason
				""", Map.of("benefitId", benefitId, "reason", reason.name(), "note", note, "now", now));
	}

	private static String where(ReviewReason reason) {
		return " where r.status = :status" + (reason == null ? "" : " and r.reason = :reason");
	}

	private static MapSqlParameterSource filter(ReviewStatus status, ReviewReason reason) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("status", status.name());
		if (reason != null) {
			params.addValue("reason", reason.name());
		}
		return params;
	}

	private static Long longOrNull(ResultSet rs, String column) throws SQLException {
		long v = rs.getLong(column);
		return rs.wasNull() ? null : v;
	}

}
