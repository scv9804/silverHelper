package com.silverhelper.pipeline.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * 승인 요청. eligibility를 생략하면 baseExtractionId(생략 시 Claude 결과)의 결과를 그대로 확정한다.
 * GROUP_CANDIDATE·SOURCE_REMOVED 사유에서는 eligibility를 무시한다.
 */
public record ApproveReviewRequest(
		Long baseExtractionId,
		@Valid EligibilityDto eligibility,
		@Size(max = 1000) String note) {
}
