package com.fourcut.photo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.member.MemberRole;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private static final String SECRET = "test-secret-key-for-tests-only-do-not-use-in-production-0123456789";
	private static final String OTHER_SECRET = "completely-different-secret-key-that-is-also-long-enough-98765432";

	private final JwtTokenProvider provider = providerWith(SECRET, 60);

	private static JwtTokenProvider providerWith(String secret, long accessTokenExpiryMinutes) {
		return new JwtTokenProvider(new JwtProperties(secret, accessTokenExpiryMinutes, 30));
	}

	@Test
	void 발급한_토큰을_파싱하면_회원과_권한이_그대로_나온다() {
		String token = provider.createAccessToken(42L, MemberRole.USER);

		MemberPrincipal principal = provider.parse(token);

		assertThat(principal.memberId()).isEqualTo(42L);
		assertThat(principal.role()).isEqualTo(MemberRole.USER);
	}

	@Test
	void 만료된_토큰은_무효가_아니라_만료로_구분된다() throws Exception {
		String expired = expiredTokenSignedWith(SECRET);

		assertThatThrownBy(() -> provider.parse(expired))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.EXPIRED_ACCESS_TOKEN);
	}

	// 만료 토큰은 음수 TTL 로 만들 수 없어 직접 서명한다.
	private static String expiredTokenSignedWith(String secret) throws Exception {
		Instant past = Instant.now().minusSeconds(120);
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.subject("42")
			.claim("role", MemberRole.USER.name())
			.issueTime(Date.from(past.minusSeconds(60)))
			.expirationTime(Date.from(past))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		jwt.sign(new MACSigner(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		return jwt.serialize();
	}

	@Test
	void 다른_비밀키로_서명된_토큰은_거부된다() {
		String forged = providerWith(OTHER_SECRET, 60).createAccessToken(42L, MemberRole.USER);

		assertThatThrownBy(() -> provider.parse(forged))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
	}

	@Test
	void 한_글자라도_변조되면_거부된다() {
		String token = provider.createAccessToken(42L, MemberRole.USER);
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");

		assertThatThrownBy(() -> provider.parse(tampered))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
	}

	@Test
	void 형식이_아예_다른_문자열도_거부된다() {
		assertThatThrownBy(() -> provider.parse("not-a-jwt"))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_ACCESS_TOKEN);
	}

	// HS256 은 256비트 키가 필요하다. 짧은 비밀키는 기동 시점에 바로 걸러야 한다.
	@Test
	void 비밀키가_짧으면_생성_시점에_실패한다() {
		assertThatThrownBy(() -> providerWith("too-short", 60))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("fourcut.jwt.secret");
	}
}
