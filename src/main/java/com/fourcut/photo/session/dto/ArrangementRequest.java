package com.fourcut.photo.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ArrangementRequest(@NotEmpty @Valid List<SlotAssignmentItem> assignments) {

	public record SlotAssignmentItem(@NotNull Integer slotIndex, @NotNull Long capturedPhotoId) {
	}
}
