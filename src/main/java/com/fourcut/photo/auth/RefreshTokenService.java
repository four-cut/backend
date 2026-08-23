package com.fourcut.photo.auth;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.member.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;
	private final Duration refreshTokenTtl;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenTtl = Duration.ofDays(jwtProperties.refreshTokenExpiryDays());
	}

	public String issue(Member member) {
		// 스케줄러 대신 발급할 때마다 해당 회원의 만료분만 정리한다. 유계이고 자기제한적이다.
		refreshTokenRepository.deleteByMemberIdAndExpiresAtBefore(member.getId(), LocalDateTime.now());
		String rawToken = generate();
		refreshTokenRepository.save(
			new RefreshToken(member, hash(rawToken), LocalDateTime.now().plus(refreshTokenTtl)));
		return rawToken;
	}

	public RotationResult rotate(String rawToken) {
		RefreshToken found = refreshTokenRepository.findByTokenHash(hash(rawToken))
			.orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

		// 이미 회전되어 폐기된 토큰이 다시 들어왔다 = 탈취 정황.
		// 무효화 작업이 이 예외로 롤백되면 안 되므로 호출자가 별도 트랜잭션에서 처리한다.
		if (found.isRevoked()) {
			throw new RefreshTokenReuseException(found.getMember().getId());
		}
		if (found.isExpired()) {
			throw new ApiException(ErrorCode.EXPIRED_REFRESH_TOKEN);
		}

		found.revoke();
		Member member = found.getMember();
		return new RotationResult(member, issue(member));
	}

	// 로그아웃은 폐기 표시가 아니라 삭제다. 폐기 표시를 남기면 로그아웃 직후 클라이언트가
	// 재발급을 한 번만 재시도해도 탈취로 오탐되어 다른 기기 세션까지 전부 끊긴다.
	// 재사용 탐지는 "회전으로 폐기된 토큰"에만 반응해야 한다.
	public void revoke(String rawToken) {
		refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(refreshTokenRepository::delete);
	}

	public void revokeAllByMember(Long memberId) {
		refreshTokenRepository.revokeAllByMemberId(memberId, LocalDateTime.now());
	}

	private String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	// 256비트 난수라 무차별 대입이 불가능하므로 적응형 해시가 필요 없고,
	// 솔트가 없어야 token_hash 로 바로 조회할 수 있다.
	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public record RotationResult(Member member, String refreshToken) {
	}
}
