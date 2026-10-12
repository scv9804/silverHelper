package com.silverhelper.common.exception;

/**
 * 도메인 규칙 위반을 알리는 예외. 전역 핸들러가 ErrorCode에 맞는 HTTP 상태와 응답으로 바꾼다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

}
