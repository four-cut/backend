package com.fourcut.photo.frame;

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
public class FrameSlot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "frame_template_id", nullable = false)
	private FrameTemplate frameTemplate;

	private int slotIndex;
	private int x;
	private int y;
	private int width;
	private int height;

	public FrameSlot(int slotIndex, int x, int y, int width, int height) {
		this.slotIndex = slotIndex;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	void assignTo(FrameTemplate frameTemplate) {
		this.frameTemplate = frameTemplate;
	}
}
