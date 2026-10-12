package com.silverhelper.common.response;

import com.silverhelper.common.exception.ErrorCode;
import java.util.List;

/**
 * 모든 API 응답의 공통 형식 (docs/specs/openapi.yaml ApiResponse).
 * 성공이면 data, 실패면 error를 채운다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> fail(ErrorCode code) {
		return fail(code, code.getMessage(), List.of());
	}

	public static ApiResponse<Void> fail(ErrorCode code, String message, List<ErrorBody.FieldError> fieldErrors) {
		return new ApiResponse<>(false, null, new ErrorBody(code.name(), message, fieldErrors));
	}

}
