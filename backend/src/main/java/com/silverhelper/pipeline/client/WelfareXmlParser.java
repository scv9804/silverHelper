package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * 복지로(중앙부처·지자체) XML 응답 파서 (public-api.md §2, §3, §6.1).
 * <ul>
 *   <li>값이 없는 요소는 응답에서 빠진다 → fields에 키가 없다</li>
 *   <li>자식 요소가 있는 요소(servList, applmetList 등)는 1건이어도 리스트로 모은다</li>
 *   <li>루트가 {@code OpenAPI_ServiceResponse}면 게이트웨이 오류, resultCode가 0이 아니면 결과 오류</li>
 * </ul>
 */
public final class WelfareXmlParser {

	private static final String GATEWAY_ROOT = "OpenAPI_ServiceResponse";
	private static final String LIST_ITEM = "servList";
	private static final Set<String> META = Set.of("resultCode", "resultMessage", "totalCount", "pageNo", "numOfRows");

	private WelfareXmlParser() {
	}

	public static SourcePage parseList(Source source, byte[] body) {
		Element root = parseChecked(source, body);
		long total = parseLong(childText(root, "totalCount"));
		List<SourceRecord> items = new ArrayList<>();
		for (Element child : children(root)) {
			if (LIST_ITEM.equals(child.getTagName())) {
				items.add(new SourceRecord(fieldsOf(child), serialize(child)));
			}
		}
		return new SourcePage(total, items);
	}

	public static SourceRecord parseDetail(Source source, byte[] body) {
		Element root = parseChecked(source, body);
		return new SourceRecord(fieldsOf(root), serialize(root));
	}

	/** 오류 응답이면 예외를 던진다. HTTP 오류 상태일 때 본문 확인용 */
	public static void checkError(Source source, byte[] body) {
		parseChecked(source, body);
	}

	private static Element parseChecked(Source source, byte[] body) {
		Element root;
		try {
			root = newBuilder().parse(new ByteArrayInputStream(body)).getDocumentElement();
		}
		catch (Exception e) {
			throw new PublicApiException(source, PublicApiException.Kind.PARSE, "-", "XML을 읽을 수 없습니다: " + e.getMessage());
		}
		if (GATEWAY_ROOT.equals(root.getTagName())) {
			Element header = firstChild(root, "cmmMsgHeader");
			Element base = header == null ? root : header;
			String code = childText(base, "returnReasonCode");
			String message = (nullToEmpty(childText(base, "errMsg")) + " " + nullToEmpty(childText(base, "returnAuthMsg"))).trim();
			throw error(source, PublicApiException.Kind.GATEWAY, code, message);
		}
		String resultCode = childText(root, "resultCode");
		if (resultCode != null && !resultCode.isBlank() && !isSuccess(resultCode)) {
			throw error(source, PublicApiException.Kind.RESULT, resultCode, childText(root, "resultMessage"));
		}
		return root;
	}

	private static PublicApiException error(Source source, PublicApiException.Kind kind, String code, String message) {
		if ("22".equals(code)) {
			return new QuotaExceededException(source, message);
		}
		return new PublicApiException(source, kind, code == null ? "-" : code, message);
	}

	private static boolean isSuccess(String code) {
		return code.trim().chars().allMatch(c -> c == '0');
	}

	private static Map<String, Object> fieldsOf(Element element) {
		Map<String, Object> fields = new LinkedHashMap<>();
		for (Element child : children(element)) {
			String name = child.getTagName();
			if (META.contains(name)) {
				continue;
			}
			List<Element> grandChildren = children(child);
			if (grandChildren.isEmpty()) {
				String text = child.getTextContent();
				if (text != null && !text.isBlank()) {
					fields.put(name, text);
				}
				continue;
			}
			Map<String, String> entry = new LinkedHashMap<>();
			for (Element g : grandChildren) {
				String text = g.getTextContent();
				if (text != null && !text.isBlank()) {
					entry.put(g.getTagName(), text);
				}
			}
			@SuppressWarnings("unchecked")
			List<Map<String, String>> list = (List<Map<String, String>>) fields.computeIfAbsent(name, k -> new ArrayList<>());
			list.add(entry);
		}
		return fields;
	}

	private static List<Element> children(Element element) {
		List<Element> result = new ArrayList<>();
		for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element e) {
				result.add(e);
			}
		}
		return result;
	}

	private static Element firstChild(Element element, String name) {
		return children(element).stream().filter(e -> name.equals(e.getTagName())).findFirst().orElse(null);
	}

	private static String childText(Element element, String name) {
		Element child = firstChild(element, name);
		return child == null ? null : child.getTextContent().trim();
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}

	private static long parseLong(String s) {
		try {
			return s == null ? 0 : Long.parseLong(s.trim());
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	private static DocumentBuilder newBuilder() throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// 외부 엔티티(XXE) 차단
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder();
	}

	private static String serialize(Element element) {
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			Transformer transformer = factory.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StringWriter out = new StringWriter();
			transformer.transform(new DOMSource(element), new StreamResult(out));
			return out.toString();
		}
		catch (Exception e) {
			return element.getTextContent();
		}
	}

}
