package com.fourcut.photo.session.dto;

import com.fourcut.photo.session.PhotoSession;
import com.fourcut.photo.session.PhotoSessionStatus;
import java.util.UUID;

public record SessionStatusResponse(
	UUID sessionId,
	PhotoSessionStatus status,
	Long frameId,
	String compositeImageUrl,
	String videoUrl,
	String qrCodeUrl
) {

	public static SessionStatusResponse of(PhotoSession session, String compositeImageUrl, String videoUrl,
		String qrCodeUrl) {
		return new SessionStatusResponse(
			session.getId(),
			session.getStatus(),
			session.getFrameTemplate().getId(),
			compositeImageUrl,
			videoUrl,
			qrCodeUrl
		);
	}
}
