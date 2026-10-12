package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;
import java.nio.charset.StandardCharsets;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정부24(odcloud) JSON API 클라이언트. 인증키는 URL에 남지 않게 {@code Authorization: Infuser {키}} 헤더로 보낸다.
 */
public class RestGov24Client implements Gov24Client {

	private final PublicApiTransport transport;
	private final JsonMapper jsonMapper;

	public RestGov24Client(RestClient.Builder builder, String baseUrl, String apiKey, ApiQuota quota, JsonMapper jsonMapper) {
		RestClient http = builder.clone()
				.baseUrl(baseUrl)
				.defaultHeader("Authorization", "Infuser " + apiKey)
				.build();
		this.transport = new PublicApiTransport(Source.GOV24, http, quota);
		this.jsonMapper = jsonMapper;
	}

	@Override
	public SourcePage serviceList(int page, int perPage) {
		return transport.get("목록", u -> u.path("/serviceList")
				.queryParam("page", page).queryParam("perPage", perPage).build(), this::parse);
	}

	@Override
	public SourcePage supportConditions(int page, int perPage) {
		return transport.get("지원조건", u -> u.path("/supportConditions")
				.queryParam("page", page).queryParam("perPage", perPage).build(), this::parse);
	}

	@Override
	public SourcePage supportConditionsOf(String serviceId) {
		return transport.get("지원조건(1건)", u -> u.path("/supportConditions")
				.queryParam("page", 1).queryParam("perPage", 10)
				.queryParam("cond[서비스ID::EQ]", serviceId).build(), this::parse);
	}

	private SourcePage parse(PublicApiTransport.Response res) {
		return Gov24JsonParser.parse(jsonMapper, res.status(), new String(res.body(), StandardCharsets.UTF_8));
	}

}
