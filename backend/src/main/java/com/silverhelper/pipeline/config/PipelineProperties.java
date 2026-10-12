package com.silverhelper.pipeline.config;

import com.silverhelper.pipeline.domain.Source;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 파이프라인 설정 (prefix {@code pipeline}). 값은 모두 코드 기본값이 있다.
 * 인증키는 {@code pipeline.public-data.api-key}가 비어 있으면 환경변수 {@code PUBLIC_DATA_API_KEY}를 쓴다.
 */
@ConfigurationProperties(prefix = "pipeline")
public record PipelineProperties(
		@DefaultValue PublicData publicData,
		@DefaultValue Quota quota,
		@DefaultValue Schedule schedule,
		@DefaultValue("false") boolean runOnStartup,
		@DefaultValue({"NATIONAL", "LOCAL", "GOV24"}) List<Source> sources,
		@DefaultValue Ai ai) {

	public record PublicData(
			String apiKey,
			@DefaultValue("https://apis.data.go.kr/B554287/NationalWelfareInformationsV001") String nationalBaseUrl,
			@DefaultValue("https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations") String localBaseUrl,
			@DefaultValue("https://api.odcloud.kr/api/gov24/v3") String gov24BaseUrl,
			@DefaultValue("500") int nationalPageSize,
			@DefaultValue("1000") int localPageSize,
			@DefaultValue("1000") int gov24PageSize,
			@DefaultValue("10s") Duration connectTimeout,
			@DefaultValue("60s") Duration readTimeout) {
	}

	/** 원천별 하루 호출 상한 (public-api.md §8). 개발계정 한도보다 낮게 둔다 */
	public record Quota(
			@DefaultValue("80") int national,
			@DefaultValue("900") int local,
			@DefaultValue("9000") int gov24) {

		public int limit(Source source) {
			return switch (source) {
				case NATIONAL -> national;
				case LOCAL -> local;
				case GOV24 -> gov24;
			};
		}

	}

	public record Schedule(
			@DefaultValue("false") boolean enabled,
			@DefaultValue("0 0 4 * * *") String cron) {
	}

	/**
	 * @param realEnabled false면 API 키가 있어도 규칙 기반 가짜 추출기를 쓴다
	 * @param maxPerRun   한 번 실행에 AI로 처리할 혜택 수 상한 (비용 통제). 나머지는 다음 실행에서 이어서 한다
	 */
	public record Ai(
			@DefaultValue("true") boolean realEnabled,
			@DefaultValue("200") int maxPerRun,
			@DefaultValue("eligibility-v1") String promptVersion) {
	}

}
