package com.fourcut.photo.download;

import com.fourcut.photo.common.BaseTimeEntity;
import com.fourcut.photo.session.PhotoSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * QR 로 들어오는 다운로드 페이지의 열쇠.
 *
 * 세션 UUID 를 그대로 QR 에 담지 않는 이유는, 그 값이 곧 촬영 API 의 경로 파라미터라서
 * QR 을 찍은 사람이 남의 세션에 사진을 올리거나 배치를 바꿀 수 있기 때문이다.
 *
 * expiresAt 은 세션 만료(30분)와 무관한 자체 수명이다. 촬영은 끝났어도
 * 손님이 QR 을 찍는 시점은 한참 뒤일 수 있다(OQ-15).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DownloadLink extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false, unique = true)
	private PhotoSession session;

	@Column(length = 64)
	private String token;

	private LocalDateTime expiresAt;

	public DownloadLink(PhotoSession session, String token, int expiryDays) {
		this.session = session;
		this.token = token;
		this.expiresAt = LocalDateTime.now().plusDays(expiryDays);
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}
}
