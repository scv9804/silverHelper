package com.silverhelper.pipeline.repository;

import com.silverhelper.pipeline.domain.EligibilityResult;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** benefit_eligibility: 확정된 자격 조건 (혜택당 1행). 매칭 API가 읽는다 */
@Repository
public class EligibilityStore {

	public record Confirmed(long benefitId, long extractionId, EligibilityResult eligibility, String fieldSourcesJson) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	public EligibilityStore(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void upsert(long benefitId, long extractionId, EligibilityResult e, String fieldSourcesJson, OffsetDateTime now) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("benefitId", benefitId)
				.addValue("extractionId", extractionId)
				.addValue("minAge", e.minAge())
				.addValue("maxAge", e.maxAge())
				.addValue("ltcRequired", e.ltcRequired())
				.addValue("ltcGrades", toArrayLiteral(e.ltcGrades()))
				.addValue("income", e.incomeMaxMedianPct())
				.addValue("livelihood", e.requiresBasicLivelihood())
				.addValue("nearPoverty", e.requiresNearPoverty())
				.addValue("pension", e.requiresBasicPension())
				.addValue("disability", e.requiresDisability())
				.addValue("veteran", e.requiresVeteran())
				.addValue("single", e.requiresSingleHousehold())
				.addValue("summary", e.conditionsSummary())
				.addValue("fieldSources", fieldSourcesJson)
				.addValue("now", now);
		jdbc.update("""
				insert into benefit_eligibility (benefit_id, extraction_id, min_age, max_age, ltc_required, ltc_grades,
				        income_max_median_pct, requires_basic_livelihood, requires_near_poverty, requires_basic_pension,
				        requires_disability, requires_veteran, requires_single_household, conditions_summary,
				        field_sources, verified_at)
				values (:benefitId, :extractionId, cast(:minAge as smallint), cast(:maxAge as smallint),
				        cast(:ltcRequired as boolean), cast(:ltcGrades as smallint[]), cast(:income as smallint),
				        cast(:livelihood as boolean), cast(:nearPoverty as boolean), cast(:pension as boolean),
				        cast(:disability as boolean), cast(:veteran as boolean), cast(:single as boolean), :summary,
				        cast(:fieldSources as jsonb), :now)
				on conflict (benefit_id) do update set
				        extraction_id = excluded.extraction_id, min_age = excluded.min_age, max_age = excluded.max_age,
				        ltc_required = excluded.ltc_required, ltc_grades = excluded.ltc_grades,
				        income_max_median_pct = excluded.income_max_median_pct,
				        requires_basic_livelihood = excluded.requires_basic_livelihood,
				        requires_near_poverty = excluded.requires_near_poverty,
				        requires_basic_pension = excluded.requires_basic_pension,
				        requires_disability = excluded.requires_disability, requires_veteran = excluded.requires_veteran,
				        requires_single_household = excluded.requires_single_household,
				        conditions_summary = excluded.conditions_summary, field_sources = excluded.field_sources,
				        verified_at = excluded.verified_at
				""", params);
	}

	public Optional<Confirmed> find(long benefitId) {
		return jdbc.query("select *, field_sources::text as fs from benefit_eligibility where benefit_id = :id",
				Map.of("id", benefitId), (rs, i) -> new Confirmed(rs.getLong("benefit_id"), rs.getLong("extraction_id"),
						new EligibilityResult(intOrNull(rs, "min_age"), intOrNull(rs, "max_age"),
								boolOrNull(rs, "ltc_required"), grades(rs.getArray("ltc_grades")),
								intOrNull(rs, "income_max_median_pct"), boolOrNull(rs, "requires_basic_livelihood"),
								boolOrNull(rs, "requires_near_poverty"), boolOrNull(rs, "requires_basic_pension"),
								boolOrNull(rs, "requires_disability"), boolOrNull(rs, "requires_veteran"),
								boolOrNull(rs, "requires_single_household"), rs.getString("conditions_summary")),
						rs.getString("fs"))).stream().findFirst();
	}

	private static String toArrayLiteral(List<Integer> values) {
		if (values == null) {
			return null;
		}
		return values.stream().sorted().distinct().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
	}

	private static List<Integer> grades(Array array) throws SQLException {
		if (array == null) {
			return null;
		}
		return Arrays.stream((Object[]) array.getArray()).map(o -> ((Number) o).intValue()).toList();
	}

	private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
		int v = rs.getInt(column);
		return rs.wasNull() ? null : v;
	}

	private static Boolean boolOrNull(ResultSet rs, String column) throws SQLException {
		boolean v = rs.getBoolean(column);
		return rs.wasNull() ? null : v;
	}

}
