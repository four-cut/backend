package com.fourcut.photo.video;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.download.DownloadService;
import com.fourcut.photo.download.dto.DownloadLinkResponse;
import com.fourcut.photo.session.PhotoSession;
import com.fourcut.photo.session.PhotoSessionRepository;
import com.fourcut.photo.storage.StorageService;
import com.fourcut.photo.video.dto.VideoUploadResponse;
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
	private final DownloadService downloadService;

	public VideoService(
		PhotoSessionRepository photoSessionRepository,
		CaptureVideoRepository captureVideoRepository,
		StorageService storageService,
		DownloadService downloadService
	) {
		this.photoSessionRepository = photoSessionRepository;
		this.captureVideoRepository = captureVideoRepository;
		this.storageService = storageService;
		this.downloadService = downloadService;
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

		// QR 은 영상 파일이 아니라 다운로드 페이지를 가리킨다. 사진도 같이 받아가야 하기 때문이다.
		DownloadLinkResponse link = downloadService.issue(session);
		String qrCodeKey = downloadService.qrCodeKey(sessionId);

		captureVideoRepository.findBySessionId(sessionId)
			.ifPresentOrElse(
				existing -> existing.update(videoKey, qrCodeKey, durationSeconds),
				() -> captureVideoRepository.save(new CaptureVideo(session, videoKey, qrCodeKey, durationSeconds))
			);

		return new VideoUploadResponse(storageService.getUrl(videoKey), link.qrCodeUrl(), link.downloadUrl());
	}

	@Transactional(readOnly = true)
	public Optional<VideoUploadResponse> getVideo(UUID sessionId) {
		return captureVideoRepository.findBySessionId(sessionId)
			.map(video -> {
				DownloadLinkResponse link = downloadService.find(sessionId).orElse(null);
				return new VideoUploadResponse(
					storageService.getUrl(video.getVideoKey()),
					link == null ? storageService.getUrl(video.getQrCodeKey()) : link.qrCodeUrl(),
					link == null ? null : link.downloadUrl()
				);
			});
	}
}
