package com.fourcut.photo.session;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.frame.FrameSlot;
import com.fourcut.photo.frame.FrameTemplate;
import com.fourcut.photo.frame.FrameTemplateRepository;
import com.fourcut.photo.session.dto.ArrangementRequest;
import com.fourcut.photo.session.dto.CapturedPhotoResponse;
import com.fourcut.photo.session.dto.SessionCreateResponse;
import com.fourcut.photo.session.dto.SessionStatusResponse;
import com.fourcut.photo.storage.StorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class PhotoSessionService {

	private final PhotoSessionRepository photoSessionRepository;
	private final FrameTemplateRepository frameTemplateRepository;
	private final CapturedPhotoRepository capturedPhotoRepository;
	private final SlotAssignmentRepository slotAssignmentRepository;
	private final StorageService storageService;
	private final int expiryMinutes;

	public PhotoSessionService(
		PhotoSessionRepository photoSessionRepository,
		FrameTemplateRepository frameTemplateRepository,
		CapturedPhotoRepository capturedPhotoRepository,
		SlotAssignmentRepository slotAssignmentRepository,
		StorageService storageService,
		@Value("${fourcut.session.expiry-minutes}") int expiryMinutes
	) {
		this.photoSessionRepository = photoSessionRepository;
		this.frameTemplateRepository = frameTemplateRepository;
		this.capturedPhotoRepository = capturedPhotoRepository;
		this.slotAssignmentRepository = slotAssignmentRepository;
		this.storageService = storageService;
		this.expiryMinutes = expiryMinutes;
	}

	public SessionCreateResponse createSession(Long frameId) {
		FrameTemplate frameTemplate = frameTemplateRepository.findWithSlotsById(frameId)
			.orElseThrow(() -> new ApiException(ErrorCode.FRAME_NOT_FOUND));
		PhotoSession session = new PhotoSession(frameTemplate, expiryMinutes);
		photoSessionRepository.save(session);
		return SessionCreateResponse.from(session);
	}

	public void uploadPhoto(UUID sessionId, int shotIndex, MultipartFile file) {
		PhotoSession session = getSessionOrThrow(sessionId);
		FrameTemplate frameTemplate = session.getFrameTemplate();
		if (shotIndex < 0 || shotIndex >= frameTemplate.getRequiredShotCount()) {
			throw new ApiException(ErrorCode.INVALID_SHOT_INDEX);
		}
		if (file.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_FILE);
		}

		String key = "raw-photos/%s/%d.jpg".formatted(sessionId, shotIndex);
		try {
			storageService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		boolean alreadyCaptured = capturedPhotoRepository.findBySessionIdAndShotIndex(sessionId, shotIndex).isPresent();
		if (!alreadyCaptured) {
			capturedPhotoRepository.save(new CapturedPhoto(session, shotIndex, key));
		}

		if (capturedPhotoRepository.countBySessionId(sessionId) >= frameTemplate.getRequiredShotCount()) {
			session.markCaptured();
		}
	}

	@Transactional(readOnly = true)
	public List<CapturedPhotoResponse> listPhotos(UUID sessionId) {
		getSessionOrThrow(sessionId);
		return capturedPhotoRepository.findBySessionIdOrderByShotIndexAsc(sessionId).stream()
			.map(photo -> CapturedPhotoResponse.of(photo, storageService.getUrl(photo.getImageKey())))
			.toList();
	}

	public void saveArrangement(UUID sessionId, ArrangementRequest request) {
		PhotoSession session = getSessionOrThrow(sessionId);
		FrameTemplate frameTemplate = session.getFrameTemplate();

		if (session.getStatus() == PhotoSessionStatus.CREATED) {
			throw new ApiException(ErrorCode.SHOT_COUNT_NOT_MET);
		}
		if (request.assignments().size() != frameTemplate.slotCount()) {
			throw new ApiException(ErrorCode.INVALID_ARRANGEMENT, "슬롯 수와 배치 항목 수가 일치하지 않습니다.");
		}

		Map<Integer, FrameSlot> slotsByIndex = frameTemplate.getSlots().stream()
			.collect(Collectors.toMap(FrameSlot::getSlotIndex, Function.identity()));
		Map<Long, CapturedPhoto> photosById = capturedPhotoRepository.findBySessionIdOrderByShotIndexAsc(sessionId)
			.stream()
			.collect(Collectors.toMap(CapturedPhoto::getId, Function.identity()));

		List<SlotAssignment> newAssignments = request.assignments().stream()
			.map(item -> {
				FrameSlot slot = slotsByIndex.get(item.slotIndex());
				CapturedPhoto photo = photosById.get(item.capturedPhotoId());
				if (slot == null || photo == null) {
					throw new ApiException(ErrorCode.INVALID_ARRANGEMENT);
				}
				return new SlotAssignment(session, slot, photo);
			})
			.toList();

		if (newAssignments.stream().map(a -> a.getFrameSlot().getSlotIndex()).distinct().count() != slotsByIndex.size()) {
			throw new ApiException(ErrorCode.INVALID_ARRANGEMENT, "모든 슬롯이 채워져야 합니다.");
		}

		slotAssignmentRepository.deleteBySessionId(sessionId);
		slotAssignmentRepository.saveAll(newAssignments);
		session.markArranged();
	}

	@Transactional(readOnly = true)
	public SessionStatusResponse getStatus(UUID sessionId) {
		PhotoSession session = getSessionOrThrow(sessionId);
		return SessionStatusResponse.of(session, null, null, null);
	}

	PhotoSession getSessionOrThrow(UUID sessionId) {
		PhotoSession session = photoSessionRepository.findById(sessionId)
			.orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
		if (session.isExpired()) {
			throw new ApiException(ErrorCode.SESSION_EXPIRED);
		}
		return session;
	}
}
