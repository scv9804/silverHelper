package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;

/**
 * 복지로 API (A 중앙부처, B 지자체). 두 원천이 같은 모양이라 인터페이스 하나를 원천별 빈으로 등록한다.
 * 모든 호출은 하루 한도를 1건씩 쓴다.
 */
public interface WelfareClient {

	Source source();

	/** 목록 1페이지. pageNo는 1부터 */
	SourcePage list(int pageNo, int numOfRows);

	SourceRecord detail(String servId);

}
