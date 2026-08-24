package com.fourcut.photo.auth.oauth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class AppleOAuthClient implements OAuthClient {

	private static final Logger log = LoggerFactory.getLogger(AppleOAuthClient.class);

	private final JwtDecoder appleIdTokenDecoder;
	private final OAuthProperties.Apple properties;

	public AppleOAuthClient(
		@Qualifier("appleIdTokenDecoder") JwtDecoder appleIdTokenDecoder, OAuthProperties properties
	) {
		this.appleIdTokenDecoder = appleIdTokenDecoder;
		this.properties = properties.apple();
	}

	@Override
	public OAuthProvider provider() {
		return OAuthProvider.APPLE;
	}

	@Override
	public OAuthUserInfo fetch(String identityToken) {
		// Apple Developer Program 가입 전에는 client-ids 가 비어 있다. 이 경우 어떤 토큰을 줘도
		// 검증기를 통과할 수 없으므로, decode 를 시도하기 전에 "설정 안 됨"으로 먼저 끊어서
		// "토큰이 무효함"과 "기능이 아직 준비 안 됨"을 구분해 응답한다.
		if (!properties.isConfigured()) {
			throw new ApiException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, "애플 로그인이 아직 설정되지 않았습니다.");
		}

		Jwt jwt;
		try {
			jwt = appleIdTokenDecoder.decode(identityToken);
		} catch (JwtException e) {
			log.debug("애플 ID 토큰 검증 실패: {}", e.getMessage());
			throw new ApiException(ErrorCode.INVALID_OAUTH_TOKEN);
		}

		// 애플은 이름을 신원 토큰에 절대 담지 않는다. 최초 인가 시 딱 한 번, 네이티브 SDK 응답의
		// fullName 필드로만 내려주고 이후 로그인부터는 다시 주지 않는다. 그래서 nickname 은 항상
		// null 로 반환하고, 클라이언트가 최초 로그인 요청에 별도로 실어 보낸 값을 AuthService 가
		// 채운다 (SocialLoginRequest.nickname 참고). email 은 사용자가 가림 이메일을 선택했으면
		// @privaterelay.appleid.com 형태로 온다 — 그대로 저장해도 무방하다.
		return new OAuthUserInfo(
			OAuthProvider.APPLE,
			jwt.getSubject(),
			jwt.getClaimAsString("email"),
			null,
			null);
	}

	// 애플 연결 해제(revoke)는 Team ID/Key ID/.p8 개인키로 서명한 client_secret JWT 가 필요해
	// 카카오 unlink 와 달리 별도 인프라가 필요하다. Apple Developer 계정 준비 후 구현.
}
