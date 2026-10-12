package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.domain.EligibilityResult;
import com.silverhelper.pipeline.domain.ReviewReason;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Claude 결과와 GPT 결과를 맞춰 보고 공개할지 검토 대기열로 보낼지 정한다 (CLAUDE.md AI 파이프라인 원칙).
 * <ol>
 *   <li>어느 한쪽이라도 스키마 검증 실패 → SCHEMA_INVALID</li>
 *   <li>비교 필드(conditionsSummary 제외)가 하나라도 다름 → MODEL_MISMATCH</li>
 *   <li>합의한 값이 구조화 필드와 충돌 → STRUCTURED_MISMATCH</li>
 *   <li>그 밖 → 공개 (Claude 결과를 확정 조건으로)</li>
 * </ol>
 */
public final class CrossValidator {

	public record Difference(String field, Object claude, Object gpt, Object structured) {
	}

	/** reason이 null이면 공개 */
	public record Decision(ReviewReason reason, List<Difference> differences, Map<String, String> fieldSources) {

		public boolean publish() {
			return reason == null;
		}

	}

	private CrossValidator() {
	}

	/**
	 * @param claude 스키마 검증을 통과한 Claude 결과 (실패했으면 null)
	 * @param gpt    스키마 검증을 통과한 GPT 결과 (실패했으면 null)
	 */
	public static Decision decide(EligibilityResult claude, EligibilityResult gpt, StructuredFacts facts) {
		if (claude == null || gpt == null) {
			return new Decision(ReviewReason.SCHEMA_INVALID, List.of(), Map.of());
		}
		List<Difference> modelDiffs = new ArrayList<>();
		for (String field : new TreeSet<>(EligibilityResult.COMPARED_FIELDS.keySet())) {
			Object c = claude.value(field);
			Object g = gpt.value(field);
			if (!Objects.equals(c, g)) {
				modelDiffs.add(new Difference(field, c, g, structuredValue(field, facts)));
			}
		}
		if (!modelDiffs.isEmpty()) {
			return new Decision(ReviewReason.MODEL_MISMATCH, modelDiffs, Map.of());
		}
		List<Difference> conflicts = structuredConflicts(claude, facts);
		if (!conflicts.isEmpty()) {
			return new Decision(ReviewReason.STRUCTURED_MISMATCH, conflicts, Map.of());
		}
		return new Decision(null, List.of(), fieldSources(claude, facts));
	}

	static List<Difference> structuredConflicts(EligibilityResult r, StructuredFacts f) {
		List<Difference> conflicts = new ArrayList<>();
		if (f.minAge() != null && r.minAge() != null && !f.minAge().equals(r.minAge())) {
			conflicts.add(new Difference("minAge", r.minAge(), r.minAge(), f.minAge()));
		}
		if (f.maxAge() != null && f.maxAge() < StructuredFacts.NO_MAX_AGE && r.maxAge() != null && !f.maxAge().equals(r.maxAge())) {
			conflicts.add(new Difference("maxAge", r.maxAge(), r.maxAge(), f.maxAge()));
		}
		// 생애주기가 있는데 노년·중장년이 없으면서 AI는 60세 이상 사업이라고 한 경우
		if (f.hasLifeTags() && !f.includesSeniorLife() && r.minAge() != null && r.minAge() >= 60) {
			conflicts.add(new Difference("minAge", r.minAge(), r.minAge(), "생애주기 " + f.lifeCodes()));
		}
		if (f.hasConditions()) {
			if (Boolean.TRUE.equals(r.requiresDisability()) && !f.disabilityTargeted()) {
				conflicts.add(new Difference("requiresDisability", true, true, "JA0328 없음"));
			}
			if (Boolean.TRUE.equals(r.requiresVeteran()) && !f.veteranTargeted()) {
				conflicts.add(new Difference("requiresVeteran", true, true, "JA0329 없음"));
			}
			if (Boolean.TRUE.equals(r.requiresSingleHousehold()) && !f.singleHouseholdTargeted()) {
				conflicts.add(new Difference("requiresSingleHousehold", true, true, "JA0404 없음"));
			}
		}
		return conflicts;
	}

	/** 값이 있는 필드만. 구조화 값과 같으면 STRUCTURED, 아니면 AI (ADR 0002 §6) */
	private static Map<String, String> fieldSources(EligibilityResult r, StructuredFacts f) {
		Map<String, String> sources = new LinkedHashMap<>();
		for (String field : new TreeSet<>(EligibilityResult.COMPARED_FIELDS.keySet())) {
			Object value = r.value(field);
			if (value == null) {
				continue;
			}
			sources.put(field, Objects.equals(value, structuredValue(field, f)) ? "STRUCTURED" : "AI");
		}
		if (r.conditionsSummary() != null) {
			sources.put("conditionsSummary", "AI");
		}
		return sources;
	}

	private static Object structuredValue(String field, StructuredFacts f) {
		return switch (field) {
			case "minAge" -> f.minAge();
			case "maxAge" -> f.maxAge() != null && f.maxAge() < StructuredFacts.NO_MAX_AGE ? f.maxAge() : null;
			default -> null;
		};
	}

}
