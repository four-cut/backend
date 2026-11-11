package com.fourcut.photo.auth.oauth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

// 구글과 같은 ID 토큰 검증 구조다. 다른 점은 애플이 토큰에 이름도 프로필 사진도 담지 않는다는 것과,
// 연결 해제를 providerId 로 할 수 없다는 것이다. 이름은 AuthService 가 요청 본문의 name 으로 메우고,
// 연결 해제는 로그인 때 받아 둔 리프레시 토큰으로 한다.
@Component
public class AppleOAuthClient implements OAuthClient {

	private static final Logger log = LoggerFactory.getLogger(AppleOAuthClient.class);

	private static final String TOKEN_URI = "/auth/token";
	private static final String REVOKE_URI = "/auth/revoke";

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
		new ParameterizedTypeReference<>() {
		};

	private final JwtDecoder appleIdTokenDecoder;
	private final RestClient appleRestClient;
	private final OAuthProperties.Apple properties;
	private final AppleClientSecret clientSecret;

	public AppleOAuthClient(
		@Qualifier("appleIdTokenDecoder") JwtDecoder appleIdTokenDecoder,
		@Qualifier("appleRestClient") RestClient appleRestClient,
		OAuthProperties oAuthProperties
	) {
		this.appleIdTokenDecoder = appleIdTokenDecoder;
		this.appleRestClient = appleRestClient;
		this.properties = oAuthProperties.apple();
		// 키가 없으면 생성기를 두지 않는다. 로그인은 그대로 되고 연결 해제만 건너뛴다.
		this.clientSecret = properties.canRevoke()
			? new AppleClientSecret(properties.teamId(), properties.keyId(), properties.privateKey())
			: null;
		if (clientSecret == null) {
			log.warn("애플 키(.p8)가 설정되지 않았습니다. 로그인은 되지만 탈퇴해도 애플 쪽 연결이 남습니다.");
		}
	}

	@Override
	public OAuthProvider provider() {
		return OAuthProvider.APPLE;
	}

	@Override
	public OAuthUserInfo fetch(String identityToken) {
		Jwt jwt;
		try {
			jwt = appleIdTokenDecoder.decode(identityToken);
		} catch (JwtException e) {
			// aud 불일치가 이 통합의 가장 흔한 실패 원인이라 실제 값을 남긴다.
			log.debug("애플 ID 토큰 검증 실패: {}", e.getMessage());
			throw new ApiException(ErrorCode.INVALID_OAUTH_TOKEN);
		}
		// sub 는 안정적인 식별자다. email 은 사용자가 바꿀 수 있으므로 키로 쓰지 않는다.
		//
		// 애플의 email 은 사용자가 "이메일 가리기"를 고르면 @privaterelay.appleid.com 주소로
		// 온다. 그래도 실제 주소로 전달되는 유효한 주소라 그대로 저장한다 — 심사 지침 4.8 이
		// 요구하는 게 바로 이 선택지라, 가린 주소를 거부하면 지침을 어기는 셈이 된다.
		return new OAuthUserInfo(
			OAuthProvider.APPLE,
			jwt.getSubject(),
			jwt.getClaimAsString("email"),
			null,
			null);
	}

	/**
	 * 인가 코드를 리프레시 토큰으로 바꾼다.
	 *
	 * 실패해도 예외를 던지지 않는다. 이건 탈퇴를 대비한 준비 작업일 뿐이라, 여기서 막으면
	 * 애플이 잠깐 느릴 때 로그인 자체가 안 된다. 토큰을 못 받으면 그 계정은 탈퇴 시
	 * 애플 쪽 연결이 남고, 사용자가 설정에서 직접 지울 수 있다.
	 */
	@Override
	public String exchangeRefreshToken(String authorizationCode) {
		if (clientSecret == null || authorizationCode == null || authorizationCode.isBlank()) {
			return null;
		}
		String clientId = clientId();
		try {
			Map<String, Object> body = appleRestClient.post()
				.uri(TOKEN_URI)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form(Map.of(
					"client_id", clientId,
					"client_secret", clientSecret.generate(clientId),
					"code", authorizationCode,
					"grant_type", "authorization_code")))
				.retrieve()
				.body(JSON_OBJECT);
			Object refreshToken = body == null ? null : body.get("refresh_token");
			if (refreshToken == null) {
				log.warn("애플이 리프레시 토큰을 주지 않았습니다. 탈퇴 시 연결 해제를 할 수 없습니다.");
				return null;
			}
			return refreshToken.toString();
		} catch (RestClientException | IllegalStateException e) {
			log.warn("애플 인가 코드 교환 실패. 로그인은 그대로 진행합니다: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * 애플 쪽 연결을 끊는다.
	 *
	 * 성공하면 사용자의 Apple ID 설정에서 이 앱이 사라지고, 다시 로그인하면 이름과 이메일을
	 * 처음처럼 다시 받게 된다. 카카오와 같은 이유로 실패해도 탈퇴를 막지 않는다.
	 */
	@Override
	public void unlink(OAuthUnlinkCommand command) {
		String refreshToken = command.refreshToken();
		if (clientSecret == null) {
			log.warn("애플 키가 없어 연결 해제를 건너뜁니다. providerId={}", command.providerId());
			return;
		}
		if (refreshToken == null || refreshToken.isBlank()) {
			// 키를 설정하기 전에 가입한 계정이거나 교환이 실패했던 계정이다.
			log.warn("애플 리프레시 토큰이 없어 연결 해제를 건너뜁니다. providerId={}", command.providerId());
			return;
		}
		String clientId = clientId();
		try {
			appleRestClient.post()
				.uri(REVOKE_URI)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form(Map.of(
					"client_id", clientId,
					"client_secret", clientSecret.generate(clientId),
					"token", refreshToken,
					"token_type_hint", "refresh_token")))
				.retrieve()
				.toBodilessEntity();
		} catch (RestClientException | IllegalStateException e) {
			// 연결 해제 실패가 탈퇴 자체를 막으면 안 된다. 수동 조치를 위해 식별자를 남기고 진행한다.
			log.error("애플 연결 해제 실패. providerId={}", command.providerId(), e);
		}
	}

	/**
	 * 토큰 엔드포인트에 낼 client_id.
	 *
	 * 검증용 aud 는 여러 개를 허용하지만 코드 교환에는 그 코드가 발급된 클라이언트 하나가
	 * 필요하다. 지금은 iOS 앱뿐이라 목록의 첫 값이 곧 그 값이다. 웹용 Services ID 를
	 * 추가하면 어느 클라이언트의 코드인지 클라이언트가 함께 알려 줘야 한다.
	 */
	private String clientId() {
		return properties.clientIds().get(0);
	}

	private static String form(Map<String, String> fields) {
		StringBuilder encoded = new StringBuilder();
		fields.forEach((key, value) -> {
			if (!encoded.isEmpty()) {
				encoded.append('&');
			}
			encoded.append(key).append('=').append(UriUtils.encode(value, StandardCharsets.UTF_8));
		});
		return encoded.toString();
	}
}
