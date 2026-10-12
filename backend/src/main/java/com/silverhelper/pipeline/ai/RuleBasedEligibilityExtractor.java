package com.silverhelper.pipeline.ai;

import com.silverhelper.pipeline.domain.AiProvider;
import com.silverhelper.pipeline.domain.EligibilityResult;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 키가 없을 때 쓰는 가짜 추출기. 정규식 규칙으로 자격 조건을 뽑는다.
 * Claude용·GPT용을 따로 등록해서 교차검증 흐름이 그대로 돌게 한다(규칙이 같으니 두 결과는 항상 같다).
 * 실제 서비스 품질의 추출이 아니다. model은 {@code rule-based-fake}로 남아 실제 AI 결과와 구분된다.
 */
public class RuleBasedEligibilityExtractor implements EligibilityExtractor {

	public static final String MODEL = "rule-based-fake";
	public static final String PROMPT_VERSION = "rule-v1";

	private static final Pattern MIN_AGE = Pattern.compile("(?:만\\s*)?(\\d{2,3})\\s*세\\s*(?:이상|부터)");
	private static final Pattern AGE_RANGE = Pattern.compile("(?:만\\s*)?(\\d{1,3})\\s*세?\\s*[~∼\\-]\\s*(\\d{1,3})\\s*세");
	private static final Pattern LTC_RANGE = Pattern.compile("장기요양\\s*(?:등급\\s*)?([1-5])\\s*[~∼\\-]\\s*([1-5])\\s*등급");
	private static final Pattern LTC_SINGLE = Pattern.compile("장기요양\\s*([1-5])\\s*등급");
	private static final Pattern LTC_HOLDER = Pattern.compile(
			"장기요양\\s*(?:급여\\s*)?(?:이용자|수급자|수급권자|인정자|등급\\s*(?:을\\s*)?(?:받은|판정|인정))");
	private static final Pattern INCOME = Pattern.compile("중위소득\\s*(\\d{2,3})\\s*%\\s*이하");
	private static final Pattern BASIC_PENSION = Pattern.compile("기초연금\\s*(?:을\\s*)?수급");
	private static final Pattern BASIC_LIVELIHOOD = Pattern.compile("기초생활\\s*수급|생계\\s*·?\\s*의료\\s*급여\\s*수급");
	private static final Pattern NEAR_POVERTY = Pattern.compile("차상위");
	private static final Pattern SINGLE = Pattern.compile("홀몸|독거|1인\\s*가구");
	private static final Pattern DISABILITY_NAME = Pattern.compile("장애인");
	private static final Pattern VETERAN_NAME = Pattern.compile("보훈|국가유공자");

	private final AiProvider provider;
	private final JsonMapper jsonMapper;

