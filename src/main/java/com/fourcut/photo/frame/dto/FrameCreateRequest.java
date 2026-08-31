package com.fourcut.photo.frame.dto;

import com.fourcut.photo.frame.FrameOrientation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record FrameCreateRequest(
	@NotBlank String name,
	@NotNull FrameOrientation orientation,
	@Positive int canvasWidth,
	@Positive int canvasHeight,
	// 슬롯 수보다 적게 찍고 끝낼 수는 없으므로 슬롯 수 이상이어야 한다(서비스에서 검증).
	@Positive int requiredShotCount,
	@NotEmpty @Valid List<SlotItem> slots
) {

	public record SlotItem(
		@NotNull @PositiveOrZero Integer slotIndex,
		@NotNull @PositiveOrZero Integer x,
		@NotNull @PositiveOrZero Integer y,
		@NotNull @Positive Integer width,
		@NotNull @Positive Integer height
	) {
	}
}
