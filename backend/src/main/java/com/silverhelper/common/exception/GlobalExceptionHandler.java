package com.silverhelper.common.exception;

import com.silverhelper.common.response.ApiResponse;
import com.silverhelper.common.response.ErrorBody;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		ErrorCode code = e.getErrorCode();
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.fail(code, e.getMessage(), List.of()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
		List<ErrorBody.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
				.map(f -> new ErrorBody.FieldError(f.getField(), f.getDefaultMessage()))
				.toList();
		return invalid(fields);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiResponse<Void>> handleParamValidation(HandlerMethodValidationException e) {
		List<ErrorBody.FieldError> fields = e.getParameterValidationResults().stream()
				.flatMap(r -> r.getResolvableErrors().stream()
						.map(err -> new ErrorBody.FieldError(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
				.toList();
		return invalid(fields);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
		List<ErrorBody.FieldError> fields = e.getConstraintViolations().stream()
				.map(v -> new ErrorBody.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
				.toList();
		return invalid(fields);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return invalid(List.of(new ErrorBody.FieldError(e.getName(), "형식이 올바르지 않습니다.")));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
		return invalid(List.of(new ErrorBody.FieldError(e.getParameterName(), "필수 값입니다.")));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.fail(ErrorCode.INVALID_REQUEST, "요청 본문을 읽을 수 없습니다.", List.of()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
		return error(ErrorCode.NOT_FOUND);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException e) {
		return error(ErrorCode.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return error(ErrorCode.INTERNAL_ERROR);
	}

	private static ResponseEntity<ApiResponse<Void>> invalid(List<ErrorBody.FieldError> fields) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.fail(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.getMessage(), fields));
	}

	private static ResponseEntity<ApiResponse<Void>> error(ErrorCode code) {
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.fail(code));
	}

	private static String lastNode(String path) {
		int i = path.lastIndexOf('.');
		return i < 0 ? path : path.substring(i + 1);
	}

}
