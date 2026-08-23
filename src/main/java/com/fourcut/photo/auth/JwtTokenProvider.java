package com.fourcut.photo.auth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.member.MemberRole;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

	private static final int MIN_SECRET_LENGTH = 32;
	private static final String ROLE_CLAIM = "role";

	private final NimbusJwtEncoder encoder;
	private final NimbusJwtDecoder decoder;
	private final Duration accessTokenTtl;

	public JwtTokenProvider(JwtProperties properties) {
		String secret = properties.secret();
		if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException(
				"fourcut.jwt.secret 은 최소 " + MIN_SECRET_LENGTH + "자여야 합니다 (HS256 은 256비트 키 필요)");
		}
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
		this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		// 만료 판정은 parse 에서 직접 한다. 클라이언트가 "만료(조용히 재발급)"와
		// "무효(로그인 화면으로)"를 구분해야 하기 때문이다. 서명 검증은 이 앞 단계에서 이미 끝난다.
		this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>());
		this.accessTokenTtl = Duration.ofMinutes(properties.accessTokenExpiryMinutes());
	}

	public String createAccessToken(Long memberId, MemberRole role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(String.valueOf(memberId))
			.issuedAt(now)
			.expiresAt(now.plus(accessTokenTtl))
			.claim(ROLE_CLAIM, role.name())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public MemberPrincipal parse(String token) {
		Jwt jwt;
		try {
			jwt = decoder.decode(token);
		} catch (JwtException e) {
			throw new ApiException(ErrorCode.INVALID_ACCESS_TOKEN);
		}
		Instant expiresAt = jwt.getExpiresAt();
		if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
			throw new ApiException(ErrorCode.EXPIRED_ACCESS_TOKEN);
		}
		try {
			return new MemberPrincipal(
				Long.valueOf(jwt.getSubject()),
				MemberRole.valueOf(jwt.getClaimAsString(ROLE_CLAIM)));
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new ApiException(ErrorCode.INVALID_ACCESS_TOKEN);
		}
	}

	public long getAccessTokenExpiresInSeconds() {
		return accessTokenTtl.toSeconds();
	}
}
