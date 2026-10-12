package com.silverhelper.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. 값을 추가하면 docs/specs/openapi.yaml ErrorBody.code enum도 함께 고친다.
 */
public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	BENEFIT_NOT_FOUND(HttpStatus.NOT_FOUND, "혜택을 찾을 수 없습니다."),
	REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "검토 항목을 찾을 수 없습니다."),
	REVIEW_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 처리된 검토 항목입니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

}
