package com.fourcut.photo.session;

import com.fourcut.photo.frame.FrameSlot;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlotAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	private PhotoSession session;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "frame_slot_id", nullable = false)
	private FrameSlot frameSlot;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "captured_photo_id", nullable = false)
	private CapturedPhoto capturedPhoto;

	public SlotAssignment(PhotoSession session, FrameSlot frameSlot, CapturedPhoto capturedPhoto) {
		this.session = session;
		this.frameSlot = frameSlot;
		this.capturedPhoto = capturedPhoto;
	}
}
