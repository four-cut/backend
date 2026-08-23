package com.fourcut.photo.member;

import com.fourcut.photo.auth.oauth.OAuthProvider;

public record MemberWithdrawnEvent(OAuthProvider provider, String providerId) {
}
