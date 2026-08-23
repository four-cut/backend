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
public class GoogleOAuthClient implements OAuthClient {

	private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

	private final JwtDecoder googleIdTokenDecoder;

	public GoogleOAuthClient(@Qualifier("googleIdTokenDecoder") JwtDecoder googleIdTokenDecoder) {
		this.googleIdTokenDecoder = googleIdTokenDecoder;
	}

	@Override
	public OAuthProvider provider() {
		return OAuthProvider.GOOGLE;
	}

	@Override
	public OAuthUserInfo fetch(String idToken) {
		Jwt jwt;
		try {
			jwt = googleIdTokenDecoder.decode(idToken);
		} catch (JwtException e) {
			// aud 불일치가 이 통합의 가장 흔한 실패 원인이라 실제 값을 남긴다.
			log.debug("구글 ID 토큰 검증 실패: {}", e.getMessage());
			throw new ApiException(ErrorCode.INVALID_OAUTH_TOKEN);
		}
		// sub 는 안정적인 식별자다. email 은 사용자가 바꿀 수 있으므로 키로 쓰지 않는다.
		return new OAuthUserInfo(
			OAuthProvider.GOOGLE,
			jwt.getSubject(),
			jwt.getClaimAsString("email"),
			jwt.getClaimAsString("name"),
			jwt.getClaimAsString("picture"));
	}
}
