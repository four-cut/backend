package com.fourcut.photo.frame;

import com.fourcut.photo.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FrameTemplate extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Enumerated(EnumType.STRING)
	private FrameOrientation orientation;

	private int canvasWidth;
	private int canvasHeight;
	private int requiredShotCount;
	private String frameAssetKey;
	private boolean active;

	@OneToMany(mappedBy = "frameTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("slotIndex asc")
	private List<FrameSlot> slots = new ArrayList<>();

	public FrameTemplate(String name, FrameOrientation orientation, int canvasWidth, int canvasHeight,
		int requiredShotCount, String frameAssetKey) {
		this.name = name;
		this.orientation = orientation;
		this.canvasWidth = canvasWidth;
		this.canvasHeight = canvasHeight;
		this.requiredShotCount = requiredShotCount;
		this.frameAssetKey = frameAssetKey;
		this.active = true;
	}

	public void addSlot(FrameSlot slot) {
		slots.add(slot);
		slot.assignTo(this);
	}

	public int slotCount() {
		return slots.size();
	}
}
