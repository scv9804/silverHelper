package com.silverhelper.pipeline.service;

import com.silverhelper.common.code.WelfareCode;
import com.silverhelper.common.config.TimeConfig;
import com.silverhelper.pipeline.ai.SourceText;
import com.silverhelper.pipeline.client.SourceRecord;
import com.silverhelper.pipeline.domain.DetailFields;
import com.silverhelper.pipeline.domain.ListFields;
import com.silverhelper.pipeline.domain.RawKind;
import com.silverhelper.pipeline.domain.Reference;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.domain.TagCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 원천 응답 필드 → benefit 컬럼, AI 입력 원문 (public-api.md §2~§4).
 * 원천마다 필드명이 달라서(A lifeArray, B lifeNmArray, C 한글 필드명) 여기 한곳에 모은다.
 */
public final class SourceMapper {

	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final DateTimeFormatter YMD_HMS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final LocalDate NO_END = LocalDate.of(9999, 12, 31);

	private SourceMapper() {
	}

	/** AI 입력 원문이 담긴 raw 종류. 복지로는 상세, 정부24는 목록에 원문이 있다 */
	public static RawKind aiInputKind(Source source) {
		return source == Source.GOV24 ? RawKind.LIST : RawKind.DETAIL;
	}

	public static String sourceId(Source source, SourceRecord item) {
		return source == Source.GOV24 ? item.text("서비스ID") : item.text("servId");
	}

	/** 정부24는 사용자구분에 개인·가구가 있는 것만 수집한다 (소상공인·법인 대상 제외) */
	public static boolean accepts(Source source, SourceRecord item) {
		if (sourceId(source, item) == null || name(source, item) == null) {
			return false;
		}
		if (source != Source.GOV24) {
			return true;
		}
		String users = item.text("사용자구분");
		if (users == null) {
			return false;
		}
		return Arrays.stream(users.split("\\|\\|")).map(String::trim).anyMatch(u -> u.equals("개인") || u.equals("가구"));
	}

	public static ListFields listFields(Source source, SourceRecord item) {
		return switch (source) {
			case NATIONAL -> new ListFields(
					cut(item.text("servNm"), 300),
					item.text("servDgst"),
					cut(sourceUrl(source, item), 1000),
					cut(item.text("jurMnofNm"), 200),
					cut(item.text("jurOrgNm"), 200),
					null, null,
					cut(item.text("sprtCycNm"), 100),
					cut(item.text("srvPvsnNm"), 100),
					null,
					yn(item.text("onapPsbltYn")),
					cut(item.text("rprsCtadr"), 500),
					date(item.text("svcfrstRegTs")),
					null,
					tags(item.text("lifeArray"), item.text("trgterIndvdlArray"), item.text("intrsThemaArray")));
			case LOCAL -> new ListFields(
					cut(item.text("servNm"), 300),
					item.text("servDgst"),
					cut(sourceUrl(source, item), 1000),
					cut(join(" ", item.text("ctpvNm"), item.text("sggNm")), 200),
					cut(item.text("bizChrDeptNm"), 200),
					cut(item.text("ctpvNm"), 50),
					cut(item.text("sggNm"), 50),
					cut(item.text("sprtCycNm"), 100),
					cut(item.text("srvPvsnNm"), 100),
					cut(item.text("aplyMtdNm"), 200),
					null,
					null,
					null,
					startOfDay(date(item.text("lastModYmd"))),
					tags(item.text("lifeNmArray"), item.text("trgterIndvdlNmArray"), item.text("intrsThemaNmArray")));
			case GOV24 -> {
				String[] region = gov24Region(item.text("소관기관유형"), item.text("소관기관명"));
				yield new ListFields(
						cut(item.text("서비스명"), 300),
						item.text("서비스목적요약"),
						cut(sourceUrl(source, item), 1000),
						cut(item.text("소관기관명"), 200),
						cut(item.text("부서명"), 200),
						cut(region[0], 50),
						cut(region[1], 50),
						null,
						cut(item.text("지원유형"), 100),
						cut(item.text("신청방법"), 200),
						null,
						cut(item.text("전화문의"), 500),
						date(left(item.text("등록일시"), 8)),
						dateTime(item.text("수정일시")),
						tags(null, null, item.text("서비스분야")));
			}
		};
	}

