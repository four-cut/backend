package com.fourcut.photo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(@NotBlank String token) {
}
