package com.silverhelper.pipeline.domain;

/** collect_run.status. PARTIAL = 한도 때문에 상세를 다 받지 못했거나 일부 항목이 실패함 */
public enum CollectRunStatus {
	RUNNING, SUCCEEDED, PARTIAL, FAILED
}
