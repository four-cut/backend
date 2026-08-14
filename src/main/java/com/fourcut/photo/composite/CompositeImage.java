package com.fourcut.photo.composite;

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
public class CompositeImage extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false, unique = true)
	private PhotoSession session;

	private String imageKey;

	public CompositeImage(PhotoSession session, String imageKey) {
		this.session = session;
		this.imageKey = imageKey;
	}

	public void updateImageKey(String imageKey) {
		this.imageKey = imageKey;
	}
}
