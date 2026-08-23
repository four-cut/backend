package com.fourcut.photo.auth;

import com.fourcut.photo.member.MemberRole;

public record MemberPrincipal(Long memberId, MemberRole role) {
}
