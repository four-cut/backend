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

// 구글과 같은 ID 토큰 검증 구조다. 다른 점은 애플이 토큰에 이름도 프로필 사진도 담지 않는다는 것이다.
// 이름은 최초 1회 인증에서만, 그것도 토큰이 아니라 별도 필드로 클라이언트에 내려간다. 그래서
// 닉네임은 여기서 채울 수 없고 AuthService 가 요청 본문의 name 으로 메운다.
@Component
public class AppleOAuthClient implements OAuthClient {

	private static final Logger log = LoggerFactory.getLogger(AppleOAuthClient.class);

	private final JwtDecoder appleIdTokenDecoder;

	public AppleOAuthClient(@Qualifier("appleIdTokenDecoder") JwtDecoder appleIdTokenDecoder) {
		this.appleIdTokenDecoder = appleIdTokenDecoder;
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

	// unlink 는 기본 구현(아무것도 하지 않음)을 그대로 둔다.
	//
	// 애플은 providerId 만으로 연결을 끊을 수 없다. 로그인 때 받은 authorizationCode 를
	// /auth/token 에서 리프레시 토큰으로 바꿔 두었다가, 탈퇴 시 그 토큰으로 /auth/revoke 를
	// 불러야 한다. 호출에는 .p8 키로 ES256 서명한 client secret 이 필요해서 애플 콘솔에서
	// 키를 먼저 발급받아야 한다.
	// TODO 키를 발급받으면 로그인 시 리프레시 토큰을 저장하고 여기서 revoke 를 호출한다.
}
