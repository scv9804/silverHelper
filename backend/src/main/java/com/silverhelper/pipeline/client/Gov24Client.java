package com.silverhelper.pipeline.client;

/**
 * 정부24 공공서비스(혜택) API (C). 모든 호출은 하루 한도를 1건씩 쓴다.
 * 목록에 지원대상·선정기준·지원내용 원문이 있어 상세(serviceDetail)는 쓰지 않는다 (public-api.md §8).
 */
public interface Gov24Client {

	/** 목록 1페이지. page는 1부터 */
	SourcePage serviceList(int page, int perPage);

	/** 지원조건(구조화) 1페이지. 조건 없이 전체를 받는다 */
	SourcePage supportConditions(int page, int perPage);

	/** 서비스 1건의 지원조건. 없으면 빈 페이지 */
	SourcePage supportConditionsOf(String serviceId);

}
