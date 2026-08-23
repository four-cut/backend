package com.fourcut.photo.auth.oauth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import java.util.Locale;

public enum OAuthProvider {

	KAKAO,
	GOOGLE,
	APPLE;

	public static OAuthProvider from(String value) {
		if (value == null || value.isBlank()) {
			throw new ApiException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
		}
		try {
			return OAuthProvider.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, "지원하지 않는 소셜 로그인입니다: " + value);
		}
	}
}
