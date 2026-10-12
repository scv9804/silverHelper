package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;
import java.net.URI;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * 공공 API GET 호출 공통 처리: 한도 예약 → 호출 → 응답 바이트 반환 → 파싱.
 * 4xx/5xx도 예외로 바꾸지 않고 본문을 돌려준다(오류 본문 해석은 각 파서가 한다).
 * 파서가 한도 초과(22)를 알리면 그날 해당 원천을 막는다. 예외 메시지와 로그에서 serviceKey를 가린다.
 */
public class PublicApiTransport {

	private static final Logger log = LoggerFactory.getLogger(PublicApiTransport.class);
	private static final Pattern SERVICE_KEY = Pattern.compile("(?i)(serviceKey=)[^&\\s\"']+");

	public record Response(int status, byte[] body) {
	}

	@FunctionalInterface
	public interface Parser<T> {
		T parse(Response response);
	}

	private final Source source;
	private final RestClient http;
	private final ApiQuota quota;

	public PublicApiTransport(Source source, RestClient http, ApiQuota quota) {
		this.source = source;
		this.http = http;
		this.quota = quota;
	}

	public <T> T get(String operation, Function<UriBuilder, URI> uri, Parser<T> parser) {
		quota.acquire(source);
		long started = System.nanoTime();
		Response response;
		try {
			response = http.get().uri(uri)
					.exchange((req, res) -> new Response(res.getStatusCode().value(), res.getBody().readAllBytes()));
		}
		catch (RestClientException e) {
			throw new PublicApiException(source, PublicApiException.Kind.IO, "-", operation + " 호출 실패: " + mask(e.getMessage()));
		}
		log.debug("{} {} HTTP {} ({} ms)", source, operation, response.status(), (System.nanoTime() - started) / 1_000_000);
		return handleQuota(() -> parser.parse(response));
	}

	private <T> T handleQuota(Supplier<T> parse) {
		try {
			return parse.get();
		}
		catch (QuotaExceededException e) {
			quota.exhaust(source);
			throw e;
		}
	}

	public static String mask(String text) {
		return text == null ? null : SERVICE_KEY.matcher(text).replaceAll("$1***");
	}

}
