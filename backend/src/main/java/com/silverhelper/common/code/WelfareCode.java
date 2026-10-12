package com.silverhelper.common.code;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 복지로 코드표 (docs/specs/public-api.md §5) + 장기요양 등급.
 * 공공 API 응답은 코드가 아니라 한글 이름을 쉼표로 이어서 주므로 {@link #fromName}으로 코드를 찾는다.
 */
public enum WelfareCode {

	LIFE_INFANT(Type.LIFE, "001", "영유아"),
	LIFE_CHILD(Type.LIFE, "002", "아동"),
	LIFE_TEEN(Type.LIFE, "003", "청소년"),
	LIFE_YOUTH(Type.LIFE, "004", "청년"),
	LIFE_MIDDLE(Type.LIFE, "005", "중장년"),
	LIFE_SENIOR(Type.LIFE, "006", "노년"),
	LIFE_PREGNANCY(Type.LIFE, "007", "임신·출산"),

	HOUSEHOLD_MULTICULTURAL(Type.HOUSEHOLD, "010", "다문화·탈북민"),
	HOUSEHOLD_MULTI_CHILD(Type.HOUSEHOLD, "020", "다자녀"),
	HOUSEHOLD_VETERAN(Type.HOUSEHOLD, "030", "보훈대상자"),
	HOUSEHOLD_DISABLED(Type.HOUSEHOLD, "040", "장애인"),
	HOUSEHOLD_LOW_INCOME(Type.HOUSEHOLD, "050", "저소득"),
	HOUSEHOLD_SINGLE_PARENT(Type.HOUSEHOLD, "060", "한부모·조손"),

	THEME_PHYSICAL(Type.THEME, "010", "신체건강"),
	THEME_MENTAL(Type.THEME, "020", "정신건강"),
	THEME_LIVING(Type.THEME, "030", "생활지원"),
	THEME_HOUSING(Type.THEME, "040", "주거"),
	THEME_JOB(Type.THEME, "050", "일자리"),
	THEME_CULTURE(Type.THEME, "060", "문화·여가"),
	THEME_SAFETY(Type.THEME, "070", "안전·위기"),
	THEME_PREGNANCY(Type.THEME, "080", "임신·출산"),
	THEME_CHILDCARE(Type.THEME, "090", "보육"),
	THEME_EDUCATION(Type.THEME, "100", "교육"),
	THEME_ADOPTION(Type.THEME, "110", "입양·위탁"),
	THEME_CARE(Type.THEME, "120", "보호·돌봄"),
	THEME_FINANCE(Type.THEME, "130", "서민금융"),
	THEME_LAW(Type.THEME, "140", "법률"),
	THEME_RELATIONSHIP(Type.THEME, "150", "관계개선"),
	THEME_ENERGY(Type.THEME, "160", "에너지"),

	LTC_1(Type.LTC_GRADE, "1", "1등급"),
	LTC_2(Type.LTC_GRADE, "2", "2등급"),
	LTC_3(Type.LTC_GRADE, "3", "3등급"),
	LTC_4(Type.LTC_GRADE, "4", "4등급"),
	LTC_5(Type.LTC_GRADE, "5", "5등급"),
	LTC_COGNITIVE(Type.LTC_GRADE, "6", "인지지원등급");

	public enum Type {
		LIFE, HOUSEHOLD, THEME, LTC_GRADE
	}

	private final Type type;
	private final String code;
	private final String name;

	WelfareCode(Type type, String code, String name) {
		this.type = type;
		this.code = code;
		this.name = name;
	}

	public Type type() {
		return type;
	}

	public String code() {
		return code;
	}

	public String displayName() {
		return name;
	}

	public static List<WelfareCode> of(Type type) {
		return Arrays.stream(values()).filter(c -> c.type == type).toList();
	}

	public static Optional<WelfareCode> fromCode(Type type, String code) {
		return Arrays.stream(values()).filter(c -> c.type == type && c.code.equals(code)).findFirst();
	}

	/** 응답의 이름("임신 · 출산"처럼 공백이 섞인 것 포함)으로 코드를 찾는다. */
	public static Optional<WelfareCode> fromName(Type type, String name) {
		if (name == null) {
			return Optional.empty();
		}
		String normalized = normalize(name);
		return Arrays.stream(values())
				.filter(c -> c.type == type && normalize(c.name).equals(normalized))
				.findFirst();
	}

	/** "청년, 중장년,노년" 같은 쉼표 목록을 코드 목록으로 바꾼다. 모르는 이름은 버린다. */
	public static List<WelfareCode> fromNames(Type type, String commaSeparated) {
		if (commaSeparated == null || commaSeparated.isBlank()) {
			return List.of();
		}
		return Arrays.stream(commaSeparated.split(","))
				.map(n -> fromName(type, n))
				.flatMap(Optional::stream)
				.distinct()
				.toList();
	}

	private static String normalize(String s) {
		return s.replaceAll("\\s+", "").replace('ㆍ', '·').replace('・', '·');
	}

}
