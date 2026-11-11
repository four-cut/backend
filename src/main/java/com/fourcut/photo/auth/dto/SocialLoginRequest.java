package com.fourcut.photo.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청.
 *
 * authorizationCode 와 name 은 애플 전용이다. 카카오·구글은 보내지 않으며 null 로 둔다.
 */
public record SocialLoginRequest(
	@Schema(description = "카카오는 액세스 토큰, 구글과 애플은 ID 토큰")
	@NotBlank String token,

	// 애플은 providerId 만으로 연결을 끊을 수 없다. 로그인 때 이 코드를 리프레시 토큰으로
	// 바꿔 저장해 두었다가 탈퇴할 때 그 토큰으로 /auth/revoke 를 부른다.
	@Schema(description = "애플 전용. 탈퇴 시 연결 해제에 쓴다")
	String authorizationCode,

	// 애플은 이름을 최초 1회 인증에서만, 그것도 ID 토큰이 아니라 따로 내려준다.
	// 이때 저장하지 않으면 다시는 받을 수 없어 그 계정의 닉네임이 영영 빈다.
	@Schema(description = "애플 전용. 최초 로그인에서만 값이 있다")
	String name
) {
}
