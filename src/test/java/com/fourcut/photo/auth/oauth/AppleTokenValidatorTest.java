package com.fourcut.photo.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

class AppleTokenValidatorTest {

	// 애플의 aud 는 OAuth 클라이언트 ID 가 아니라 앱의 번들 ID 다.
	private static final String OUR_BUNDLE_ID = "com.fourcut.app";

	private final OAuth2TokenValidator<Jwt> validator =
		OAuthConfig.appleTokenValidator(List.of(OUR_BUNDLE_ID));

	private static Jwt jwt(Object issuer, String audience) {
		return Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.header("typ", "JWT")
			.claim(JwtClaimNames.ISS, issuer)
			.claim(JwtClaimNames.AUD, List.of(audience))
			.claim(JwtClaimNames.SUB, "001234.fedcba9876543210.1234")
			.issuedAt(Instant.now().minusSeconds(10))
			.expiresAt(Instant.now().plusSeconds(300))
			.build();
	}

	@Test
	void 애플이_발급한_토큰은_통과한다() {
		Jwt token = jwt("https://appleid.apple.com", OUR_BUNDLE_ID);

		assertThat(validator.validate(token).hasErrors()).isFalse();
	}

	// 구글에서 겪은 문제다. iss 가 URL 로 파싱되는 문자열이라 Spring 이 java.net.URL 객체로
	// 바꿔 넣을 수 있고, 검증기를 String 타입으로 선언하면 그때만 ClassCastException 이 난다.
	@Test
	void iss가_URL_객체로_들어와도_통과한다() throws Exception {
		Jwt token = jwt(URI.create("https://appleid.apple.com").toURL(), OUR_BUNDLE_ID);

		assertThat(validator.validate(token).hasErrors()).isFalse();
	}

	@Test
	void 우리가_모르는_발급자는_거부한다() {
		Jwt token = jwt("https://appleid.evil.example", OUR_BUNDLE_ID);

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	// aud 를 안 보면 다른 앱이 받은 애플 토큰으로 우리 계정에 들어올 수 있다.
	@Test
	void 다른_앱을_대상으로_발급된_토큰은_거부한다() {
		Jwt token = jwt("https://appleid.apple.com", "com.someone.else");

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void 만료된_토큰은_거부한다() {
		Jwt token = Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.claim(JwtClaimNames.ISS, "https://appleid.apple.com")
			.claim(JwtClaimNames.AUD, List.of(OUR_BUNDLE_ID))
			.claim(JwtClaimNames.SUB, "001234.fedcba9876543210.1234")
			.issuedAt(Instant.now().minusSeconds(600))
			.expiresAt(Instant.now().minusSeconds(300))
			.build();

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}
}
