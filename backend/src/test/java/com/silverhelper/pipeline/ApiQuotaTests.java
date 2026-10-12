package com.silverhelper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.silverhelper.pipeline.client.QuotaExceededException;
import com.silverhelper.pipeline.client.RestWelfareClient;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.repository.JdbcApiQuota;
import com.silverhelper.pipeline.support.Fixtures;
import com.silverhelper.pipeline.support.PipelineIntegrationTest;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// api_daily_usage 하루 한도 (기본 상한 A 80, B 900, C 9000)
class ApiQuotaTests extends PipelineIntegrationTest {

	@Autowired
	JdbcApiQuota quota;

	@Test
	void 상한까지만_예약되고_다음날_다시_열린다() {
		for (int i = 0; i < 80; i++) {
			quota.acquire(Source.NATIONAL);
		}

		assertThatThrownBy(() -> quota.acquire(Source.NATIONAL)).isInstanceOf(QuotaExceededException.class);
		assertThat(quota.used(Source.NATIONAL)).isEqualTo(80);
		assertThat(quota.remaining(Source.LOCAL)).isEqualTo(900);

		clock.advance(Duration.ofDays(1));
		quota.acquire(Source.NATIONAL);
		assertThat(quota.used(Source.NATIONAL)).isEqualTo(1);
	}

	@Test
	void 동시에_예약해도_상한을_넘지_않는다() throws Exception {
		AtomicInteger granted = new AtomicInteger();
		try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
			Future<?>[] futures = new Future<?>[120];
			for (int i = 0; i < futures.length; i++) {
				futures[i] = pool.submit(() -> {
					try {
						quota.acquire(Source.NATIONAL);
						granted.incrementAndGet();
					}
					catch (QuotaExceededException ignored) {
						// 상한
					}
				});
			}
			for (Future<?> f : futures) {
				f.get();
			}
		}

		assertThat(granted).hasValue(80);
		assertThat(quota.used(Source.NATIONAL)).isEqualTo(80);
	}

	@Test
	void 상한에_도달하면_실제_클라이언트가_요청을_보내지_않는다() {
		jdbc.update("insert into api_daily_usage (source, usage_date, call_count) values ('NATIONAL', ?, 80)",
				java.time.LocalDate.now(clock));
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestWelfareClient client = new RestWelfareClient(Source.NATIONAL, builder, "https://apis.example/A", "k", quota);

		assertThatThrownBy(() -> client.list(1, 500)).isInstanceOf(QuotaExceededException.class);
		server.verify();
	}

	@Test
	void 코드_22를_받으면_그날_남은_호출을_막는다() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestWelfareClient client = new RestWelfareClient(Source.LOCAL, builder, "https://apis.example/B", "k", quota);
		server.expect(requestTo(startsWith("https://apis.example/B/LcgvWelfaredetailed")))
				.andRespond(withSuccess(Fixtures.text("gateway-error-unregistered-key.xml")
						.replace("<returnReasonCode>30</returnReasonCode>", "<returnReasonCode>22</returnReasonCode>"),
						MediaType.APPLICATION_XML));

		assertThatThrownBy(() -> client.detail("WLF00002281")).isInstanceOf(QuotaExceededException.class);

		assertThat(quota.remaining(Source.LOCAL)).isZero();
		assertThatThrownBy(() -> client.detail("WLF00005610")).isInstanceOf(QuotaExceededException.class);
		server.verify();
	}

}
