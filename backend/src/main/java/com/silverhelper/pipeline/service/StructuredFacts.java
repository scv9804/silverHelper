package com.silverhelper.pipeline.service;

import java.util.List;
import java.util.Map;

/**
 * 원천 API의 구조화 필드 중 AI 결과와 대조할 값.
 * <ul>
 *   <li>복지로: 생애주기 코드 (006 노년, 005 중장년)</li>
 *   <li>정부24: supportConditions의 JA0110/JA0111(대상연령), JA0328(장애인), JA0329(보훈), JA0404(1인가구)</li>
 * </ul>
 * 구조화 연령은 그대로 믿지 않는다(public-api.md §7). 대조용으로만 쓰고, 다르면 사람이 본다.
 */
public record StructuredFacts(
		List<String> lifeCodes,
		Integer minAge,
		Integer maxAge,
		boolean hasConditions,
		boolean disabilityTargeted,
		boolean veteranTargeted,
		boolean singleHouseholdTargeted) {

	/** 정부24 대상연령 상한 120은 "제한 없음"으로 쓰인다 (public-api.md §10) */
	public static final int NO_MAX_AGE = 120;

	public static final StructuredFacts NONE = new StructuredFacts(List.of(), null, null, false, false, false, false);

	public static StructuredFacts ofLifeCodes(List<String> lifeCodes) {
		return new StructuredFacts(List.copyOf(lifeCodes), null, null, false, false, false, false);
	}

	public static StructuredFacts ofGov24Conditions(Map<String, Object> c) {
		if (c == null || c.size() <= 1) {
			return NONE;
		}
		return new StructuredFacts(List.of(), number(c.get("JA0110")), number(c.get("JA0111")), true,
				"Y".equals(c.get("JA0328")), "Y".equals(c.get("JA0329")), "Y".equals(c.get("JA0404")));
	}

	public boolean hasLifeTags() {
		return !lifeCodes.isEmpty();
	}

	/** 생애주기에 노년(006)이나 중장년(005)이 있는지 */
	public boolean includesSeniorLife() {
		return lifeCodes.contains("006") || lifeCodes.contains("005");
	}

	private static Integer number(Object value) {
		if (value instanceof Number n) {
			return n.intValue();
		}
		if (value instanceof String s && !s.isBlank()) {
			try {
				return Integer.valueOf(s.trim());
			}
			catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

}
