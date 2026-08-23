package com.fourcut.photo.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	private final JwtTokenProvider jwtTokenProvider;
	private final RestAuthenticationEntryPoint authenticationEntryPoint;
	private final RestAccessDeniedHandler accessDeniedHandler;
	private final HandlerExceptionResolver handlerExceptionResolver;
	private final int loginRateLimitPerMinute;

	public SecurityConfig(
		JwtTokenProvider jwtTokenProvider,
		RestAuthenticationEntryPoint authenticationEntryPoint,
		RestAccessDeniedHandler accessDeniedHandler,
		@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
		@Value("${fourcut.auth.login-rate-limit-per-minute}") int loginRateLimitPerMinute
	) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
		this.handlerExceptionResolver = handlerExceptionResolver;
		this.loginRateLimitPerMinute = loginRateLimitPerMinute;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.csrf(AbstractHttpConfigurer::disable)
			// React Native 의 네이티브 요청은 브라우저가 아니라 동일 출처 정책 대상이 아니고,
			// Swagger UI 는 동일 출처다. 웹 클라이언트가 생기면 그때 켠다.
			.cors(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/**").permitAll()
				// 기존 포토부스 API 는 로그인 없이 쓰던 그대로 열어 둔다.
				// 컨트롤러는 이 둘뿐이고, 합성/영상은 /api/sessions 하위 경로다.
				.requestMatchers("/api/frames/**").permitAll()
				.requestMatchers("/api/sessions/**").permitAll()
				.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
				.requestMatchers("/actuator/health", "/actuator/info").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.addFilterBefore(
				new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(
				new LoginRateLimitFilter(loginRateLimitPerMinute, handlerExceptionResolver),
				JwtAuthenticationFilter.class)
			.build();
	}
}
