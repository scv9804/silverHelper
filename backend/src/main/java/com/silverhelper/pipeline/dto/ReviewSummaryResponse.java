package com.silverhelper.pipeline.dto;

import com.silverhelper.pipeline.domain.ReviewReason;
import com.silverhelper.pipeline.domain.ReviewStatus;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.repository.ReviewStore.ReviewRow;
import java.time.OffsetDateTime;

/** openapi.yaml ReviewSummary */
public record ReviewSummaryResponse(
		long id,
		long benefitId,
		String benefitName,
		Source source,
		ReviewReason reason,
		ReviewStatus status,
		OffsetDateTime createdAt,
		OffsetDateTime resolvedAt,
		String resolvedBy) {

	public static ReviewSummaryResponse from(ReviewRow r) {
		return new ReviewSummaryResponse(r.id(), r.benefitId(), r.benefitName(), r.source(), r.reason(), r.status(),
				r.createdAt(), r.resolvedAt(), r.resolvedBy());
	}

}