	/** 복지로는 상세 응답, 정부24는 목록 항목에서 */
	public static DetailFields detailFields(Source source, SourceRecord record) {
		return switch (source) {
			case NATIONAL -> new DetailFields(
					shortValue(record.text("crtrYr")),
					null, null,
					null,
					record.text("tgtrDtlCn"),
					record.text("slctCritCn"),
					record.text("alwServCn"),
					applySteps(record.list("applmetList")),
					nationalReferences(record));
			case LOCAL -> {
				List<Reference> refs = localReferences(record);
				String contact = refs.stream().filter(r -> r.type().equals("CONTACT")).findFirst()
						.map(r -> join(" ", r.name(), r.value())).orElse(null);
				LocalDate end = date(record.text("enfcEndYmd"));
				yield new DetailFields(
						null,
						date(record.text("enfcBgngYmd")),
						NO_END.equals(end) ? null : end,
						cut(contact, 500),
						record.text("sprtTrgtCn"),
						record.text("slctCritCn"),
						record.text("alwServCn"),
						record.text("aplyMtdCn"),
						refs);
			}
			case GOV24 -> {
				List<Reference> refs = new ArrayList<>();
				if (record.text("전화문의") != null) {
					refs.add(new Reference("CONTACT", null, record.text("전화문의")));
				}
				String apply = join("\n", record.text("신청방법"),
						record.text("신청기한") == null ? null : "신청기한: " + record.text("신청기한"));
				yield new DetailFields(null, null, null, null,
						record.text("지원대상"),
						record.text("선정기준"),
						record.text("지원내용"),
						apply,
						refs);
			}
		};
	}

	/** AI 입력 원문. 키는 사람이 읽는 항목명. 관리자 검토 화면에도 그대로 보인다 */
	public static SourceText sourceText(Source source, Map<String, Object> payload) {
		SourceRecord r = new SourceRecord(payload, "");
		Map<String, String> fields = new LinkedHashMap<>();
		switch (source) {
			case NATIONAL -> {
				put(fields, "서비스명", r.text("servNm"));
				put(fields, "서비스 요약", r.text("wlfareInfoOutlCn"));
				put(fields, "지원대상", r.text("tgtrDtlCn"));
				put(fields, "선정기준", r.text("slctCritCn"));
				put(fields, "지원내용", r.text("alwServCn"));
			}
			case LOCAL -> {
				put(fields, "서비스명", r.text("servNm"));
				put(fields, "서비스 요약", r.text("servDgst"));
				put(fields, "지원대상", r.text("sprtTrgtCn"));
				put(fields, "선정기준", r.text("slctCritCn"));
				put(fields, "지원내용", r.text("alwServCn"));
			}
			case GOV24 -> {
				put(fields, "서비스명", r.text("서비스명"));
				put(fields, "서비스 요약", r.text("서비스목적요약"));
				put(fields, "지원대상", r.text("지원대상"));
				put(fields, "선정기준", r.text("선정기준"));
				put(fields, "지원내용", r.text("지원내용"));
			}
		}
		return new SourceText(fields);
	}

	private static void put(Map<String, String> fields, String key, String value) {
		if (value != null && !value.isBlank()) {
			fields.put(key, value.replace("\r\n", "\n").replace('\r', '\n').trim());
		}
	}

	private static String name(Source source, SourceRecord item) {
		return source == Source.GOV24 ? item.text("서비스명") : item.text("servNm");
	}

	private static String sourceUrl(Source source, SourceRecord item) {
		String id = sourceId(source, item);
		return switch (source) {
			case NATIONAL -> orDefault(item.text("servDtlLink"),
					"https://www.bokjiro.go.kr/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do?wlfareInfoId=" + id + "&wlfareInfoReldBztpCd=01");
			case LOCAL -> orDefault(item.text("servDtlLink"),
					"https://www.bokjiro.go.kr/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do?wlfareInfoId=" + id + "&wlfareInfoReldBztpCd=02");
			case GOV24 -> orDefault(item.text("상세조회URL"), "https://www.gov.kr/portal/rcvfvrSvc/dtlEx/" + id);
		};
	}

	/**
	 * 정부24는 지역 필드가 없다. 소관기관유형이 시군구면 "경상북도 울진군" → (경상북도, 울진군),
	 * 광역시도면 "서울특별시" → (서울특별시, null). 그 밖(중앙행정기관 등)은 전국.
	 */
	static String[] gov24Region(String agencyType, String agencyName) {
		if (agencyName == null || agencyType == null) {
			return new String[] {null, null};
		}
		String name = agencyName.trim();
		if (agencyType.contains("시군구")) {
			int space = name.indexOf(' ');
			if (space > 0) {
				return new String[] {name.substring(0, space), name.substring(space + 1).trim()};
			}
			return new String[] {null, name};
		}
		if (agencyType.contains("광역") || agencyType.contains("시도")) {
			int space = name.indexOf(' ');
			return new String[] {space > 0 ? name.substring(0, space) : name, null};
		}
		return new String[] {null, null};
	}

