package com.fourcut.photo.auth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

// 소셜 로그인은 요청마다 카카오/구글로 나가는 외부 호출을 동반하므로, 남용되면
// 비용과 제공자 쪽 쿼터 소진으로 직결된다.
//
// 한계: 인스턴스 메모리 기반이라 카운트가 앱마다 따로 잡히고 재시작하면 초기화된다.
// 앱을 여러 대로 늘리면 Redis 등 공유 저장소로 옮겨야 한다.
// 리버스 프록시를 앞에 두면 server.forward-headers-strategy 를 설정해야 실제 클라이언트 IP 가 잡힌다.
public class LoginRateLimitFilter extends OncePerRequestFilter {

	private static final String LOGIN_PATH_PREFIX = "/api/auth/login/";
	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final int MAX_TRACKED_CLIENTS = 10_000;

	private final int maxAttemptsPerWindow;
	private final HandlerExceptionResolver handlerExceptionResolver;
	private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

	public LoginRateLimitFilter(int maxAttemptsPerWindow, HandlerExceptionResolver handlerExceptionResolver) {
		this.maxAttemptsPerWindow = maxAttemptsPerWindow;
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(LOGIN_PATH_PREFIX);
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
	) throws ServletException, IOException {
		if (isExceeded(request.getRemoteAddr())) {
			// 필터에서 만든 응답도 ErrorResponse 형식이 되도록 MVC 예외 처리로 넘긴다.
			handlerExceptionResolver.resolveException(
				request, response, null, new ApiException(ErrorCode.TOO_MANY_REQUESTS));
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean isExceeded(String clientIp) {
		Instant now = Instant.now();
		evictExpiredIfCrowded(now);
		Attempt attempt = attempts.compute(clientIp,
			(key, existing) -> existing == null || existing.isExpired(now) ? new Attempt(now) : existing);
		return attempt.increment() > maxAttemptsPerWindow;
	}

	private void evictExpiredIfCrowded(Instant now) {
		if (attempts.size() > MAX_TRACKED_CLIENTS) {
			attempts.values().removeIf(attempt -> attempt.isExpired(now));
		}
	}

	private static final class Attempt {

		private final Instant windowStart;
		private final AtomicInteger count = new AtomicInteger();

		private Attempt(Instant windowStart) {
			this.windowStart = windowStart;
		}

		private boolean isExpired(Instant now) {
			return windowStart.plus(WINDOW).isBefore(now);
		}

		private int increment() {
			return count.incrementAndGet();
		}
	}
}
