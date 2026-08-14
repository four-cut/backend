package com.fourcut.photo.session.dto;

import jakarta.validation.constraints.NotNull;

public record SessionCreateRequest(@NotNull Long frameId) {
}
