package com.silverhelper.pipeline.repository;

import com.silverhelper.pipeline.domain.CollectRunStatus;
import com.silverhelper.pipeline.domain.Source;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** collect_run: 원천별 수집 실행 이력 */
@Repository
public class CollectRunStore {

	public record Counts(int apiCalls, int itemsSeen, int itemsNew, int itemsChanged, int itemsRemoved) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	public CollectRunStore(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long start(Source source, OffsetDateTime now) {
		return jdbc.queryForObject("""
				insert into collect_run (source, status, started_at) values (:source, 'RUNNING', :now) returning id
				""", Map.of("source", source.name(), "now", now), Long.class);
	}

	public void finish(long id, CollectRunStatus status, Counts counts, String errorMessage, OffsetDateTime now) {
		jdbc.update("""
				update collect_run
				   set status = :status, finished_at = :now, api_calls = :apiCalls, items_seen = :seen,
				       items_new = :new, items_changed = :changed, items_removed = :removed, error_message = :error
				 where id = :id
				""", new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("status", status.name())
				.addValue("now", now)
				.addValue("apiCalls", counts.apiCalls())
				.addValue("seen", counts.itemsSeen())
				.addValue("new", counts.itemsNew())
				.addValue("changed", counts.itemsChanged())
				.addValue("removed", counts.itemsRemoved())
				.addValue("error", errorMessage));
	}

}
