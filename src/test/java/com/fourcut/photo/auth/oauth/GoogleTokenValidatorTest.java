package com.fourcut.photo.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

class GoogleTokenValidatorTest {

	private static final String OUR_CLIENT_ID = "our-web-client.apps.googleusercontent.com";

	private final OAuth2TokenValidator<Jwt> validator =
		OAuthConfig.googleTokenValidator(List.of(OUR_CLIENT_ID, "our-ios-client.apps.googleusercontent.com"));

	private static Jwt jwt(Object issuer, String audience) {
		return Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.header("typ", "JWT")
			.claim(JwtClaimNames.ISS, issuer)
			.claim(JwtClaimNames.AUD, List.of(audience))
			.claim(JwtClaimNames.SUB, "1234567890")
			.issuedAt(Instant.now().minusSeconds(10))
			.expiresAt(Instant.now().plusSeconds(300))
			.build();
	}

	// 구글은 iss 를 두 형태로 발급하고, Spring 은 URL 로 파싱되는 쪽을 java.net.URL 객체로 변환한다.
	// 검증기를 String 타입으로 선언하면 URL 형태에서만 ClassCastException 이 나는 간헐 버그가 된다.
	@Test
	void iss가_URL_객체로_들어와도_통과한다() throws Exception {
		Jwt token = jwt(URI.create("https://accounts.google.com").toURL(), OUR_CLIENT_ID);

		assertThat(validator.validate(token).hasErrors()).isFalse();
	}

	@Test
	void iss가_문자열로_들어와도_통과한다() {
		Jwt token = jwt("accounts.google.com", OUR_CLIENT_ID);

		assertThat(validator.validate(token).hasErrors()).isFalse();
	}

	@Test
	void 우리가_모르는_발급자는_거부한다() {
		Jwt token = jwt("https://accounts.evil.example", OUR_CLIENT_ID);

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	// aud 불일치가 이 통합에서 가장 흔한 실패 원인이다.
	@Test
	void 다른_앱을_대상으로_발급된_토큰은_거부한다() {
		Jwt token = jwt("https://accounts.google.com", "someone-elses-client.apps.googleusercontent.com");

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void 등록된_아이오에스_클라이언트_아이디도_허용한다() {
		Jwt token = jwt("https://accounts.google.com", "our-ios-client.apps.googleusercontent.com");

		assertThat(validator.validate(token).hasErrors()).isFalse();
	}
}
