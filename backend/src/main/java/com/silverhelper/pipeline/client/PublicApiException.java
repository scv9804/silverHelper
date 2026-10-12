package com.silverhelper.pipeline.client;

import com.silverhelper.pipeline.domain.Source;

/**
 * 공공 API 호출 실패.
 * <ul>
 *   <li>GATEWAY: data.go.kr 게이트웨이 오류 ({@code OpenAPI_ServiceResponse}) 또는 odcloud 인증 오류. 키·권한 문제라 그날 해당 원천은 더 호출해도 소용없다</li>
 *   <li>RESULT: 정상 응답 구조인데 resultCode가 0이 아님 (잘못된 파라미터 등). 해당 요청만 실패</li>
 *   <li>HTTP / IO / PARSE: 전송 오류, 읽을 수 없는 응답</li>
 * </ul>
 * 메시지에는 인증키가 들어가지 않는다.
 */
public class PublicApiException extends RuntimeException {

	public enum Kind {
		GATEWAY, RESULT, HTTP, IO, PARSE
	}

	private final Source source;
	private final Kind kind;
	private final String code;

	public PublicApiException(Source source, Kind kind, String code, String message) {
		super("[%s %s %s] %s".formatted(source, kind, code, message));
		this.source = source;
		this.kind = kind;
		this.code = code;
	}

	public Source getSource() {
		return source;
	}

	public Kind getKind() {
		return kind;
	}

	public String getCode() {
		return code;
	}

	/** 이 원천의 남은 작업을 그만둬야 하는 오류인지 (키 오류, 한도 초과) */
	public boolean stopsSource() {
		return kind == Kind.GATEWAY;
	}

}
