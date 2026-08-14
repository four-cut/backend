package com.fourcut.photo.frame.dto;

import com.fourcut.photo.frame.FrameOrientation;
import com.fourcut.photo.frame.FrameTemplate;
import java.util.List;

public record FrameDetailResponse(
	Long frameId,
	String name,
	FrameOrientation orientation,
	int canvasWidth,
	int canvasHeight,
	int requiredShotCount,
	String previewImageUrl,
	List<FrameSlotResponse> slots
) {

	public static FrameDetailResponse of(FrameTemplate frameTemplate, String previewImageUrl) {
		List<FrameSlotResponse> slots = frameTemplate.getSlots().stream()
			.map(FrameSlotResponse::from)
			.toList();
		return new FrameDetailResponse(
			frameTemplate.getId(),
			frameTemplate.getName(),
			frameTemplate.getOrientation(),
			frameTemplate.getCanvasWidth(),
			frameTemplate.getCanvasHeight(),
			frameTemplate.getRequiredShotCount(),
			previewImageUrl,
			slots
		);
	}
}
