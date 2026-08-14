package com.fourcut.photo.frame;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.frame.dto.FrameDetailResponse;
import com.fourcut.photo.frame.dto.FrameSummaryResponse;
import com.fourcut.photo.storage.StorageService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FrameService {

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

	FrameTemplate getFrameOrThrow(Long frameId) {
		return frameTemplateRepository.findWithSlotsById(frameId)
			.orElseThrow(() -> new ApiException(ErrorCode.FRAME_NOT_FOUND));
	}
}
