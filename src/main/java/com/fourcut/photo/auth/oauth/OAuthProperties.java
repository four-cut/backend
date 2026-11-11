package com.fourcut.photo.auth.oauth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fourcut.oauth")
public record OAuthProperties(Kakao kakao, Google google, Apple apple) {

	// appId 는 카카오 개발자 콘솔 > 앱 설정 > 요약 정보의 숫자 앱 ID다. REST API 키가 아니다.
	// adminKey 는 탈퇴 시 연결 해제(unlink)에만 쓴다.
	public record Kakao(Long appId, String adminKey) {
	}

	// RN google-signin 이 넘기는 id_token 의 aud. 안드로이드/iOS 에서 값이 달라질 수 있어 목록으로 받는다.
	public record Google(List<String> clientIds) {
	}

	/**
	 * 애플 설정.
	 *
	 * clientIds 는 identityToken 의 aud 로 허용할 값이다. OAuth 클라이언트 ID 가 아니라 앱의
	 * 번들 ID(com.fourcut.app)이고, 웹이나 안드로이드를 붙이면 Services ID 가 늘어나므로 목록이다.
	 *
	 * 나머지 셋은 탈퇴 시 연결 해제에만 쓴다. 개발자 콘솔 Keys 에서 Sign in with Apple 키를
	 * 만들면 .p8 파일과 Key ID 가 나오고, Team ID 는 멤버십 정보에 있다. 셋 중 하나라도 비면
	 * 로그인은 되지만 탈퇴할 때 애플 쪽 연결이 남는다.
	 */
	public record Apple(List<String> clientIds, String teamId, String keyId, String privateKey) {

		public boolean canRevoke() {
			return notBlank(teamId) && notBlank(keyId) && notBlank(privateKey);
		}

		private static boolean notBlank(String value) {
			return value != null && !value.isBlank();
		}
	}
}
