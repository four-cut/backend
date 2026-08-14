package com.fourcut.photo.video;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.session.PhotoSession;
import com.fourcut.photo.session.PhotoSessionRepository;
import com.fourcut.photo.storage.StorageService;
import com.fourcut.photo.video.dto.VideoUploadResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class VideoService {

	private final PhotoSessionRepository photoSessionRepository;
	private final CaptureVideoRepository captureVideoRepository;
	private final StorageService storageService;
	private final QrCodeService qrCodeService;

	public VideoService(
		PhotoSessionRepository photoSessionRepository,
		CaptureVideoRepository captureVideoRepository,
		StorageService storageService,
		QrCodeService qrCodeService
	) {
		this.photoSessionRepository = photoSessionRepository;
		this.captureVideoRepository = captureVideoRepository;
		this.storageService = storageService;
		this.qrCodeService = qrCodeService;
	}

	public VideoUploadResponse uploadVideo(UUID sessionId, MultipartFile file, Integer durationSeconds) {
		PhotoSession session = photoSessionRepository.findById(sessionId)
			.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
		if (session.isExpired()) {
			throw new ApiException(ErrorCode.SESSION_EXPIRED);
		}
		if (file.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_FILE);
		}

		String videoKey = "videos/%s/capture.mp4".formatted(sessionId);
		try {
			storageService.upload(videoKey, file.getInputStream(), file.getSize(), file.getContentType());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		String videoUrl = storageService.getUrl(videoKey);
		String qrCodeKey = "qrcodes/%s.png".formatted(sessionId);
		byte[] qrPng = qrCodeService.generatePng(videoUrl);
		storageService.upload(qrCodeKey, new ByteArrayInputStream(qrPng), qrPng.length, "image/png");

		captureVideoRepository.findBySessionId(sessionId)
			.ifPresentOrElse(
				existing -> existing.update(videoKey, qrCodeKey, durationSeconds),
				() -> captureVideoRepository.save(new CaptureVideo(session, videoKey, qrCodeKey, durationSeconds))
			);

		return new VideoUploadResponse(videoUrl, storageService.getUrl(qrCodeKey));
	}

	@Transactional(readOnly = true)
	public Optional<VideoUploadResponse> getVideo(UUID sessionId) {
		return captureVideoRepository.findBySessionId(sessionId)
			.map(video -> new VideoUploadResponse(
				storageService.getUrl(video.getVideoKey()),
				storageService.getUrl(video.getQrCodeKey())
			));
	}
}
