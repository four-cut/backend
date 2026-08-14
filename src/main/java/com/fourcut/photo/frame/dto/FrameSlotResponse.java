package com.fourcut.photo.frame.dto;

import com.fourcut.photo.frame.FrameSlot;

public record FrameSlotResponse(int slotIndex, int x, int y, int width, int height) {

	public static FrameSlotResponse from(FrameSlot slot) {
		return new FrameSlotResponse(slot.getSlotIndex(), slot.getX(), slot.getY(), slot.getWidth(), slot.getHeight());
	}
}
