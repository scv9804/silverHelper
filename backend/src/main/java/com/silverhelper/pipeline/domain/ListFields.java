package com.silverhelper.pipeline.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 목록 응답에서 얻는 benefit 컬럼. 매 수집마다 덮어쓴다 */
public record ListFields(
		String name,
		String summary,
		String sourceUrl,
		String agencyName,
		String departmentName,
		String ctpvName,
		String sggName,
		String supportCycle,
		String provisionType,
		String applyMethodName,
		Boolean onlineApply,
		String contact,
		LocalDate sourceRegisteredOn,
		OffsetDateTime sourceModifiedAt,
		List<TagCode> tags) {
}
