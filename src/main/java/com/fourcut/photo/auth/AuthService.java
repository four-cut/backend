package com.fourcut.photo.auth;

import com.fourcut.photo.auth.dto.LoginResponse;
import com.fourcut.photo.auth.dto.TokenResponse;
import com.fourcut.photo.auth.oauth.OAuthClientRegistry;
import com.fourcut.photo.auth.oauth.OAuthProvider;
import com.fourcut.photo.auth.oauth.OAuthUserInfo;
import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.member.Member;
import com.fourcut.photo.member.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// 다른 서비스와 달리 클래스 레벨 @Transactional 을 일부러 붙이지 않았다. 로그인 흐름에는
// 카카오/구글로 가는 블로킹 HTTP 호출이 있어서, 트랜잭션 안에 두면 제공자가 느려질 때
// DB 커넥션 풀이 고갈되어 포토부스 API까지 함께 멈춘다. 트랜잭션은 호출 대상 서비스 안에만 둔다.
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final OAuthClientRegistry oAuthClientRegistry;
	private final MemberService memberService;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;

	public AuthService(
		OAuthClientRegistry oAuthClientRegistry,
		MemberService memberService,
		JwtTokenProvider jwtTokenProvider,
		RefreshTokenService refreshTokenService
	) {
		this.oAuthClientRegistry = oAuthClientRegistry;
		this.memberService = memberService;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenService = refreshTokenService;
	}

	public LoginResponse login(
		OAuthProvider provider,
		String credential,
		String fallbackNickname,
		String authorizationCode
	) {
		OAuthUserInfo userInfo = withFallbackNickname(verifyCredential(provider, credential), fallbackNickname);
		MemberService.LoginMember loginMember = findOrCreate(userInfo);
		Member member = loginMember.member();
		storeProviderRefreshToken(provider, member.getId(), authorizationCode);
		log.info("소셜 로그인 성공. provider={} memberId={} newMember={}",
			provider, member.getId(), loginMember.newMember());
		return LoginResponse.of(
			member,
			jwtTokenProvider.createAccessToken(member.getId(), member.getRole()),
			refreshTokenService.issue(member),
			jwtTokenProvider.getAccessTokenExpiresInSeconds(),
			loginMember.newMember());
	}

	public TokenResponse reissue(String refreshToken) {
		RefreshTokenService.RotationResult result;
		try {
			result = refreshTokenService.rotate(refreshToken);
		} catch (RefreshTokenReuseException e) {
			log.warn("리프레시 토큰 재사용 감지. 해당 회원의 토큰을 모두 무효화합니다. memberId={}", e.getMemberId());
			// rotate 의 트랜잭션은 이 예외로 이미 롤백됐다. 무효화는 새 트랜잭션에서 해야 살아남는다.
			refreshTokenService.revokeAllByMember(e.getMemberId());
			throw e;
		}
		Member member = result.member();
		return new TokenResponse(
			jwtTokenProvider.createAccessToken(member.getId(), member.getRole()),
			result.refreshToken(),
			jwtTokenProvider.getAccessTokenExpiresInSeconds());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	/**
	 * 탈퇴할 때 제공자 연결을 끊는 데 쓸 토큰을 미리 받아 둔다.
	 *
	 * 애플만 해당한다. 다른 제공자는 exchangeRefreshToken 이 null 을 주고 아무 일도 하지 않는다.
	 *
	 * 어떤 실패도 로그인을 막지 않는다. 이건 나중을 위한 준비일 뿐인데 여기서 던지면 애플이
	 * 잠깐 느릴 때 로그인 자체가 안 된다. 실패하면 그 계정은 탈퇴 시 애플 쪽 연결이 남고,
	 * 사용자가 Apple ID 설정에서 직접 지울 수 있다.
	 */
	private void storeProviderRefreshToken(OAuthProvider provider, Long memberId, String authorizationCode) {
		try {
			String refreshToken = oAuthClientRegistry.resolve(provider).exchangeRefreshToken(authorizationCode);
			if (refreshToken != null) {
				memberService.updateProviderRefreshToken(memberId, refreshToken);
			}
		} catch (RuntimeException e) {
			log.warn("제공자 리프레시 토큰 저장 실패. provider={} memberId={}", provider, memberId, e);
		}
	}

	// 애플은 ID 토큰에 이름을 담지 않고 최초 1회 인증에서만 따로 내려준다. 클라이언트가 그때
	// 같이 보낸 이름을 쓴다. 제공자가 이름을 준 경우(카카오·구글)에는 그쪽이 늘 최신이므로
	// 건드리지 않는다.
	private OAuthUserInfo withFallbackNickname(OAuthUserInfo userInfo, String fallbackNickname) {
		if (userInfo.nickname() != null || fallbackNickname == null || fallbackNickname.isBlank()) {
			return userInfo;
		}
		return new OAuthUserInfo(
			userInfo.provider(),
			userInfo.providerId(),
			userInfo.email(),
			fallbackNickname.strip(),
			userInfo.profileImageUrl());
	}

	private OAuthUserInfo verifyCredential(OAuthProvider provider, String credential) {
		try {
			return oAuthClientRegistry.resolve(provider).fetch(credential);
		} catch (ApiException e) {
			log.warn("소셜 로그인 실패. provider={} code={}", provider, e.getErrorCode());
			throw e;
		}
	}

	// 로그인 버튼 더블탭처럼 첫 로그인이 동시에 들어오면 둘 다 조회에 실패하고 둘 다 INSERT 해서
	// 하나가 유니크 제약에 걸린다. 실패한 트랜잭션 안에서는 복구할 수 없어 새 트랜잭션으로 한 번 재시도한다.
	private MemberService.LoginMember findOrCreate(OAuthUserInfo userInfo) {
		try {
			return memberService.findOrCreate(userInfo);
		} catch (DataIntegrityViolationException e) {
			return memberService.findOrCreate(userInfo);
		}
	}
}
