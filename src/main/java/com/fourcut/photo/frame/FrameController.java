package com.fourcut.photo.frame;

import com.fourcut.photo.frame.dto.FrameDetailResponse;
import com.fourcut.photo.frame.dto.FrameSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "프레임", description = "프레임 템플릿 조회 API")
@RestController
@RequestMapping("/api/frames")
public class FrameController {

	private final FrameService frameService;

	public FrameController(FrameService frameService) {
		this.frameService = frameService;
	}

	@Operation(summary = "프레임 목록 조회", description = "활성화된 프레임 템플릿 목록을 조회합니다. orientation으로 세로형/가로형 필터링 가능")
	@GetMapping
	public List<FrameSummaryResponse> getFrames(@RequestParam(required = false) FrameOrientation orientation) {
		return frameService.getActiveFrames(orientation);
	}

	@Operation(summary = "프레임 상세 조회", description = "슬롯 좌표를 포함한 프레임 상세 정보를 조회합니다")
	@GetMapping("/{frameId}")
	public FrameDetailResponse getFrameDetail(@PathVariable Long frameId) {
		return frameService.getFrameDetail(frameId);
	}
}
