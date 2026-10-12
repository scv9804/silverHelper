package com.silverhelper.pipeline.repository;

import com.silverhelper.pipeline.client.ApiQuota;
import com.silverhelper.pipeline.client.QuotaExceededException;
import com.silverhelper.pipeline.config.PipelineProperties;
import com.silverhelper.pipeline.domain.Source;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * api_daily_usage로 원천·날짜(Asia/Seoul)별 호출 수를 센다.
 * 호출 전에 1을 원자적으로 더하고, 상한이면 더하지 않고 거절한다(여러 인스턴스가 동시에 돌아도 상한을 넘지 않음).
 */
@Repository
public class JdbcApiQuota implements ApiQuota {

	private final JdbcTemplate jdbc;
	private final PipelineProperties properties;
	private final Clock clock;

	public JdbcApiQuota(JdbcTemplate jdbc, PipelineProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void acquire(Source source) {
		int limit = properties.quota().limit(source);
		if (limit <= 0) {
			throw new QuotaExceededException(source, "하루 상한이 0입니다");
		}
		List<Integer> counted = jdbc.queryForList("""
				insert into api_daily_usage (source, usage_date, call_count) values (?, ?, 1)
				on conflict (source, usage_date) do update set call_count = api_daily_usage.call_count + 1
				where api_daily_usage.call_count < ?
				returning call_count
				""", Integer.class, source.name(), today(), limit);
		if (counted.isEmpty()) {
			throw new QuotaExceededException(source, "오늘 호출 상한(" + limit + "건)에 도달했습니다");
		}
	}

	@Override
	public void exhaust(Source source) {
		int limit = properties.quota().limit(source);
		jdbc.update("""
				insert into api_daily_usage (source, usage_date, call_count) values (?, ?, ?)
				on conflict (source, usage_date) do update set call_count = greatest(api_daily_usage.call_count, excluded.call_count)
				""", source.name(), today(), limit);
	}

	@Override
	public int remaining(Source source) {
		return Math.max(0, properties.quota().limit(source) - used(source));
	}

	public int used(Source source) {
		List<Integer> used = jdbc.queryForList(
				"select call_count from api_daily_usage where source = ? and usage_date = ?",
				Integer.class, source.name(), today());
		return used.isEmpty() ? 0 : used.getFirst();
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

}
