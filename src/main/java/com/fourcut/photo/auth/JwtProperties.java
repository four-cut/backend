package com.fourcut.photo.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fourcut.jwt")
public record JwtProperties(String secret, long accessTokenExpiryMinutes, long refreshTokenExpiryDays) {
}
