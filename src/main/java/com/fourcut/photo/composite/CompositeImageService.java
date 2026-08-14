package com.fourcut.photo.composite;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.composite.dto.CompositeImageResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompositeImageService {

	private final PhotoSessionRepository photoSessionRepository;
	private final SlotAssignmentRepository slotAssignmentRepository;
	private final CompositeImageRepository compositeImageRepository;
	private final StorageService storageService;

	public CompositeImageService(
		PhotoSessionRepository photoSessionRepository,
		SlotAssignmentRepository slotAssignmentRepository,
		CompositeImageRepository compositeImageRepository,
		StorageService storageService
	) {
		this.photoSessionRepository = photoSessionRepository;
		this.slotAssignmentRepository = slotAssignmentRepository;
		this.compositeImageRepository = compositeImageRepository;
		this.storageService = storageService;
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
