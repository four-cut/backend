package com.fourcut.photo.session;

import com.fourcut.photo.composite.CompositeImageService;
import com.fourcut.photo.composite.dto.CompositeImageResponse;
import com.fourcut.photo.session.dto.ArrangementRequest;
import com.fourcut.photo.session.dto.CapturedPhotoResponse;
import com.fourcut.photo.session.dto.SessionCreateRequest;
import com.fourcut.photo.session.dto.SessionCreateResponse;
import com.fourcut.photo.session.dto.SessionStatusResponse;
import com.fourcut.photo.video.VideoService;
import com.fourcut.photo.video.dto.VideoUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "촬영 세션", description = "세션 생성부터 사진 업로드, 배치, 합성, 영상 업로드까지 촬영 흐름 전체 API")
@RestController
@RequestMapping("/api/sessions")
public class PhotoSessionController {

	private final PhotoSessionService photoSessionService;
	private final CompositeImageService compositeImageService;
	private final VideoService videoService;

	public PhotoSessionController(
		PhotoSessionService photoSessionService,
		CompositeImageService compositeImageService,
		VideoService videoService
	) {
		this.photoSessionService = photoSessionService;
		this.compositeImageService = compositeImageService;
		this.videoService = videoService;
	}

	@Operation(summary = "세션 생성", description = "프레임을 선택해 새 촬영 세션을 시작합니다")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SessionCreateResponse createSession(@Valid @RequestBody SessionCreateRequest request) {
		return photoSessionService.createSession(request.frameId());
	}

	@Operation(summary = "촬영 사진 업로드", description = "촬영본 1장을 순번(shotIndex)과 함께 업로드합니다")
	@PostMapping("/{sessionId}/photos")
	public ResponseEntity<Void> uploadPhoto(
		@PathVariable UUID sessionId,
		@RequestParam int shotIndex,
		@RequestPart MultipartFile file
	) {
		photoSessionService.uploadPhoto(sessionId, shotIndex, file);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "촬영 사진 목록 조회", description = "이 세션에서 촬영된 사진 목록을 조회합니다 (배치 화면에서 고르는 용도)")
	@GetMapping("/{sessionId}/photos")
	public List<CapturedPhotoResponse> listPhotos(@PathVariable UUID sessionId) {
		return photoSessionService.listPhotos(sessionId);
	}

	@Operation(summary = "사진 배치 저장", description = "고른 사진들을 프레임 슬롯 위치에 배치합니다")
	@PutMapping("/{sessionId}/arrangement")
	public ResponseEntity<Void> saveArrangement(@PathVariable UUID sessionId, @Valid @RequestBody ArrangementRequest request) {
		photoSessionService.saveArrangement(sessionId, request);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "콜라주 합성", description = "배치된 사진과 프레임을 합성해 완성된 이미지 1장을 생성합니다")
	@PostMapping("/{sessionId}/composite")
	public CompositeImageResponse composeSession(@PathVariable UUID sessionId) {
		return compositeImageService.compose(sessionId);
	}

	@Operation(summary = "촬영 영상 업로드", description = "촬영 과정 영상을 업로드하고 QR코드를 생성합니다")
	@PostMapping("/{sessionId}/video")
	public VideoUploadResponse uploadVideo(
		@PathVariable UUID sessionId,
		@RequestPart MultipartFile file,
		@RequestParam(required = false) Integer durationSeconds
	) {
		return videoService.uploadVideo(sessionId, file, durationSeconds);
	}

	@Operation(summary = "세션 상태 조회", description = "세션 상태와 합성 이미지/영상/QR코드 URL을 함께 조회합니다")
	@GetMapping("/{sessionId}")
	public SessionStatusResponse getSession(@PathVariable UUID sessionId) {
		SessionStatusResponse status = photoSessionService.getStatus(sessionId);
		String compositeImageUrl = compositeImageService.getImageUrl(sessionId).orElse(null);
		Optional<VideoUploadResponse> video = videoService.getVideo(sessionId);
		return new SessionStatusResponse(
			status.sessionId(), status.status(), status.frameId(),
			compositeImageUrl,
			video.map(VideoUploadResponse::videoUrl).orElse(null),
			video.map(VideoUploadResponse::qrCodeUrl).orElse(null)
		);
	}
}
