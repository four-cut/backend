package com.fourcut.photo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
	@NotBlank String token,
	// 애플처럼 신원 토큰에 이름이 없는 제공자를 위한 선택 필드. 애플은 최초 인가 시에만
	// 클라이언트가 이 값을 실어 보낼 수 있다(그 뒤로는 다시 주어지지 않는다). 카카오/구글은
	// 자체 프로필에서 닉네임을 가져오므로 이 값을 무시한다.
	String nickname
) {
}
