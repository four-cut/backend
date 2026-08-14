package com.fourcut.photo.video;

import com.fourcut.photo.common.BaseTimeEntity;
import com.fourcut.photo.session.PhotoSession;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaptureVideo extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false, unique = true)
	private PhotoSession session;

	private String videoKey;
	private String qrCodeKey;
	private Integer durationSeconds;

	public CaptureVideo(PhotoSession session, String videoKey, String qrCodeKey, Integer durationSeconds) {
		this.session = session;
		this.videoKey = videoKey;
		this.qrCodeKey = qrCodeKey;
		this.durationSeconds = durationSeconds;
	}

	public void update(String videoKey, String qrCodeKey, Integer durationSeconds) {
		this.videoKey = videoKey;
		this.qrCodeKey = qrCodeKey;
		this.durationSeconds = durationSeconds;
	}
}
