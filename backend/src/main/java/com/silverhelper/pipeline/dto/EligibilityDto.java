package com.silverhelper.pipeline.dto;

import com.silverhelper.pipeline.domain.EligibilityResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/** openapi.yaml Eligibility. null = 조건 없음(원문에 언급 없음) */
public record EligibilityDto(
		@Min(0) @Max(120) Integer minAge,
		@Min(0) @Max(120) Integer maxAge,
		Boolean ltcRequired,
		List<@Min(1) @Max(6) Integer> ltcGrades,
		@Min(0) @Max(1000) Integer incomeMaxMedianPct,
		Boolean requiresBasicLivelihood,
		Boolean requiresNearPoverty,
		Boolean requiresBasicPension,
		Boolean requiresDisability,
		Boolean requiresVeteran,
		Boolean requiresSingleHousehold,
		@Size(max = 300) String conditionsSummary) {

	public static EligibilityDto from(EligibilityResult e) {
		return new EligibilityDto(e.minAge(), e.maxAge(), e.ltcRequired(), e.ltcGrades(), e.incomeMaxMedianPct(),
				e.requiresBasicLivelihood(), e.requiresNearPoverty(), e.requiresBasicPension(), e.requiresDisability(),
				e.requiresVeteran(), e.requiresSingleHousehold(), e.conditionsSummary());
	}

	public EligibilityResult toResult() {
		return new EligibilityResult(minAge, maxAge, ltcRequired,
				ltcGrades == null ? null : ltcGrades.stream().sorted().distinct().toList(),
				incomeMaxMedianPct, requiresBasicLivelihood, requiresNearPoverty, requiresBasicPension,
				requiresDisability, requiresVeteran, requiresSingleHousehold, conditionsSummary);
	}

}
