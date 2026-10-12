package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;

/**
 * 하루 호출 한도에 걸림. 우리 상한(api_daily_usage)에 도달했거나 원천이 코드 22(LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR)를 돌려줬다.
 * 그날 해당 원천은 더 호출하지 않는다.
 */
public class QuotaExceededException extends PublicApiException {

	public QuotaExceededException(Source source, String message) {
		super(source, Kind.GATEWAY, "22", message);
	}

}
