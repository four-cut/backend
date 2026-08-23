package com.fourcut.photo.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
