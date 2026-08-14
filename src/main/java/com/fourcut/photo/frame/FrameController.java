package com.fourcut.photo.frame;

import com.fourcut.photo.frame.dto.FrameDetailResponse;
import com.fourcut.photo.frame.dto.FrameSummaryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/frames")
public class FrameController {

	private final FrameService frameService;

	public FrameController(FrameService frameService) {
		this.frameService = frameService;
	}

	@GetMapping
	public List<FrameSummaryResponse> getFrames(@RequestParam(required = false) FrameOrientation orientation) {
		return frameService.getActiveFrames(orientation);
	}

	@GetMapping("/{frameId}")
	public FrameDetailResponse getFrameDetail(@PathVariable Long frameId) {
		return frameService.getFrameDetail(frameId);
	}
}
