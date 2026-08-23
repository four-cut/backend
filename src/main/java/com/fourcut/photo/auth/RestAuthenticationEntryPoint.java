package com.fourcut.photo.auth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

// 시큐리티 필터에서 나는 예외는 DispatcherServlet 앞이라 @RestControllerAdvice 를 타지 않는다.
// 여기서 JSON 을 직접 만들면 GlobalExceptionHandler 와 형식이 서서히 어긋나므로,
// MVC 예외 처리 파이프라인으로 되돌려보내 기존 핸들러가 그대로 응답을 만들게 한다.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HandlerExceptionResolver handlerExceptionResolver;

	public RestAuthenticationEntryPoint(
		@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
	) {
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	@Override
	public void commence(
		HttpServletRequest request, HttpServletResponse response, AuthenticationException authException
	) {
		Object attribute = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
		ErrorCode errorCode = attribute instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED;
		handlerExceptionResolver.resolveException(request, response, null, new ApiException(errorCode));
	}
}
