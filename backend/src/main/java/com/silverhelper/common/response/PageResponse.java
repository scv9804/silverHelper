package com.silverhelper.common.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 응답 (docs/specs/openapi.yaml PageMeta + content). page는 0부터.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
		return from(page.map(mapper));
	}

}
