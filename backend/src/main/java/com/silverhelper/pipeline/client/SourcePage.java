package com.silverhelper.pipeline.client;

import java.util.List;

/**
 * 목록 응답 1페이지.
 *
 * @param totalCount 조건에 맞는 전체 건수. 정부24는 필터를 걸어도 totalCount가 전체라서 matchCount를 넣는다
 */
public record SourcePage(long totalCount, List<SourceRecord> items) {
}
