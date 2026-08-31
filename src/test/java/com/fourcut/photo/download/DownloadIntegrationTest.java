package com.fourcut.photo.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fourcut.photo.frame.FrameOrientation;
import com.fourcut.photo.frame.FrameTemplate;
import com.fourcut.photo.frame.FrameTemplateRepository;
import com.fourcut.photo.session.PhotoSession;
import com.fourcut.photo.session.PhotoSessionRepository;
import com.fourcut.photo.session.PhotoSessionStatus;
import com.fourcut.photo.storage.StorageService;
import com.fourcut.photo.video.CaptureVideo;
import com.fourcut.photo.video.CaptureVideoRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR 다운로드 경로(OQ-13/14/15) 통합 테스트.
 *
 * S3 는 대역으로 세운다. 확인하려는 것은 어떤 주소가 어떤 조건에서 나오는지지
 * 실제 업로드가 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DownloadIntegrationTest {

	private static final String INLINE_URL = "https://s3.example/inline";
	private static final String ATTACHMENT_URL = "https://s3.example/attachment";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FrameTemplateRepository frameTemplateRepository;

	@Autowired
	private PhotoSessionRepository photoSessionRepository;

	@Autowired
	private DownloadLinkRepository downloadLinkRepository;

	@Autowired
	private CaptureVideoRepository captureVideoRepository;

	@MockitoBean
	private StorageService storageService;

	@BeforeEach
	void stubStorage() {
		given(storageService.getUrl(anyString())).willReturn(INLINE_URL);
		given(storageService.getDownloadUrl(anyString(), anyString())).willReturn(ATTACHMENT_URL);
	}

	private PhotoSession session(int expiryMinutes) {
		FrameTemplate frame = frameTemplateRepository.save(
			new FrameTemplate("세로형", FrameOrientation.PORTRAIT, 1200, 1800, 4, "frames/portrait.png"));
		return photoSessionRepository.save(new PhotoSession(frame, expiryMinutes));
	}

	private DownloadLink link(PhotoSession session, int expiryDays) {
		return downloadLinkRepository.save(new DownloadLink(session, "token-" + session.getId(), expiryDays));
	}

	private MockMultipartFile strip(String contentType) {
		return new MockMultipartFile("file", "strip.jpg", contentType, "not-a-real-image".getBytes());
	}

	private String pageBody(String token) throws Exception {
		return mockMvc.perform(get("/d/{token}", token))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	void 앱이_만든_스트립을_올리면_QR과_다운로드_주소가_함께_온다() throws Exception {
		PhotoSession session = session(30);

		mockMvc.perform(multipart("/api/sessions/{id}/composite/image", session.getId())
				.file(strip("image/jpeg")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imageUrl").value(INLINE_URL))
			.andExpect(jsonPath("$.qrCodeUrl").value(INLINE_URL))
			.andExpect(jsonPath("$.downloadUrl").value(startsWith("https://test.fourcut.example/d/")));

		assertThat(downloadLinkRepository.findBySessionId(session.getId())).isPresent();
		assertThat(session.getStatus()).isEqualTo(PhotoSessionStatus.COMPOSED);
	}

	@Test
	void 이미지가_아닌_파일은_거절된다() throws Exception {
		PhotoSession session = session(30);

		mockMvc.perform(multipart("/api/sessions/{id}/composite/image", session.getId())
				.file(strip("text/plain")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_FILE"));
	}

	@Test
	void 다운로드_페이지는_사진과_영상_버튼을_보여준다() throws Exception {
		PhotoSession session = session(30);
		mockMvc.perform(multipart("/api/sessions/{id}/composite/image", session.getId())
			.file(strip("image/jpeg")));
		captureVideoRepository.save(
			new CaptureVideo(session, "videos/%s/capture.mp4".formatted(session.getId()), "qrcodes/x.png", 8));
		String token = downloadLinkRepository.findBySessionId(session.getId()).orElseThrow().getToken();

		String body = pageBody(token);

		assertThat(body).contains("사진 저장하기", "영상 저장하기", ATTACHMENT_URL);
	}

	// OQ-15. 부스에서 QR 을 찍는 시점은 촬영이 끝나고 한참 뒤일 수 있다.
	@Test
	void 세션이_만료돼도_다운로드_링크는_열린다() throws Exception {
		PhotoSession expired = session(-60);
		DownloadLink link = link(expired, 7);
		captureVideoRepository.save(
			new CaptureVideo(expired, "videos/%s/capture.mp4".formatted(expired.getId()), "qrcodes/x.png", 8));

		assertThat(expired.isExpired()).isTrue();
		assertThat(pageBody(link.getToken())).contains("영상 저장하기");
	}

	@Test
	void 링크_자체가_만료되면_페이지가_만료를_알린다() throws Exception {
		DownloadLink expiredLink = link(session(30), -1);

		assertThat(pageBody(expiredLink.getToken())).contains("다운로드 링크가 만료되었습니다");
	}

	@Test
	void 만료된_링크는_API에서_410으로_구분된다() throws Exception {
		DownloadLink expiredLink = link(session(30), -1);

		mockMvc.perform(get("/api/downloads/{token}", expiredLink.getToken()))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("DOWNLOAD_LINK_EXPIRED"));
	}

	@Test
	void 없는_토큰은_404다() throws Exception {
		mockMvc.perform(get("/api/downloads/{token}", "nope"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOWNLOAD_LINK_NOT_FOUND"));
	}
}
