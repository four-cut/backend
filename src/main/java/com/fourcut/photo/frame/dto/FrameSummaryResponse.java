package com.fourcut.photo.frame.dto;

import com.fourcut.photo.frame.FrameOrientation;
import com.fourcut.photo.frame.FrameTemplate;

public record FrameSummaryResponse(
	Long frameId,
	String name,
	FrameOrientation orientation,
	int requiredShotCount,
	int slotCount,
	String previewImageUrl
) {

	public static FrameSummaryResponse of(FrameTemplate frameTemplate, String previewImageUrl) {
		return new FrameSummaryResponse(
			frameTemplate.getId(),
			frameTemplate.getName(),
			frameTemplate.getOrientation(),
			frameTemplate.getRequiredShotCount(),
			frameTemplate.slotCount(),
			previewImageUrl
		);
	}
}
