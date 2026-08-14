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

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SessionCreateResponse createSession(@Valid @RequestBody SessionCreateRequest request) {
		return photoSessionService.createSession(request.frameId());
	}

	@PostMapping("/{sessionId}/photos")
	public ResponseEntity<Void> uploadPhoto(
		@PathVariable UUID sessionId,
		@RequestParam int shotIndex,
		@RequestPart MultipartFile file
	) {
		photoSessionService.uploadPhoto(sessionId, shotIndex, file);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{sessionId}/photos")
	public List<CapturedPhotoResponse> listPhotos(@PathVariable UUID sessionId) {
		return photoSessionService.listPhotos(sessionId);
	}

	@PutMapping("/{sessionId}/arrangement")
	public ResponseEntity<Void> saveArrangement(@PathVariable UUID sessionId, @Valid @RequestBody ArrangementRequest request) {
		photoSessionService.saveArrangement(sessionId, request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{sessionId}/composite")
	public CompositeImageResponse composeSession(@PathVariable UUID sessionId) {
		return compositeImageService.compose(sessionId);
	}

	@PostMapping("/{sessionId}/video")
	public VideoUploadResponse uploadVideo(
		@PathVariable UUID sessionId,
		@RequestPart MultipartFile file,
		@RequestParam(required = false) Integer durationSeconds
	) {
		return videoService.uploadVideo(sessionId, file, durationSeconds);
	}

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
