package com.silverhelper.pipeline.dto;

import com.silverhelper.pipeline.domain.AiProvider;
import com.silverhelper.pipeline.domain.ReviewReason;
import com.silverhelper.pipeline.domain.ReviewStatus;
import com.silverhelper.pipeline.domain.Source;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** openapi.yaml ReviewDetail (ReviewSummary + 원문, 두 모델 결과, 차이) */
public record ReviewDetailResponse(
		long id,
		long benefitId,
		String benefitName,
		Source source,
		ReviewReason reason,
		ReviewStatus status,
		OffsetDateTime createdAt,
		OffsetDateTime resolvedAt,
		String resolvedBy,
		String sourceUrl,
		Map<String, String> sourceText,
		EligibilityDto currentEligibility,
		List<Extraction> extractions,
		List<Difference> differences,
		GroupCandidate groupCandidate,
		String resolutionNote) {

	public record Extraction(long id, AiProvider provider, String model, String promptVersion, boolean schemaValid,
			JsonNode result, String errorMessage, OffsetDateTime createdAt) {
	}

	public record Difference(String field, JsonNode claude, JsonNode gpt, JsonNode structured) {
	}

	public record GroupCandidate(long targetBenefitId, String targetName, Source targetSource, String method,
			BigDecimal score) {
	}

}
