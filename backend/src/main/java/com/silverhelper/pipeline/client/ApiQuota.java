package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;

/** 원천별 하루 호출 수 관리 */
public interface ApiQuota {

	/** 호출 1건을 예약한다. 상한이면 호출하지 말라는 뜻으로 {@link QuotaExceededException}을 던진다 */
	void acquire(Source source);

	/** 원천이 한도 초과(22)를 알려왔을 때 그날 남은 호출을 막는다 */
	void exhaust(Source source);

	/** 오늘 남은 호출 수 */
	int remaining(Source source);

}
