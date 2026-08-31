package com.fourcut.photo.download;

import com.fourcut.photo.download.dto.DownloadContentsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "다운로드", description = "QR 로 공유되는 사진·영상 다운로드")
@RestController
@RequestMapping("/api/downloads")
public class DownloadApiController {

	private final DownloadService downloadService;

	public DownloadApiController(DownloadService downloadService) {
		this.downloadService = downloadService;
	}

	@Operation(summary = "다운로드 대상 조회",
		description = "다운로드 토큰으로 사진·영상 주소를 조회합니다. 세션 만료와 무관하게 링크 자체 수명을 따릅니다")
	@GetMapping("/{token}")
	public DownloadContentsResponse getContents(@PathVariable String token) {
		return downloadService.getContents(token);
	}
}
