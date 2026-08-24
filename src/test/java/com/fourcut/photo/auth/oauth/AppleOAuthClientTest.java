package com.fourcut.photo.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;

// 실제 애플 JWKS 를 타지 않도록 JwtDecoder 를 목으로 대체한다. 서명 검증 자체는 이미
// AppleTokenValidatorTest 가 검증기 로직만 떼어 확인하고, 여기서는 AppleOAuthClient 가
// "설정 안 됨/토큰 무효/정상 매핑" 세 경로를 올바르게 나누는지만 본다.
@ExtendWith(MockitoExtension.class)
class AppleOAuthClientTest {

	@Mock
	private JwtDecoder appleIdTokenDecoder;

	private static Jwt jwt(String subject, String email) {
		Jwt.Builder builder = Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.claim(JwtClaimNames.ISS, "https://appleid.apple.com")
			.claim(JwtClaimNames.SUB, subject)
			.issuedAt(Instant.now().minusSeconds(10))
			.expiresAt(Instant.now().plusSeconds(300));
		if (email != null) {
			builder.claim("email", email);
		}
		return builder.build();
	}

	// client-ids 가 비어 있으면(Apple Developer 가입 전) decode 를 아예 시도하지 않는다.
	// 검증기까지 갔다면 어차피 항상 실패하므로, 미리 걸러 "설정 안 됨"과 "토큰 무효"를 구분한다.
	@Test
	void 설정되지_않았으면_토큰을_디코드하지_않고_바로_거부한다() {
		AppleOAuthClient client = new AppleOAuthClient(
			appleIdTokenDecoder, new OAuthProperties(null, null, new OAuthProperties.Apple(List.of())));

		assertThatThrownBy(() -> client.fetch("whatever-token"))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
		verify(appleIdTokenDecoder, never()).decode(anyString());
	}

	@Test
	void 설정되어_있으면_토큰_검증이_실패할_때_토큰_무효로_변환된다() {
		when(appleIdTokenDecoder.decode(anyString())).thenThrow(new BadJwtException("서명 불일치"));
		AppleOAuthClient client = clientWithConfiguredIds();

		assertThatThrownBy(() -> client.fetch("tampered-token"))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_OAUTH_TOKEN);
	}

	@Test
	void 유효한_토큰이면_providerId와_이메일을_읽고_닉네임은_항상_비운다() {
		when(appleIdTokenDecoder.decode(anyString()))
			.thenReturn(jwt("001234.abcd1234efgh5678.5678", "user@privaterelay.appleid.com"));
		AppleOAuthClient client = clientWithConfiguredIds();

		OAuthUserInfo userInfo = client.fetch("valid-token");

		assertThat(userInfo.provider()).isEqualTo(OAuthProvider.APPLE);
		assertThat(userInfo.providerId()).isEqualTo("001234.abcd1234efgh5678.5678");
		assertThat(userInfo.email()).isEqualTo("user@privaterelay.appleid.com");
		// 애플은 신원 토큰에 이름을 절대 담지 않는다 — AuthService 가 클라이언트 제공값으로 채운다.
		assertThat(userInfo.nickname()).isNull();
		assertThat(userInfo.profileImageUrl()).isNull();
	}

	@Test
	void 이메일_동의를_안_했으면_이메일도_비어서_돌아온다() {
		when(appleIdTokenDecoder.decode(anyString()))
			.thenReturn(jwt("001234.abcd1234efgh5678.5678", null));
		AppleOAuthClient client = clientWithConfiguredIds();

		OAuthUserInfo userInfo = client.fetch("valid-token");

		assertThat(userInfo.email()).isNull();
	}

	private AppleOAuthClient clientWithConfiguredIds() {
		return new AppleOAuthClient(
			appleIdTokenDecoder, new OAuthProperties(null, null, new OAuthProperties.Apple(List.of("com.fourcut.photo"))));
	}
}
