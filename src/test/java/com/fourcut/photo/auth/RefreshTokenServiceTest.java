package com.fourcut.photo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fourcut.photo.auth.dto.TokenResponse;
import com.fourcut.photo.auth.oauth.OAuthProvider;
import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.member.Member;
import com.fourcut.photo.member.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 테스트에 @Transactional 을 붙이지 않는다. 재사용 탐지는 "예외로 롤백된 트랜잭션 밖에서
// 무효화가 살아남는지"가 핵심이라 실제 트랜잭션 경계가 필요하다.
@SpringBootTest
class RefreshTokenServiceTest {

	@Autowired
	private RefreshTokenService refreshTokenService;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AuthService authService;

	private Member member;

	@BeforeEach
	void setUp() {
		member = memberRepository.save(new Member(OAuthProvider.KAKAO, "987654321", null, "홍길동", null));
	}

	@AfterEach
	void tearDown() {
		refreshTokenRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	void 토큰_원문은_어디에도_저장되지_않는다() {
		String rawToken = refreshTokenService.issue(member);

		assertThat(refreshTokenRepository.findAll())
			.isNotEmpty()
			.noneMatch(saved -> saved.getTokenHash().contains(rawToken));
		assertThat(refreshTokenRepository.findByTokenHash(rawToken)).isEmpty();
	}

	@Test
	void 재발급하면_새_토큰이_나오고_옛_토큰은_폐기된다() {
		String first = refreshTokenService.issue(member);

		RefreshTokenService.RotationResult result = refreshTokenService.rotate(first);

		assertThat(result.refreshToken()).isNotEqualTo(first);
		assertThat(result.member().getId()).isEqualTo(member.getId());
		assertThat(refreshTokenRepository.findAll()).anyMatch(RefreshToken::isRevoked);
	}

	// 회전만 하고 재사용 탐지를 안 하면 회전의 의미가 없다.
	// 탈취된 토큰이 다시 쓰이는 순간 그 회원의 모든 세션이 끊겨야 한다.
	@Test
	void 폐기된_토큰이_다시_쓰이면_그_회원의_토큰이_전부_무효화된다() {
		String stolen = refreshTokenService.issue(member);
		TokenResponse rotated = authService.reissue(stolen);

		assertThatThrownBy(() -> authService.reissue(stolen))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);

		// 정상 사용자가 들고 있던 새 토큰까지 함께 끊겼는지 확인한다.
		assertThatThrownBy(() -> authService.reissue(rotated.refreshToken()))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
		assertThat(refreshTokenRepository.findAll()).allMatch(RefreshToken::isRevoked);
	}

	// 로그아웃한 토큰을 다시 내밀어도 탈취로 오탐하면 안 된다.
	@Test
	void 로그아웃한_토큰은_재사용_탐지가_아니라_단순_무효로_처리된다() {
		String rawToken = refreshTokenService.issue(member);
		String otherDevice = refreshTokenService.issue(member);

		refreshTokenService.revoke(rawToken);

		assertThatThrownBy(() -> authService.reissue(rawToken))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

		// 다른 기기 세션은 살아있어야 한다.
		assertThat(authService.reissue(otherDevice).accessToken()).isNotBlank();
	}

	@Test
	void 로그아웃은_같은_토큰으로_여러_번_불러도_실패하지_않는다() {
		String rawToken = refreshTokenService.issue(member);

		refreshTokenService.revoke(rawToken);
		refreshTokenService.revoke(rawToken);

		assertThat(refreshTokenRepository.findAll()).isEmpty();
	}

	@Test
	void 없는_토큰으로_재발급하면_무효로_거부된다() {
		assertThatThrownBy(() -> authService.reissue("does-not-exist"))
			.isInstanceOf(ApiException.class)
			.extracting(e -> ((ApiException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
	}
}
