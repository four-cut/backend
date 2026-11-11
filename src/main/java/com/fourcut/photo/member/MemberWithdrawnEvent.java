package com.fourcut.photo.member;

import com.fourcut.photo.auth.oauth.OAuthProvider;

// 리스너가 커밋 이후에 도니, 그때 회원 행을 다시 읽지 않아도 되도록 필요한 값을 실어 보낸다.
public record MemberWithdrawnEvent(OAuthProvider provider, String providerId, String providerRefreshToken) {
}
