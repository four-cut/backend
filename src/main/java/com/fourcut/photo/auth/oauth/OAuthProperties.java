package com.fourcut.photo.auth.oauth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fourcut.oauth")
public record OAuthProperties(Kakao kakao, Google google) {

	// appId 는 카카오 개발자 콘솔 > 앱 설정 > 요약 정보의 숫자 앱 ID다. REST API 키가 아니다.
	// adminKey 는 탈퇴 시 연결 해제(unlink)에만 쓴다.
	public record Kakao(Long appId, String adminKey) {
	}

	// RN google-signin 이 넘기는 id_token 의 aud. 안드로이드/iOS 에서 값이 달라질 수 있어 목록으로 받는다.
	public record Google(List<String> clientIds) {
	}
}
