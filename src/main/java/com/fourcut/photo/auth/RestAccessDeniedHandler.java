package com.fourcut.photo.auth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final HandlerExceptionResolver handlerExceptionResolver;

	public RestAccessDeniedHandler(
		@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
	) {
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	@Override
	public void handle(
		HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException
	) {
		handlerExceptionResolver.resolveException(
			request, response, null, new ApiException(ErrorCode.FORBIDDEN));
	}
}
