package com.fourcut.photo.auth;

import com.fourcut.photo.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// @Component 를 붙이면 안 된다. Filter 타입 빈은 일반 서블릿 체인에도 자동 등록되어
// 시큐리티 체인 밖에서 한 번 더 실행된다. SecurityConfig 에서 직접 생성한다.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String ERROR_CODE_ATTRIBUTE = "fourcut.auth.errorCode";

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String ROLE_PREFIX = "ROLE_";

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	// 여기서는 응답을 쓰지도, 예외를 던지지도 않는다. 인증에 실패해도 그대로 통과시키고
	// 거부 여부는 인가 규칙에 따라 EntryPoint 가 판단한다. 그래야 permitAll 경로에
	// 낡은 토큰이 붙어 와도 그 요청이 깨지지 않는다.
	@Override
	protected void doFilterInternal(
		HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
	) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			try {
				MemberPrincipal principal = jwtTokenProvider.parse(header.substring(BEARER_PREFIX.length()));
				SecurityContextHolder.getContext().setAuthentication(toAuthentication(principal));
			} catch (ApiException e) {
				SecurityContextHolder.clearContext();
				// 만료와 무효를 구분해 전달해야 클라이언트가 재발급과 재로그인을 나눠 처리할 수 있다.
				request.setAttribute(ERROR_CODE_ATTRIBUTE, e.getErrorCode());
			}
		}
		filterChain.doFilter(request, response);
	}

	private Authentication toAuthentication(MemberPrincipal principal) {
		return new UsernamePasswordAuthenticationToken(
			principal, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + principal.role().name())));
	}
}
