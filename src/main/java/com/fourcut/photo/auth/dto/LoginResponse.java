package com.fourcut.photo.auth.dto;

import com.fourcut.photo.member.Member;
import com.fourcut.photo.member.dto.MemberResponse;

public record LoginResponse(
	String accessToken,
	String refreshToken,
	long expiresIn,
	boolean newMember,
	MemberResponse member
) {

	public static LoginResponse of(
		Member member, String accessToken, String refreshToken, long expiresIn, boolean newMember
	) {
		return new LoginResponse(accessToken, refreshToken, expiresIn, newMember, MemberResponse.from(member));
	}
}
