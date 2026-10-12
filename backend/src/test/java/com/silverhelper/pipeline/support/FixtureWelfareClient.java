package com.silverhelper.pipeline.support;

import com.silverhelper.pipeline.client.PublicApiException;
import com.silverhelper.pipeline.client.SourcePage;
import com.silverhelper.pipeline.client.SourceRecord;
import com.silverhelper.pipeline.client.WelfareClient;
import com.silverhelper.pipeline.client.WelfareXmlParser;
import com.silverhelper.pipeline.domain.Source;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 픽스처 XML로 응답하는 복지로 클라이언트 (네트워크 없음). 응답은 실제 파서를 거친다.
 * 상세 픽스처가 없는 서비스는 목록 항목으로 최소 상세를 만든다.
 */
public class FixtureWelfareClient implements WelfareClient {

	private static final Pattern TOTAL = Pattern.compile("<totalCount>\\d+</totalCount>");

	private final Source source;
	private final String defaultList;
	private final Map<String, String> defaultDetails;
	private String listXml;
	private final Map<String, String> details = new HashMap<>();
	private Function<String, RuntimeException> detailFailure;
	private boolean emptySecondPage;
	public final AtomicInteger listCalls = new AtomicInteger();
	public final AtomicInteger detailCalls = new AtomicInteger();

	public FixtureWelfareClient(Source source, String listXml, Map<String, String> details) {
		this.source = source;
		this.defaultList = listXml;
		this.defaultDetails = Map.copyOf(details);
		reset();
	}

	public void reset() {
		listXml = defaultList;
		details.clear();
		details.putAll(defaultDetails);
		detailFailure = null;
		emptySecondPage = false;
		listCalls.set(0);
		detailCalls.set(0);
	}

	public void setList(String xml) {
		this.listXml = xml;
	}

	public String list() {
		return listXml;
	}

	public void emptySecondPage() {
		emptySecondPage = true;
	}

	public void setDetail(String servId, String xml) {
		details.put(servId, xml);
	}

	public String detailXml(String servId) {
		return details.get(servId);
	}

	/** servId를 받아 예외를 돌려주면 그 예외를 던진다 (null이면 정상 응답) */
	public void failDetail(Function<String, RuntimeException> failure) {
		this.detailFailure = failure;
	}

	@Override
	public Source source() {
		return source;
	}

	@Override
	public SourcePage list(int pageNo, int numOfRows) {
		listCalls.incrementAndGet();
		if (pageNo > 1) {
			return new SourcePage(emptySecondPage ? 100 : 0, List.of());
		}
		int count = listXml.split("<servList>", -1).length - 1;
		String xml = TOTAL.matcher(listXml).replaceFirst("<totalCount>" + (emptySecondPage ? 100 : count) + "</totalCount>");
		return WelfareXmlParser.parseList(source, xml.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public SourceRecord detail(String servId) {
		detailCalls.incrementAndGet();
		if (detailFailure != null) {
			RuntimeException e = detailFailure.apply(servId);
			if (e != null) {
				throw e;
			}
		}
		String xml = details.get(servId);
		if (xml == null) {
			xml = synthesize(servId);
		}
		return WelfareXmlParser.parseDetail(source, xml.getBytes(StandardCharsets.UTF_8));
	}

	private String synthesize(String servId) {
		Matcher m = Pattern.compile("<servList>((?:(?!</servList>).)*<servId>" + servId + "</servId>(?:(?!</servList>).)*)</servList>")
				.matcher(listXml);
		if (!m.find()) {
			throw new PublicApiException(source, PublicApiException.Kind.RESULT, "99", "없는 서비스 " + servId);
		}
		String item = m.group(1);
		String name = tag(item, "servNm");
		String digest = tag(item, "servDgst");
		String target = source == Source.NATIONAL ? "tgtrDtlCn" : "sprtTrgtCn";
		return "<wantedDtl><resultCode>0</resultCode><servId>" + servId + "</servId><servNm>" + name + "</servNm>"
				+ "<" + target + ">" + digest + "</" + target + "></wantedDtl>";
	}

	private static String tag(String xml, String name) {
		Matcher m = Pattern.compile("<" + name + ">(.*?)</" + name + ">").matcher(xml);
		return m.find() ? m.group(1) : "";
	}

}
