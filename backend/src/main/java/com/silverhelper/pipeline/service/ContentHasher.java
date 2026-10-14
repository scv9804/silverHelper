package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.domain.RawKind;
import com.silverhelper.pipeline.domain.Source;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 변경 감지용 정규화 해시 (ADR 0002 §4, public-api.md §8).
 * <ul>
 *   <li>조회수(inqNum, 조회수)는 매일 바뀌므로 뺀다</li>
 *   <li>CR(&amp;#13;)·줄바꿈·연속 공백은 공백 하나로, 가운뎃점 변형(ㆍ・‧)은 ·로, · 주변 공백은 없앤다</li>
 *   <li>필드 순서와 상관없이 같은 값이면 같은 해시</li>
 * </ul>
 */
public final class ContentHasher {

	public static final Set<String> EXCLUDED = Set.of("inqNum", "조회수");

	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");
	private static final Pattern MIDDLE_DOT = Pattern.compile("\\s*[·ㆍ・‧]\\s*");

	private ContentHasher() {
	}

	public static String hash(Map<String, Object> fields) {
		StringBuilder canonical = new StringBuilder();
		append(canonical, fields);
		return sha256(canonical.toString());
	}

	/** 원천과 응답 종류별 표시·AI 입력 필드만 변경 감지에 사용한다. */
	public static String hash(Source source, RawKind kind, Map<String, Object> fields) {
		Set<String> allowed = switch (kind) {
			case CONDITIONS -> Set.of("서비스ID", "JA0110", "JA0111", "JA0328", "JA0329", "JA0404");
			case DETAIL -> source == Source.NATIONAL
					? Set.of("servNm", "wlfareInfoOutlCn", "crtrYr", "tgtrDtlCn", "slctCritCn", "alwServCn", "applmetList", "inqplCtadrList", "inqplHmpgReldList", "baslawList", "basfrmList")
					: Set.of("servNm", "servDgst", "sprtTrgtCn", "slctCritCn", "alwServCn", "aplyMtdCn", "enfcBgngYmd", "enfcEndYmd", "inqplCtadrList", "inqplHmpgReldList", "baslawList", "basfrmList");
			case LIST -> source == Source.GOV24
					? Set.of("서비스ID", "서비스명", "서비스목적요약", "지원대상", "선정기준", "지원내용", "신청방법", "신청기한", "지원유형", "소관기관명", "소관기관유형", "부서명", "전화문의", "등록일시", "수정일시", "상세조회URL", "서비스분야", "사용자구분")
					: source == Source.NATIONAL
					? Set.of("servId", "servNm", "servDgst", "servDtlLink", "jurMnofNm", "jurOrgNm", "sprtCycNm", "srvPvsnNm", "onapPsbltYn", "rprsCtadr", "svcfrstRegTs", "lifeArray", "trgterIndvdlArray", "intrsThemaArray")
					: Set.of("servId", "servNm", "servDgst", "servDtlLink", "ctpvNm", "sggNm", "bizChrDeptNm", "sprtCycNm", "srvPvsnNm", "aplyMtdNm", "lastModYmd", "lifeNmArray", "trgterIndvdlNmArray", "intrsThemaNmArray");
		};
		Map<String, Object> selected = new TreeMap<>();
		fields.forEach((key, value) -> { if (allowed.contains(key)) selected.put(key, value); });
		return hash(selected);
	}

	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String s = WHITESPACE.matcher(text).replaceAll(" ");
		s = MIDDLE_DOT.matcher(s).replaceAll("·");
		return s.trim();
	}

	@SuppressWarnings("unchecked")
	private static void append(StringBuilder out, Object value) {
		if (value instanceof Map<?, ?> map) {
			TreeMap<String, Object> sorted = new TreeMap<>();
			((Map<String, Object>) map).forEach((k, v) -> {
				if (!EXCLUDED.contains(k)) {
					sorted.put(k, v);
				}
			});
			out.append('{');
			sorted.forEach((k, v) -> {
				String normalized = v instanceof String s ? normalize(s) : null;
				if (v == null || "".equals(normalized)) {
					return;
				}
				out.append(k).append('=');
				append(out, v);
				out.append(';');
			});
			out.append('}');
		}
		else if (value instanceof List<?> list) {
			out.append('[');
			list.forEach(item -> {
				append(out, item);
				out.append(',');
			});
			out.append(']');
		}
		else if (value instanceof String s) {
			out.append(normalize(s).replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\="));
		}
		else {
			out.append(value);
		}
	}

	private static String sha256(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
