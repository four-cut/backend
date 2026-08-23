package com.fourcut.photo.member.dto;

import com.fourcut.photo.auth.oauth.OAuthProvider;
import com.fourcut.photo.member.Member;

public record MemberResponse(
	Long memberId,
	OAuthProvider provider,
	String email,
	String nickname,
	String profileImageUrl
) {

	public static MemberResponse from(Member member) {
		return new MemberResponse(
			member.getId(),
			member.getProvider(),
			member.getEmail(),
			member.getNickname(),
			member.getProfileImageUrl()
		);
	}
}
