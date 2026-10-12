package com.silverhelper.pipeline.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * AI가 추출한 자격 조건 (JSON 스키마는 {@link com.silverhelper.pipeline.ai.EligibilitySchema}).
 * null = 조건 없음 또는 원문에 언급 없음 (ADR 0002 §6)
 */
public record EligibilityResult(
		Integer minAge,
		Integer maxAge,
		Boolean ltcRequired,
		List<Integer> ltcGrades,
		Integer incomeMaxMedianPct,
		Boolean requiresBasicLivelihood,
		Boolean requiresNearPoverty,
		Boolean requiresBasicPension,
		Boolean requiresDisability,
		Boolean requiresVeteran,
		Boolean requiresSingleHousehold,
		String conditionsSummary) {

	/** 교차검증에서 비교하는 필드. conditionsSummary는 자유 문장이라 비교하지 않는다 */
	public static final Map<String, Function<EligibilityResult, Object>> COMPARED_FIELDS = Map.ofEntries(
			Map.entry("minAge", EligibilityResult::minAge),
			Map.entry("maxAge", EligibilityResult::maxAge),
			Map.entry("ltcRequired", EligibilityResult::ltcRequired),
			Map.entry("ltcGrades", e -> e.ltcGrades() == null ? null : e.ltcGrades().stream().sorted().distinct().toList()),
			Map.entry("incomeMaxMedianPct", EligibilityResult::incomeMaxMedianPct),
			Map.entry("requiresBasicLivelihood", EligibilityResult::requiresBasicLivelihood),
			Map.entry("requiresNearPoverty", EligibilityResult::requiresNearPoverty),
			Map.entry("requiresBasicPension", EligibilityResult::requiresBasicPension),
			Map.entry("requiresDisability", EligibilityResult::requiresDisability),
			Map.entry("requiresVeteran", EligibilityResult::requiresVeteran),
			Map.entry("requiresSingleHousehold", EligibilityResult::requiresSingleHousehold));

	public static final EligibilityResult EMPTY =
			new EligibilityResult(null, null, null, null, null, null, null, null, null, null, null, null);

	public Object value(String field) {
		return COMPARED_FIELDS.get(field).apply(this);
	}

	public boolean sameConditions(EligibilityResult other) {
		return COMPARED_FIELDS.keySet().stream().allMatch(f -> Objects.equals(value(f), other.value(f)));
	}

}
