package com.fourcut.photo.auth.oauth;

public interface OAuthClient {

	OAuthProvider provider();

	OAuthUserInfo fetch(String credential);

	// 탈퇴 시 제공자 쪽 연결 해제. 지원하지 않는 제공자는 아무것도 하지 않는다.
	default void unlink(String providerId) {
	}
}
