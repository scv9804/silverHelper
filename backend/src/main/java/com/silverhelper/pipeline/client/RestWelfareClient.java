package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;
import org.springframework.web.client.RestClient;

/**
 * 복지로 XML API 클라이언트.
 * <ul>
 *   <li>A 중앙부처: {@code /NationalWelfarelistV001?callTp=L&srchKeyCode=001}, {@code /NationalWelfaredetailedV001?callTp=D}</li>
 *   <li>B 지자체: {@code /LcgvWelfarelist}, {@code /LcgvWelfaredetailed}</li>
 * </ul>
 */
public class RestWelfareClient implements WelfareClient {

	private final Source source;
	private final String apiKey;
	private final PublicApiTransport transport;

	public RestWelfareClient(Source source, RestClient.Builder builder, String baseUrl, String apiKey, ApiQuota quota) {
		if (source == Source.GOV24) {
			throw new IllegalArgumentException("정부24는 Gov24Client를 쓴다");
		}
		this.source = source;
		this.apiKey = apiKey;
		this.transport = new PublicApiTransport(source, builder.clone().baseUrl(baseUrl).build(), quota);
	}

	@Override
	public Source source() {
		return source;
	}

	@Override
	public SourcePage list(int pageNo, int numOfRows) {
		return transport.get("목록", u -> {
			if (source == Source.NATIONAL) {
				// srchKeyCode를 빼면 resultCode=10 (public-api.md §2.2)
				u.path("/NationalWelfarelistV001").queryParam("callTp", "L").queryParam("srchKeyCode", "001");
			}
			else {
				u.path("/LcgvWelfarelist");
			}
			return u.queryParam("serviceKey", apiKey)
					.queryParam("pageNo", pageNo)
					.queryParam("numOfRows", numOfRows)
					.build();
		}, res -> {
			checkHttp(res);
			return WelfareXmlParser.parseList(source, res.body());
		});
	}

	@Override
	public SourceRecord detail(String servId) {
		return transport.get("상세", u -> {
			if (source == Source.NATIONAL) {
				u.path("/NationalWelfaredetailedV001").queryParam("callTp", "D");
			}
			else {
				u.path("/LcgvWelfaredetailed");
			}
			return u.queryParam("serviceKey", apiKey).queryParam("servId", servId).build();
		}, res -> {
			checkHttp(res);
			return WelfareXmlParser.parseDetail(source, res.body());
		});
	}

	private void checkHttp(PublicApiTransport.Response res) {
		if (res.status() == 429) {
			throw new QuotaExceededException(source, "요청 한도 초과 (HTTP 429)");
		}
		if (res.status() >= 400) {
			// 게이트웨이는 오류도 XML 본문으로 알려준다. 본문에 오류가 없으면 HTTP 오류로 본다
			WelfareXmlParser.checkError(source, res.body());
			throw new PublicApiException(source, PublicApiException.Kind.HTTP, String.valueOf(res.status()), "HTTP 오류");
		}
	}

}
