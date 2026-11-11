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

	// 애플은 providerId 만으로 연결을 끊을 수 없어 탈퇴 시 이 코드로 얻은 리프레시 토큰이
	// 필요하다. revoke 호출에 쓸 .p8 키를 아직 발급받지 않아 지금은 받아만 둔다.
	// TODO 키가 준비되면 이 코드로 리프레시 토큰을 받아 저장하고 탈퇴 시 revoke 를 호출한다.
	@Schema(description = "애플 전용. 탈퇴 시 연결 해제에 쓴다")
	String authorizationCode,

	// 애플은 이름을 최초 1회 인증에서만, 그것도 ID 토큰이 아니라 따로 내려준다.
	// 이때 저장하지 않으면 다시는 받을 수 없어 그 계정의 닉네임이 영영 빈다.
	@Schema(description = "애플 전용. 최초 로그인에서만 값이 있다")
	String name
) {
}
