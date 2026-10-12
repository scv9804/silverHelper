package com.silverhelper.pipeline.domain;

/**
 * 혜택 원천 (benefit.source). NATIONAL = 복지로 중앙부처, LOCAL = 복지로 지자체, GOV24 = 정부24 공공서비스
 */
public enum Source {

	NATIONAL, LOCAL, GOV24;

	/** 그룹 대표 선택 우선순위 (ADR 0002 §3). 작을수록 먼저 */
	public int primaryRank() {
		return ordinal();
	}

}