	public RuleBasedEligibilityExtractor(AiProvider provider, JsonMapper jsonMapper) {
		this.provider = provider;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public AiProvider provider() {
		return provider;
	}

	@Override
	public String promptVersion() {
		return PROMPT_VERSION;
	}

	@Override
	public AiOutput extract(SourceText text) {
		return new AiOutput(MODEL, jsonMapper.writeValueAsString(extractResult(text)), null, null);
	}

	public static EligibilityResult extractResult(SourceText text) {
		String all = text.joined();
		String name = text.get("서비스명") == null ? "" : text.get("서비스명");

		Integer minAge = null;
		Integer maxAge = null;
		Matcher range = AGE_RANGE.matcher(all);
		if (range.find()) {
			minAge = Integer.parseInt(range.group(1));
			maxAge = Integer.parseInt(range.group(2));
			if (minAge > maxAge || maxAge > 120) {
				minAge = null;
				maxAge = null;
			}
		}
		if (minAge == null) {
			Matcher min = MIN_AGE.matcher(all);
			if (min.find()) {
				int age = Integer.parseInt(min.group(1));
				minAge = age <= 120 ? age : null;
			}
		}

		Boolean ltcRequired = null;
		TreeSet<Integer> grades = new TreeSet<>();
		Matcher ltcRange = LTC_RANGE.matcher(all);
		while (ltcRange.find()) {
			for (int g = Integer.parseInt(ltcRange.group(1)); g <= Integer.parseInt(ltcRange.group(2)); g++) {
				grades.add(g);
			}
		}
		if (grades.isEmpty()) {
			Matcher single = LTC_SINGLE.matcher(all);
			while (single.find()) {
				grades.add(Integer.parseInt(single.group(1)));
			}
		}
		if (!grades.isEmpty() && all.contains("인지지원등급")) {
			grades.add(6);
		}
		if (!grades.isEmpty() || LTC_HOLDER.matcher(all).find()) {
			ltcRequired = true;
		}

		Integer income = null;
		Matcher incomeMatcher = INCOME.matcher(all);
		if (incomeMatcher.find()) {
			income = Integer.parseInt(incomeMatcher.group(1));
		}

		// 기초생활수급과 차상위가 함께 나오면 "둘 중 하나"인 경우가 대부분이라 어느 쪽도 필수로 보지 않는다
		boolean livelihood = BASIC_LIVELIHOOD.matcher(all).find();
		boolean nearPoverty = NEAR_POVERTY.matcher(all).find();
		Boolean requiresLivelihood = livelihood && !nearPoverty ? Boolean.TRUE : null;
		Boolean requiresNearPoverty = nearPoverty && !livelihood ? Boolean.TRUE : null;

		Boolean basicPension = BASIC_PENSION.matcher(all).find() ? Boolean.TRUE : null;
		Boolean single = SINGLE.matcher(all).find() ? Boolean.TRUE : null;
		Boolean disability = DISABILITY_NAME.matcher(name).find() ? Boolean.TRUE : null;
		Boolean veteran = VETERAN_NAME.matcher(name).find() ? Boolean.TRUE : null;

		List<Integer> gradeList = grades.isEmpty() ? null : List.copyOf(grades);
		EligibilityResult partial = new EligibilityResult(minAge, maxAge, ltcRequired, gradeList, income,
				requiresLivelihood, requiresNearPoverty, basicPension, disability, veteran, single, null);
		return new EligibilityResult(minAge, maxAge, ltcRequired, gradeList, income,
				requiresLivelihood, requiresNearPoverty, basicPension, disability, veteran, single, summarize(partial));
	}

	private static String summarize(EligibilityResult r) {
		List<String> parts = new ArrayList<>();
		if (r.minAge() != null && r.maxAge() != null) {
			parts.add(r.minAge() + "~" + r.maxAge() + "세");
		}
		else if (r.minAge() != null) {
			parts.add(r.minAge() + "세 이상");
		}
		if (Boolean.TRUE.equals(r.ltcRequired())) {
			parts.add(r.ltcGrades() == null ? "장기요양 등급 보유" : "장기요양 " + r.ltcGrades() + "등급");
		}
		if (r.incomeMaxMedianPct() != null) {
			parts.add("기준 중위소득 " + r.incomeMaxMedianPct() + "% 이하");
		}
		if (Boolean.TRUE.equals(r.requiresBasicLivelihood())) {
			parts.add("기초생활수급자");
		}
		if (Boolean.TRUE.equals(r.requiresNearPoverty())) {
			parts.add("차상위계층");
		}
		if (Boolean.TRUE.equals(r.requiresBasicPension())) {
			parts.add("기초연금 수급자");
		}
		if (Boolean.TRUE.equals(r.requiresDisability())) {
			parts.add("등록 장애인");
		}
		if (Boolean.TRUE.equals(r.requiresVeteran())) {
			parts.add("국가보훈대상자");
		}
		if (Boolean.TRUE.equals(r.requiresSingleHousehold())) {
			parts.add("1인 가구");
		}
		return parts.isEmpty() ? null : String.join(" · ", parts);
	}

}
