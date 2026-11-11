package com.fourcut.photo.auth.oauth;

/**
 * 탈퇴할 때 제공자에게 연결 해제를 요청하는 데 필요한 값.
 *
 * 카카오는 providerId 하나로 끊을 수 있지만 애플은 그렇지 않다. 로그인할 때 받아 둔
 * 리프레시 토큰을 넘겨야 해서, 제공자마다 다른 재료를 한 자리에 모아 전달한다.
 *
 * @param providerId 제공자가 준 안정적인 사용자 식별자
 * @param refreshToken 제공자 쪽 리프레시 토큰. 애플만 쓰고 없을 수 있다
 */
public record OAuthUnlinkCommand(String providerId, String refreshToken) {
}
