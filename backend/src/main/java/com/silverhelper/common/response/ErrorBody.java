package com.silverhelper.common.response;

import java.util.List;

public record ErrorBody(String code, String message, List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

}
