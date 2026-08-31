package com.fourcut.photo.composite;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.composite.dto.CompositeImageResponse;
import com.fourcut.photo.composite.dto.CompositeUploadResponse;
import com.fourcut.photo.download.DownloadService;
import com.fourcut.photo.download.dto.DownloadLinkResponse;
import com.fourcut.photo.frame.FrameSlot;
import com.fourcut.photo.frame.FrameTemplate;
import com.fourcut.photo.session.PhotoSession;
import com.fourcut.photo.session.PhotoSessionRepository;
import com.fourcut.photo.session.PhotoSessionStatus;
import com.fourcut.photo.session.SlotAssignment;
import com.fourcut.photo.session.SlotAssignmentRepository;
import com.fourcut.photo.storage.StorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class CompositeImageService {

	private final PhotoSessionRepository photoSessionRepository;
	private final SlotAssignmentRepository slotAssignmentRepository;
	private final CompositeImageRepository compositeImageRepository;
	private final StorageService storageService;
	private final DownloadService downloadService;

	public CompositeImageService(
		PhotoSessionRepository photoSessionRepository,
		SlotAssignmentRepository slotAssignmentRepository,
		CompositeImageRepository compositeImageRepository,
		StorageService storageService,
		DownloadService downloadService
	) {
		this.photoSessionRepository = photoSessionRepository;
		this.slotAssignmentRepository = slotAssignmentRepository;
		this.compositeImageRepository = compositeImageRepository;
		this.storageService = storageService;
		this.downloadService = downloadService;
	}

	/**
	 * 앱이 만든 최종 스트립을 받아 세션의 합성물로 저장한다.
	 *
	 * 서버 합성(compose)과 저장 자리를 공유해서, 다운로드 페이지는 어느 쪽으로 만들어졌든
	 * "이 세션의 합성물" 하나만 보면 된다. 앱은 로고까지 얹은 결과를 갖고 있고 서버는 그걸
	 * 다시 만들 수 없으므로, 배치나 촬영본 없이도 올릴 수 있어야 한다.
	 */
	public CompositeUploadResponse uploadComposite(UUID sessionId, MultipartFile file) {
		PhotoSession session = photoSessionRepository.findById(sessionId)
			.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
		if (session.isExpired()) {
			throw new ApiException(ErrorCode.SESSION_EXPIRED);
		}
		if (file.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_FILE);
		}

		String key = "composites/%s/final.%s".formatted(sessionId, resolveExtension(file.getContentType()));
		try {
			storageService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		compositeImageRepository.findBySessionId(sessionId)
			.ifPresentOrElse(
				existing -> existing.updateImageKey(key),
				() -> compositeImageRepository.save(new CompositeImage(session, key))
			);
		session.markComposed();

		// 영상보다 스트립이 먼저 올라올 수도 있어서 여기서도 링크를 보장한다.
		DownloadLinkResponse link = downloadService.issue(session);
		return new CompositeUploadResponse(storageService.getUrl(key), link.qrCodeUrl(), link.downloadUrl());
	}

	private String resolveExtension(String contentType) {
		if ("image/jpeg".equals(contentType)) {
			return "jpg";
		}
		if ("image/png".equals(contentType)) {
			return "png";
		}
		throw new ApiException(ErrorCode.INVALID_FILE, "jpg 또는 png 이미지만 올릴 수 있습니다.");
	}

	public CompositeImageResponse compose(UUID sessionId) {
		PhotoSession session = photoSessionRepository.findById(sessionId)
			.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
		if (session.isExpired()) {
			throw new ApiException(ErrorCode.SESSION_EXPIRED);
		}
		if (session.getStatus() != PhotoSessionStatus.ARRANGED && session.getStatus() != PhotoSessionStatus.COMPOSED) {
			throw new ApiException(ErrorCode.ARRANGEMENT_NOT_READY);
		}

		List<SlotAssignment> assignments = slotAssignmentRepository.findBySessionIdOrderByFrameSlot_SlotIndexAsc(sessionId);
		if (assignments.isEmpty()) {
			throw new ApiException(ErrorCode.ARRANGEMENT_NOT_READY);
		}

		FrameTemplate frameTemplate = session.getFrameTemplate();
		byte[] jpegBytes = render(frameTemplate, assignments);

		String key = "composites/%s/final.jpg".formatted(sessionId);
		storageService.upload(key, new ByteArrayInputStream(jpegBytes), jpegBytes.length, "image/jpeg");

		compositeImageRepository.findBySessionId(sessionId)
			.ifPresentOrElse(
				existing -> existing.updateImageKey(key),
				() -> compositeImageRepository.save(new CompositeImage(session, key))
			);
		session.markComposed();

		return new CompositeImageResponse(storageService.getUrl(key));
	}

	@Transactional(readOnly = true)
	public Optional<String> getImageUrl(UUID sessionId) {
		return compositeImageRepository.findBySessionId(sessionId)
			.map(image -> storageService.getUrl(image.getImageKey()));
	}

	private byte[] render(FrameTemplate frameTemplate, List<SlotAssignment> assignments) {
		BufferedImage canvas = new BufferedImage(frameTemplate.getCanvasWidth(), frameTemplate.getCanvasHeight(),
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = canvas.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		try {
			for (SlotAssignment assignment : assignments) {
				FrameSlot slot = assignment.getFrameSlot();
				BufferedImage photo = readImage(assignment.getCapturedPhoto().getImageKey());
				Rectangle rect = new Rectangle(slot.getX(), slot.getY(), slot.getWidth(), slot.getHeight());
				ImageCompositor.drawCover(g2d, photo, rect);
			}
			BufferedImage overlay = readImage(frameTemplate.getFrameAssetKey());
			g2d.drawImage(overlay, 0, 0, frameTemplate.getCanvasWidth(), frameTemplate.getCanvasHeight(), null);
		} finally {
			g2d.dispose();
		}

		BufferedImage flattened = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D flatGraphics = flattened.createGraphics();
		try {
			flatGraphics.setColor(Color.WHITE);
			flatGraphics.fillRect(0, 0, flattened.getWidth(), flattened.getHeight());
			flatGraphics.drawImage(canvas, 0, 0, null);
		} finally {
			flatGraphics.dispose();
		}

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(flattened, "jpg", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new ApiException(ErrorCode.STORAGE_ERROR, e.getMessage());
		}
	}

	private BufferedImage readImage(String key) {
		try (InputStream in = storageService.download(key)) {
			BufferedImage image = ImageIO.read(in);
			if (image == null) {
				throw new ApiException(ErrorCode.INVALID_FILE, "이미지를 디코딩할 수 없습니다: " + key);
			}
			return image;
		} catch (IOException e) {
			throw new ApiException(ErrorCode.STORAGE_ERROR, e.getMessage());
		}
	}
}
