package com.fourcut.photo.download;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.fourcut.photo.composite.CompositeImageRepository;
import com.fourcut.photo.download.dto.DownloadContentsResponse;
import com.fourcut.photo.download.dto.DownloadLinkResponse;
import com.fourcut.photo.session.PhotoSession;
import com.fourcut.photo.storage.StorageService;
import com.fourcut.photo.video.CaptureVideoRepository;
import com.fourcut.photo.video.QrCodeService;
import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * QR 이 가리키는 다운로드 링크를 발급하고, 그 링크로 사진·영상 주소를 돌려준다.
 *
 * 링크는 세션당 하나고 한 번 만들면 바뀌지 않는다. 영상과 합성물이 어느 순서로
 * 올라와도 같은 QR 이 나와야 하기 때문이다.
 */
@Service
@Transactional
public class DownloadService {

	/** 세션당 하나라 키를 세션 id 로 정한다. 다시 발급해도 같은 자리를 덮어쓴다. */
	private static final String QR_KEY_FORMAT = "qrcodes/%s.png";
	private static final String PHOTO_FILENAME = "fourcut-photo.jpg";
	private static final String VIDEO_FILENAME = "fourcut-video.mp4";

	private final DownloadLinkRepository downloadLinkRepository;
	private final CompositeImageRepository compositeImageRepository;
	private final CaptureVideoRepository captureVideoRepository;
	private final StorageService storageService;
	private final QrCodeService qrCodeService;
	private final SecureRandom random = new SecureRandom();
	private final String baseUrl;
	private final int expiryDays;

	public DownloadService(
		DownloadLinkRepository downloadLinkRepository,
		CompositeImageRepository compositeImageRepository,
		CaptureVideoRepository captureVideoRepository,
		StorageService storageService,
		QrCodeService qrCodeService,
		@Value("${fourcut.download.base-url}") String baseUrl,
		@Value("${fourcut.download.expiry-days}") int expiryDays
	) {
		this.downloadLinkRepository = downloadLinkRepository;
		this.compositeImageRepository = compositeImageRepository;
		this.captureVideoRepository = captureVideoRepository;
		this.storageService = storageService;
		this.qrCodeService = qrCodeService;
		this.baseUrl = baseUrl;
		this.expiryDays = expiryDays;
	}

	/**
	 * 세션의 다운로드 링크와 QR 을 보장한다. 이미 있으면 그대로 쓴다.
	 *
	 * QR PNG 는 매번 다시 올린다. 토큰이 같으면 내용도 같아서 덮어써도 결과가 같고,
	 * 링크만 있고 PNG 가 없는 상태(업로드 실패 후 재시도 등)를 스스로 메운다.
	 */
	public DownloadLinkResponse issue(PhotoSession session) {
		DownloadLink link = downloadLinkRepository.findBySessionId(session.getId())
			.orElseGet(() -> downloadLinkRepository.save(new DownloadLink(session, generateToken(), expiryDays)));

		String pageUrl = pageUrl(link.getToken());
		byte[] qrPng = qrCodeService.generatePng(pageUrl);
		String qrCodeKey = QR_KEY_FORMAT.formatted(session.getId());
		storageService.upload(qrCodeKey, new ByteArrayInputStream(qrPng), qrPng.length, "image/png");

		return new DownloadLinkResponse(pageUrl, storageService.getUrl(qrCodeKey), link.getExpiresAt());
	}

	/**
	 * QR PNG 가 저장되는 키. 세션당 하나라 세션 id 로 정해진다.
	 *
	 * 이 형식을 아는 곳은 여기 하나여야 한다. CaptureVideo 가 같은 값을 컬럼에 들고 있는 건
	 * 예전 스키마와의 호환 때문이고, QR 의 주인은 DownloadLink 다.
	 */
	public String qrCodeKey(UUID sessionId) {
		return QR_KEY_FORMAT.formatted(sessionId);
	}

	/** 이미 발급된 링크만 돌려준다. 없으면 비어 있다(QR 을 새로 만들지 않는다). */
	@Transactional(readOnly = true)
	public Optional<DownloadLinkResponse> find(UUID sessionId) {
		return downloadLinkRepository.findBySessionId(sessionId)
			.map(link -> new DownloadLinkResponse(
				pageUrl(link.getToken()),
				storageService.getUrl(QR_KEY_FORMAT.formatted(sessionId)),
				link.getExpiresAt()));
	}

	/**
	 * 토큰으로 받아갈 것들을 찾는다.
	 *
	 * 세션 상태나 세션 만료는 보지 않는다. 여기서 세션을 다시 검사하면
	 * 30분 지난 세션의 QR 이 열리지 않는 OQ-15 문제가 그대로 돌아온다.
	 */
	@Transactional(readOnly = true)
	public DownloadContentsResponse getContents(String token) {
		DownloadLink link = downloadLinkRepository.findByToken(token)
			.orElseThrow(() -> new ApiException(ErrorCode.DOWNLOAD_LINK_NOT_FOUND));
		if (link.isExpired()) {
			throw new ApiException(ErrorCode.DOWNLOAD_LINK_EXPIRED);
		}

		UUID sessionId = link.getSession().getId();
		Optional<String> photoKey = compositeImageRepository.findBySessionId(sessionId)
			.map(image -> image.getImageKey());

		return new DownloadContentsResponse(
			photoKey.map(key -> storageService.getDownloadUrl(key, PHOTO_FILENAME)).orElse(null),
			photoKey.map(storageService::getUrl).orElse(null),
			captureVideoRepository.findBySessionId(sessionId)
				.map(video -> storageService.getDownloadUrl(video.getVideoKey(), VIDEO_FILENAME))
				.orElse(null),
			link.getExpiresAt());
	}

	private String pageUrl(String token) {
		return "%s/d/%s".formatted(resolveBaseUrl(), token);
	}

	/**
	 * QR 에 담을 공개 주소.
	 *
	 * 설정이 비어 있으면 지금 요청에서 유추한다. nginx 가 X-Forwarded-* 를 주고
	 * forward-headers-strategy 가 켜져 있어서 https 주소가 그대로 나온다.
	 * 배치 작업 등 요청 밖에서 부르게 되면 그때는 설정을 채워야 한다.
	 */
	private String resolveBaseUrl() {
		if (StringUtils.hasText(baseUrl)) {
			return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		}
		return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
	}

	/** 추측으로 남의 사진을 받아갈 수 없어야 해서 128비트 난수를 쓴다. */
	private String generateToken() {
		byte[] bytes = new byte[16];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
