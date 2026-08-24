package com.fourcut.photo.auth.oauth;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthConfig {

	// 제공자 엔드포인트는 배포 설정이 아니라 벤더 상수이므로 여기 둔다.
	private static final String KAKAO_API_BASE_URL = "https://kapi.kakao.com";
	private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
	private static final Set<String> GOOGLE_ISSUERS =
		Set.of("https://accounts.google.com", "accounts.google.com");
	private static final String APPLE_JWK_SET_URI = "https://appleid.apple.com/auth/keys";
	private static final String APPLE_ISSUER = "https://appleid.apple.com";

	@Bean
	public RestClient kakaoRestClient(RestClient.Builder builder) {
		return builder.baseUrl(KAKAO_API_BASE_URL).build();
	}

	@Bean
	public JwtDecoder googleIdTokenDecoder(OAuthProperties properties) {
		List<String> allowedClientIds = properties.google().clientIds();
		Assert.notEmpty(allowedClientIds, "fourcut.oauth.google.client-ids 는 비어 있을 수 없습니다");

		// JWKS 는 최초 검증 시점에 가져와 캐시한다. 기동 시 네트워크를 타지 않는다.
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI)
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.build();
		decoder.setJwtValidator(googleTokenValidator(allowedClientIds));
		return decoder;
	}

	// 애플은 Developer Program 가입 전까지 client-ids 가 비어 있을 수 있다. 구글과 달리
	// Assert.notEmpty 로 기동을 막지 않는다 — 이미 카카오/구글로 운영 중인 서비스라, 애플 설정이
	// 안 됐다는 이유로 앱 전체가 기동 실패하면 안 된다. 비어 있을 때의 처리는 AppleOAuthClient 가 한다.
	@Bean
	public JwtDecoder appleIdTokenDecoder(OAuthProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(APPLE_JWK_SET_URI)
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.build();
		decoder.setJwtValidator(appleTokenValidator(properties.apple().clientIds()));
		return decoder;
	}

	// 테스트에서 직접 검증할 수 있도록 분리했다.
	static OAuth2TokenValidator<Jwt> googleTokenValidator(List<String> allowedClientIds) {
		// iss 는 반드시 Object 로 읽는다. Spring 이 URL 로 파싱되는 값을 java.net.URL 로 변환하기 때문에
		// JwtClaimValidator<String> 을 쓰면 구글이 주는 두 형태 중 하나에서 ClassCastException 이 난다.
		OAuth2TokenValidator<Jwt> issuerValidator = new JwtClaimValidator<Object>(
			JwtClaimNames.ISS, issuer -> issuer != null && GOOGLE_ISSUERS.contains(issuer.toString()));

		// aud 는 항상 List<String> 으로 정규화된다. 안드로이드/iOS 에서 값이 달라질 수 있어 목록과 대조한다.
		OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
			JwtClaimNames.AUD, audience -> audience != null && !Collections.disjoint(audience, allowedClientIds));

		// createDefaultWithValidators 가 exp/nbf 등 표준 검증기를 함께 묶어준다.
		return JwtValidators.createDefaultWithValidators(issuerValidator, audienceValidator);
	}

	// 애플의 iss 는 단일 형태(https://appleid.apple.com)만 온다. 구글처럼 URL/문자열 두 형태를
	// 대비할 필요는 없지만, MappedJwtClaimSetConverter 가 URL 로 파싱 가능한 값은 java.net.URL 로
	// 바꿔버리는 동작 자체는 동일하므로 안전하게 Object 로 읽는다.
	// allowedClientIds 가 비어 있으면 어떤 토큰도 통과하지 못한다 — AppleOAuthClient 가 이 상태를
	// decode 이전에 감지해 UNSUPPORTED_OAUTH_PROVIDER 로 먼저 끊어내므로, 이 검증기까지 오는
	// 요청은 항상 설정이 끝난 상태다.
	static OAuth2TokenValidator<Jwt> appleTokenValidator(List<String> allowedClientIds) {
		OAuth2TokenValidator<Jwt> issuerValidator = new JwtClaimValidator<Object>(
			JwtClaimNames.ISS, issuer -> issuer != null && APPLE_ISSUER.equals(issuer.toString()));

		OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
			JwtClaimNames.AUD, audience -> audience != null && !Collections.disjoint(audience, allowedClientIds));

		return JwtValidators.createDefaultWithValidators(issuerValidator, audienceValidator);
	}
}