	private static List<TagCode> tags(String life, String household, String theme) {
		List<TagCode> tags = new ArrayList<>();
		WelfareCode.fromNames(WelfareCode.Type.LIFE, life).forEach(c -> tags.add(new TagCode("LIFE", c.code())));
		WelfareCode.fromNames(WelfareCode.Type.HOUSEHOLD, household).forEach(c -> tags.add(new TagCode("HOUSEHOLD", c.code())));
		WelfareCode.fromNames(WelfareCode.Type.THEME, theme).forEach(c -> tags.add(new TagCode("THEME", c.code())));
		return tags;
	}

	private static List<Reference> nationalReferences(SourceRecord detail) {
		List<Reference> refs = new ArrayList<>();
		addRefs(refs, "CONTACT", detail.list("inqplCtadrList"), "servSeDetailNm", "servSeDetailLink");
		addRefs(refs, "SITE", detail.list("inqplHmpgReldList"), "servSeDetailNm", "servSeDetailLink");
		addRefs(refs, "LAW", detail.list("baslawList"), "servSeDetailNm", "servSeDetailLink");
		addRefs(refs, "FORM", detail.list("basfrmList"), "servSeDetailNm", "servSeDetailLink");
		addRefs(refs, "APPLY", detail.list("applmetList"), "servSeDetailNm", "servSeDetailLink");
		return refs;
	}

	private static List<Reference> localReferences(SourceRecord detail) {
		List<Reference> refs = new ArrayList<>();
		addRefs(refs, "CONTACT", detail.list("inqplCtadrList"), "wlfareInfoReldNm", "wlfareInfoReldCn");
		addRefs(refs, "SITE", detail.list("inqplHmpgReldList"), "wlfareInfoReldNm", "wlfareInfoReldCn");
		addRefs(refs, "LAW", detail.list("baslawList"), "wlfareInfoReldNm", "wlfareInfoReldCn");
		addRefs(refs, "FORM", detail.list("basfrmList"), "wlfareInfoReldNm", "wlfareInfoReldCn");
		return refs;
	}

	private static void addRefs(List<Reference> refs, String type, List<Map<String, String>> items, String nameKey, String valueKey) {
		for (Map<String, String> item : items) {
			String name = item.get(nameKey) == null ? null : item.get(nameKey).trim();
			String value = item.get(valueKey) == null ? null : item.get(valueKey).trim();
			if (name != null || value != null) {
				refs.add(new Reference(type, cut(name, 500), value));
			}
		}
	}

	/** 중앙부처 신청 절차(applmetList): "신청기관연락처목록: 거주지 읍/면/동 주민센터 …" 줄 목록 */
	private static String applySteps(List<Map<String, String>> items) {
		if (items.isEmpty()) {
			return null;
		}
		return items.stream()
				.map(i -> join(": ", i.get("servSeDetailNm") == null ? null : i.get("servSeDetailNm").trim(), i.get("servSeDetailLink")))
				.filter(s -> s != null && !s.isBlank())
				.collect(Collectors.joining("\n"));
	}

	private static Boolean yn(String value) {
		if ("Y".equalsIgnoreCase(value)) {
			return true;
		}
		if ("N".equalsIgnoreCase(value)) {
			return false;
		}
		return null;
	}

	private static Short shortValue(String value) {
		try {
			return value == null ? null : Short.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	static LocalDate date(String yyyymmdd) {
		if (yyyymmdd == null || yyyymmdd.trim().length() != 8) {
			return null;
		}
		try {
			return LocalDate.parse(yyyymmdd.trim(), YMD);
		}
		catch (Exception e) {
			return null;
		}
	}

	/** 정부24 목록 수정일시 yyyyMMddHHmmss (상세는 yyyy-MM-dd라 다르다) */
	static OffsetDateTime dateTime(String value) {
		if (value == null) {
			return null;
		}
		String v = value.trim();
		try {
			if (v.length() == 14) {
				return LocalDateTime.parse(v, YMD_HMS).atZone(TimeConfig.ZONE).toOffsetDateTime();
			}
			if (v.length() == 10 && v.charAt(4) == '-') {
				return LocalDate.parse(v).atStartOfDay(TimeConfig.ZONE).toOffsetDateTime();
			}
			return startOfDay(date(v));
		}
		catch (Exception e) {
			return null;
		}
	}

	private static OffsetDateTime startOfDay(LocalDate date) {
		return date == null ? null : date.atStartOfDay(TimeConfig.ZONE).toOffsetDateTime();
	}

	private static String left(String s, int n) {
		return s == null || s.length() <= n ? s : s.substring(0, n);
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String join(String sep, String a, String b) {
		if (a == null || a.isBlank()) {
			return b;
		}
		if (b == null || b.isBlank()) {
			return a;
		}
		return a.trim() + sep + b.trim();
	}

	static String cut(String s, int max) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.length() <= max ? t : t.substring(0, max);
	}

}
