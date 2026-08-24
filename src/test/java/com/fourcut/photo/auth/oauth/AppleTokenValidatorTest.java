package com.fourcut.photo.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

class AppleTokenValidatorTest {

	// iOS 네이티브 앱은 번들 ID 가, Android/웹은 Apple Developer 에 등록한 Services ID 가 aud 로 온다.
	private static final String IOS_BUNDLE_ID = "com.fourcut.photo";
	private static final String ANDROID_SERVICES_ID = "com.fourcut.photo.web";

	private final OAuth2TokenValidator<Jwt> validator =
		OAuthConfig.appleTokenValidator(List.of(IOS_BUNDLE_ID, ANDROID_SERVICES_ID));

	private static Jwt jwt(Object issuer, String audience) {
		return Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.header("typ", "JWT")
			.claim(JwtClaimNames.ISS, issuer)
			.claim(JwtClaimNames.AUD, List.of(audience))
			.claim(JwtClaimNames.SUB, "001234.abcd1234efgh5678.5678")
			.issuedAt(Instant.now().minusSeconds(10))
			.expiresAt(Instant.now().plusSeconds(300))
			.build();
	}

	@Test
	void 우리_앱을_대상으로_발급된_토큰은_통과한다() {
		Jwt token = jwt("https://appleid.apple.com", IOS_BUNDLE_ID);

		assertThat(validator.validate(token).hasErrors()).isFalse();
	}

	@Test
	void 등록된_안드로이드_서비스_아이디도_허용한다() {
		Jwt token = jwt("https://appleid.apple.com", ANDROID_SERVICES_ID);

		assertThat(validator.validate(token).hasErrors()).isFalse();
	}

	@Test
	void 우리가_모르는_발급자는_거부한다() {
		Jwt token = jwt("https://evil.example", IOS_BUNDLE_ID);

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	@Test
	void 다른_앱을_대상으로_발급된_토큰은_거부한다() {
		Jwt token = jwt("https://appleid.apple.com", "someone.elses.app");

		assertThat(validator.validate(token).hasErrors()).isTrue();
	}

	// 애플 계정 준비 전(client-ids 미설정) 에는 AppleOAuthClient 가 decode 이전에 먼저 걸러내지만,
	// 검증기 자체도 빈 허용 목록이면 그 무엇도 통과시키지 않아야 한다 — 이중 방어.
	@Test
	void 허용_목록이_비어있으면_아무_토큰도_통과하지_못한다() {
		OAuth2TokenValidator<Jwt> emptyAllowList = OAuthConfig.appleTokenValidator(List.of());
		Jwt token = jwt("https://appleid.apple.com", IOS_BUNDLE_ID);

		assertThat(emptyAllowList.validate(token).hasErrors()).isTrue();
	}
}
