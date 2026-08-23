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
}
