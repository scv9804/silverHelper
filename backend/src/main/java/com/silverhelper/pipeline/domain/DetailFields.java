package com.silverhelper.pipeline.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 상세 응답(정부24는 목록)에서 얻는 benefit 컬럼. 상세를 새로 받았을 때만 덮어쓴다.
 * contact가 null이면 목록 값을 유지한다.
 */
public record DetailFields(
		Short baseYear,
		LocalDate enforceStartDate,
		LocalDate enforceEndDate,
		String contact,
		String targetContent,
		String criteriaContent,
		String benefitContent,
		String applyContent,
		List<Reference> references) {
}
