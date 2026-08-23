package com.fourcut.photo.auth;

import com.fourcut.photo.auth.dto.LoginResponse;
import com.fourcut.photo.auth.dto.RefreshTokenRequest;
import com.fourcut.photo.auth.dto.SocialLoginRequest;
import com.fourcut.photo.auth.dto.TokenResponse;
import com.fourcut.photo.auth.oauth.OAuthProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "소셜 로그인, 토큰 재발급, 로그아웃 API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	// provider 를 enum 파라미터로 받지 않는 이유: 스프링의 문자열-enum 변환은 대소문자를 구분해
	// /login/kakao 가 매칭되지 않고, 변환 실패 예외는 GlobalExceptionHandler 를 타지 않아
	// 응답이 ErrorResponse 형식을 벗어난다.
	@Operation(summary = "소셜 로그인",
		description = "provider 는 kakao 또는 google 입니다. token 은 카카오는 액세스 토큰, 구글은 ID 토큰입니다.")
	@PostMapping("/login/{provider}")
	public LoginResponse login(@PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
		return authService.login(OAuthProvider.from(provider), request.token());
	}

	@Operation(summary = "토큰 재발급",
		description = "리프레시 토큰으로 새 토큰 쌍을 발급합니다. 기존 리프레시 토큰은 폐기되며, 다시 사용하면 해당 회원의 모든 토큰이 무효화됩니다.")
	@PostMapping("/reissue")
	public TokenResponse reissue(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.reissue(request.refreshToken());
	}

	// 액세스 토큰을 요구하지 않는다. 요구하면 액세스 토큰이 만료된 뒤에는 로그아웃할 수 없다.
	@Operation(summary = "로그아웃", description = "전달한 리프레시 토큰을 무효화합니다. 이미 없는 토큰이어도 성공으로 응답합니다.")
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}
}
