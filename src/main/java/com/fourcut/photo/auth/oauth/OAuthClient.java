package com.fourcut.photo.auth.oauth;

public interface OAuthClient {

	OAuthProvider provider();

	OAuthUserInfo fetch(String credential);

	// 탈퇴 시 제공자 쪽 연결 해제. 지원하지 않는 제공자는 아무것도 하지 않는다.
	default void unlink(OAuthUnlinkCommand command) {
	}

	/**
	 * 로그인 때 받은 인가 코드를 제공자 쪽 리프레시 토큰으로 바꾼다.
	 *
	 * 애플만 쓴다. 애플은 providerId 로 연결을 끊을 수 없어서, 탈퇴할 때 쓸 토큰을
	 * 로그인 시점에 미리 받아 둬야 한다. 다른 제공자는 null 을 돌려주고 저장하지 않는다.
	 */
	default String exchangeRefreshToken(String authorizationCode) {
		return null;
	}
}
