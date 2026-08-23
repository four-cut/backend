package com.fourcut.photo.member;

import com.fourcut.photo.auth.RefreshTokenService;
import com.fourcut.photo.auth.oauth.OAuthUserInfo;
import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.member.dto.MemberResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

	private static final Logger log = LoggerFactory.getLogger(MemberService.class);

	private final MemberRepository memberRepository;
	private final RefreshTokenService refreshTokenService;
	private final ApplicationEventPublisher eventPublisher;

	public MemberService(
		MemberRepository memberRepository,
		RefreshTokenService refreshTokenService,
		ApplicationEventPublisher eventPublisher
	) {
		this.memberRepository = memberRepository;
		this.refreshTokenService = refreshTokenService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public LoginMember findOrCreate(OAuthUserInfo userInfo) {
		return memberRepository.findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
			.map(member -> reactivateOrUpdate(member, userInfo))
			.orElseGet(() -> new LoginMember(memberRepository.save(toMember(userInfo)), true));
	}

	public MemberResponse getMe(Long memberId) {
		return MemberResponse.from(getMemberOrThrow(memberId));
	}

	@Transactional
	public void withdraw(Long memberId) {
		Member member = getMemberOrThrow(memberId);
		member.withdraw();
		refreshTokenService.revokeAllByMember(memberId);
		eventPublisher.publishEvent(new MemberWithdrawnEvent(member.getProvider(), member.getProviderId()));
		log.info("회원 탈퇴. memberId={} provider={}", memberId, member.getProvider());
	}

	Member getMemberOrThrow(Long memberId) {
		return memberRepository.findByIdAndDeletedAtIsNull(memberId)
			.orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
	}

	// (provider, provider_id) 유니크 제약 때문에 탈퇴한 계정이 다시 로그인하면
	// 새로 넣을 수 없고 기존 행을 되살려야 한다.
	// TODO 개인정보 파기 요건이 생기면 탈퇴 시 프로필을 비우고 provider_id 를 치환하는 방식으로 바꾼다.
	private LoginMember reactivateOrUpdate(Member member, OAuthUserInfo userInfo) {
		boolean reactivated = member.isDeleted();
		if (reactivated) {
			member.restore();
		}
		member.updateProfile(userInfo.email(), userInfo.nickname(), userInfo.profileImageUrl());
		return new LoginMember(member, reactivated);
	}

	private Member toMember(OAuthUserInfo userInfo) {
		return new Member(
			userInfo.provider(),
			userInfo.providerId(),
			userInfo.email(),
			userInfo.nickname(),
			userInfo.profileImageUrl());
	}

	// newMember 는 "이번에 가입했거나, 탈퇴 후 다시 가입했다"는 뜻이다. 클라이언트가 온보딩을
	// 보여줄지 판단하는 용도라 재가입도 신규로 취급한다.
	public record LoginMember(Member member, boolean newMember) {
	}
}
