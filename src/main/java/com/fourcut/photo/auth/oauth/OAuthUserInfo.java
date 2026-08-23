package com.fourcut.photo.auth.oauth;

public record OAuthUserInfo(
	OAuthProvider provider,
	String providerId,
	String email,
	String nickname,
	String profileImageUrl
) {
}
