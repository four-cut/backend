package com.fourcut.photo.session;

import com.fourcut.photo.common.BaseTimeEntity;
import com.fourcut.photo.frame.FrameTemplate;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoSession extends BaseTimeEntity {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "frame_template_id", nullable = false)
	private FrameTemplate frameTemplate;

	@Enumerated(EnumType.STRING)
	private PhotoSessionStatus status;

	private LocalDateTime expiresAt;

	public PhotoSession(FrameTemplate frameTemplate, int expiryMinutes) {
		this.id = UUID.randomUUID();
		this.frameTemplate = frameTemplate;
		this.status = PhotoSessionStatus.CREATED;
		this.expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}

	public void markCaptured() {
		this.status = PhotoSessionStatus.CAPTURED;
	}

	public void markArranged() {
		this.status = PhotoSessionStatus.ARRANGED;
	}

	public void markComposed() {
		this.status = PhotoSessionStatus.COMPOSED;
	}
}
