package com.silverhelper.pipeline.domain;

import java.util.EnumSet;
import java.util.Set;

/** review_item.reason */
public enum ReviewReason {

	MODEL_MISMATCH, SCHEMA_INVALID, STRUCTURED_MISMATCH, GROUP_CANDIDATE, SOURCE_REMOVED, OTHER;

	/** AI 추출 결과를 확정하지 못해서 생긴 사유. 승인하면 자격 조건을 확정한다 */
	public static final Set<ReviewReason> EXTRACTION = EnumSet.of(MODEL_MISMATCH, SCHEMA_INVALID, STRUCTURED_MISMATCH);

}
