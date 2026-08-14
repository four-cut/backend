package com.fourcut.photo.session.dto;

import com.fourcut.photo.session.PhotoSession;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessionCreateResponse(
	UUID sessionId,
	Long frameId,
	int requiredShotCount,
	LocalDateTime expiresAt
) {

	public static SessionCreateResponse from(PhotoSession session) {
		return new SessionCreateResponse(
			session.getId(),
			session.getFrameTemplate().getId(),
			session.getFrameTemplate().getRequiredShotCount(),
			session.getExpiresAt()
		);
	}
}
