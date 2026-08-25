package com.fourcut.photo.frame;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.frame.dto.FrameCreateRequest;
import com.fourcut.photo.frame.dto.FrameDetailResponse;
import com.fourcut.photo.frame.dto.FrameSummaryResponse;
import com.fourcut.photo.storage.StorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class FrameService {

	// 프레임 이미지는 슬롯이 뚫린 투명 배경이 많아 PNG/WEBP 위주다. 확장자는 원본 파일명이 아니라
	// 검증된 content-type 에서만 뽑아서, 확장자와 실제 내용이 어긋나는 걸 막는다.
	private static final Map<String, String> ALLOWED_IMAGE_EXTENSIONS = Map.of(
		"image/png", "png",
		"image/jpeg", "jpg",
		"image/webp", "webp"
	);

	private final FrameTemplateRepository frameTemplateRepository;
	private final StorageService storageService;

	public FrameService(FrameTemplateRepository frameTemplateRepository, StorageService storageService) {
		this.frameTemplateRepository = frameTemplateRepository;
		this.storageService = storageService;
	}

	public List<FrameSummaryResponse> getActiveFrames(FrameOrientation orientation) {
		List<FrameTemplate> templates = orientation == null
			? frameTemplateRepository.findByActiveTrue()
			: frameTemplateRepository.findByActiveTrueAndOrientation(orientation);
		return templates.stream()
			.map(template -> FrameSummaryResponse.of(template, storageService.getUrl(template.getFrameAssetKey())))
			.toList();
	}

	public FrameDetailResponse getFrameDetail(Long frameId) {
		FrameTemplate template = getFrameOrThrow(frameId);
		return FrameDetailResponse.of(template, storageService.getUrl(template.getFrameAssetKey()));
	}

	// TODO 지금은 인증 없이 누구나 호출할 수 있다. 내부 관리용으로만 쓰다가, 외부에 노출되기 전에
	// KAKAO_ADMIN_KEY 와 같은 방식으로 관리자 키 검증을 추가할 것 (SecurityConfig 에서
	// POST /api/frames 한 줄만 hasRole("ADMIN") 이나 별도 필터로 잠그면 된다).
	@Transactional
	public FrameDetailResponse create(FrameCreateRequest request, MultipartFile file) {
		String extension = validateAndResolveExtension(file);
		validateSlots(request);

		FrameTemplate template = new FrameTemplate(
			request.name(), request.orientation(), request.canvasWidth(), request.canvasHeight(),
			request.requiredShotCount(), null);
		request.slots().forEach(item ->
			template.addSlot(new FrameSlot(item.slotIndex(), item.x(), item.y(), item.width(), item.height())));

		// IDENTITY 전략이라 save() 시점에 즉시 INSERT 되어 id 가 확정된다. 그 id 로 스토리지 키를
		// 만들어야 해서 이미지 업로드보다 먼저 저장한다. save() 가 반환하는 인스턴스는 인자와 동일한
		// 참조이므로(JPA save 는 새 객체를 만들지 않는다) 재할당하지 않는다.
		frameTemplateRepository.save(template);

		String assetKey = "frames/%d/asset.%s".formatted(template.getId(), extension);
		try {
			storageService.upload(assetKey, file.getInputStream(), file.getSize(), file.getContentType());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		template.updateAssetKey(assetKey);

		return FrameDetailResponse.of(template, storageService.getUrl(assetKey));
	}

	private String validateAndResolveExtension(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_FILE);
		}
		String extension = ALLOWED_IMAGE_EXTENSIONS.get(file.getContentType());
		if (extension == null) {
			throw new ApiException(ErrorCode.INVALID_FILE, "지원하지 않는 이미지 형식입니다: " + file.getContentType());
		}
		return extension;
	}

	// 슬롯 인덱스가 사람이 직접 입력하는 값이라 오타로 겹치거나 빠지기 쉽다. 0부터 연속된
	// 값이어야 saveArrangement 에서 slotIndex 로 슬롯을 정확히 찾을 수 있다.
	// 캔버스를 벗어나는 좌표도 등록 시점에 걸러 실제 합성 단계에서야 발견하는 일을 막는다.
	private void validateSlots(FrameCreateRequest request) {
		List<FrameCreateRequest.SlotItem> slots = request.slots();
		if (slots.size() > request.requiredShotCount()) {
			throw new ApiException(ErrorCode.INVALID_FRAME, "슬롯 수가 필요 촬영 장수보다 많습니다.");
		}

		Set<Integer> indexes = slots.stream().map(FrameCreateRequest.SlotItem::slotIndex).collect(Collectors.toSet());
		if (indexes.size() != slots.size()) {
			throw new ApiException(ErrorCode.INVALID_FRAME, "slotIndex 가 중복되었습니다.");
		}
		for (int i = 0; i < slots.size(); i++) {
			if (!indexes.contains(i)) {
				throw new ApiException(ErrorCode.INVALID_FRAME, "slotIndex 는 0부터 연속된 값이어야 합니다.");
			}
		}

		for (FrameCreateRequest.SlotItem slot : slots) {
			if (slot.x() + slot.width() > request.canvasWidth() || slot.y() + slot.height() > request.canvasHeight()) {
				throw new ApiException(ErrorCode.INVALID_FRAME,
					"slotIndex %d 가 캔버스 크기를 벗어납니다.".formatted(slot.slotIndex()));
			}
		}
	}

	FrameTemplate getFrameOrThrow(Long frameId) {
		return frameTemplateRepository.findWithSlotsById(frameId)
			.orElseThrow(() -> new ApiException(ErrorCode.FRAME_NOT_FOUND));
	}
}
