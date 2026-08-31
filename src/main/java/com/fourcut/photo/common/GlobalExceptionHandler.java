package com.fourcut.photo.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
		return ResponseEntity.status(e.getErrorCode().getStatus())
			.body(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
	}

	// 한도 초과는 MVC 디스패치 안에서 끝내야 한다.
	// 여기서 안 잡으면 DefaultHandlerExceptionResolver 가 sendError(413) 을 호출하고,
	// 그게 /error 로 내부 forward 를 일으킨다. /error 는 permitAll 목록에 없어서
	// 시큐리티가 인증을 요구하고, 결국 413 대신 401 UNAUTHORIZED 가 나간다.
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
		ErrorCode errorCode = ErrorCode.FILE_TOO_LARGE;
		return ResponseEntity.status(errorCode.getStatus())
			.body(ErrorResponse.of(errorCode, errorCode.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
			.orElse("입력 값이 유효하지 않습니다.");
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
	}
}
