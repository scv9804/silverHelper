package com.silverhelper.pipeline.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.support.Fixtures;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

// RestClient 구현을 MockRestServiceServer로 확인한다. 실제 네트워크 호출 없음
class RestClientTests {

	private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	/** 메모리 한도. 상한 도달과 22 처리 확인용 */
	static class MemoryQuota implements ApiQuota {
		final Map<Source, Integer> used = new EnumMap<>(Source.class);
		final int limit;

		MemoryQuota(int limit) {
			this.limit = limit;
		}

		@Override
		public void acquire(Source source) {
			int n = used.getOrDefault(source, 0);
			if (n >= limit) {
				throw new QuotaExceededException(source, "상한");
			}
			used.put(source, n + 1);
		}

		@Override
		public void exhaust(Source source) {
			used.put(source, limit);
		}

		@Override
		public int remaining(Source source) {
			return limit - used.getOrDefault(source, 0);
		}
	}

	private RestClient.Builder builder;
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
	}

	@Test
	void 중앙부처_목록은_필수_파라미터를_붙여_호출한다() {
		RestWelfareClient client = new RestWelfareClient(Source.NATIONAL, builder, "https://apis.example/A", KEY, new MemoryQuota(10));
		server.expect(requestTo(startsWith("https://apis.example/A/NationalWelfarelistV001")))
				.andExpect(queryParam("callTp", "L"))
				.andExpect(queryParam("srchKeyCode", "001"))
				.andExpect(queryParam("numOfRows", "500"))
				.andExpect(queryParam("serviceKey", KEY))
				.andRespond(withSuccess(Fixtures.bytes("national-list.xml"), MediaType.APPLICATION_XML));

		SourcePage page = client.list(1, 500);

		assertThat(page.items()).hasSize(3);
		server.verify();
	}

	@Test
	void 지자체_상세를_호출한다() {
		RestWelfareClient client = new RestWelfareClient(Source.LOCAL, builder, "https://apis.example/B", KEY, new MemoryQuota(10));
		server.expect(requestTo(startsWith("https://apis.example/B/LcgvWelfaredetailed")))
				.andExpect(queryParam("servId", "WLF00002281"))
				.andRespond(withSuccess(Fixtures.bytes("local-detail-WLF00002281.xml"), MediaType.APPLICATION_XML));

		assertThat(client.detail("WLF00002281").text("sggNm")).isEqualTo("보성군");
	}

	@Test
	void 상한에_도달하면_호출하지_않는다() {
		MemoryQuota quota = new MemoryQuota(1);
		quota.exhaust(Source.NATIONAL);
		RestWelfareClient client = new RestWelfareClient(Source.NATIONAL, builder, "https://apis.example/A", KEY, quota);

		assertThatThrownBy(() -> client.list(1, 500)).isInstanceOf(QuotaExceededException.class);
		// 기대한 요청이 없으므로 요청이 나갔다면 위에서 AssertionError가 났다
		server.verify();
	}

	@Test
	void 코드_22를_받으면_그날_해당_원천을_막는다() {
		MemoryQuota quota = new MemoryQuota(80);
		RestWelfareClient client = new RestWelfareClient(Source.NATIONAL, builder, "https://apis.example/A", KEY, quota);
		String limited = Fixtures.text("gateway-error-unregistered-key.xml")
				.replace("<returnReasonCode>30</returnReasonCode>", "<returnReasonCode>22</returnReasonCode>");
		server.expect(requestTo(startsWith("https://apis.example/A/NationalWelfaredetailedV001")))
				.andRespond(withSuccess(limited, MediaType.APPLICATION_XML));

		assertThatThrownBy(() -> client.detail("WLF00001164")).isInstanceOf(QuotaExceededException.class);
		assertThat(quota.remaining(Source.NATIONAL)).isZero();
		assertThatThrownBy(() -> client.detail("WLF00001177")).isInstanceOf(QuotaExceededException.class);
		server.verify();
	}

	@Test
	void HTTP_오류여도_게이트웨이_XML이면_게이트웨이_오류로_본다() {
		RestWelfareClient client = new RestWelfareClient(Source.LOCAL, builder, "https://apis.example/B", KEY, new MemoryQuota(10));
		server.expect(requestTo(startsWith("https://apis.example/B/LcgvWelfarelist")))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(Fixtures.bytes("gateway-error-unregistered-key.xml")));

		assertThatThrownBy(() -> client.list(1, 1000))
				.isInstanceOfSatisfying(PublicApiException.class, e -> assertThat(e.getCode()).isEqualTo("30"));
	}

	@Test
	void 정부24는_인증키를_헤더로_보내고_URL에_남기지_않는다() {
		RestGov24Client client = new RestGov24Client(builder, "https://api.example/gov24/v3", KEY, new MemoryQuota(10),
				JsonMapper.builder().build());
		server.expect(requestTo("https://api.example/gov24/v3/serviceList?page=1&perPage=1000"))
				.andExpect(header("Authorization", "Infuser " + KEY))
				.andRespond(withSuccess(Fixtures.bytes("gov24-list.json"), MediaType.APPLICATION_JSON));

		assertThat(client.serviceList(1, 1000).items()).hasSize(3);
		server.verify();
	}

	@Test
	void 정부24_지원조건_1건은_서비스ID_조건으로_조회한다() {
		RestGov24Client client = new RestGov24Client(builder, "https://api.example/gov24/v3", KEY, new MemoryQuota(10),
				JsonMapper.builder().build());
		server.expect(requestTo(startsWith("https://api.example/gov24/v3/supportConditions")))
				.andExpect(request -> assertThat(URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
						.contains("cond[서비스ID::EQ]=135200005031"))
				.andRespond(withSuccess(Fixtures.bytes("gov24-conditions.json"), MediaType.APPLICATION_JSON));

		assertThat(client.supportConditionsOf("135200005031").items()).isNotEmpty();
		server.verify();
	}

	@Test
	void 정부24_401_인증오류() {
		RestGov24Client client = new RestGov24Client(builder, "https://api.example/gov24/v3", KEY, new MemoryQuota(10),
				JsonMapper.builder().build());
		server.expect(requestTo(startsWith("https://api.example/gov24/v3/serviceList")))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"code\":-401,\"msg\":\"유효하지 않은 인증키 입니다.\"}"));

		assertThatThrownBy(() -> client.serviceList(1, 10))
				.isInstanceOfSatisfying(PublicApiException.class, e -> assertThat(e.stopsSource()).isTrue());
	}

	@Test
	void 예외_메시지에서_serviceKey를_가린다() {
		String masked = PublicApiTransport.mask("I/O error on GET https://apis.data.go.kr/x?serviceKey=" + KEY + "&pageNo=1");

		assertThat(masked).doesNotContain(KEY).contains("serviceKey=***&pageNo=1");
	}

}
