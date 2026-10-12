package com.silverhelper.pipeline.service;

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
