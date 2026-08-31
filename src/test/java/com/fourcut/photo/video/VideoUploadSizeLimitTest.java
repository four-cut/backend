package com.fourcut.photo.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 업로드 용량 초과가 401 이 아니라 413 으로 돌아오는지 본다.
 *
 * MockMvc 는 multipart 요청을 이미 파싱된 형태로 넘겨서 컨테이너의 용량 검사를
 * 아예 타지 않는다. 실제 톰캣을 띄워야 MaxUploadSizeExceededException 이 난다.
 * 한도는 테스트에서만 8KB 로 낮춰 큰 더미 파일 없이 초과 상황을 만든다.
 */
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = {
		"spring.servlet.multipart.max-file-size=8KB",
		"spring.servlet.multipart.max-request-size=8KB"
	}
)
@AutoConfigureTestRestTemplate
class VideoUploadSizeLimitTest {

	@Autowired
	private TestRestTemplate restTemplate;

	private ResponseEntity<String> uploadVideoOfSize(int bytes) {
		ByteArrayResource file = new ByteArrayResource(new byte[bytes]) {
			@Override
			public String getFilename() {
				return "capture.mp4";
			}
		};

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", file);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		return restTemplate.postForEntity(
			"/api/sessions/{sessionId}/video",
			new HttpEntity<>(body, headers),
			String.class,
			UUID.randomUUID());
	}

	@Test
	void 한도를_넘는_영상은_401이_아니라_413_FILE_TOO_LARGE_로_거절된다() {
		ResponseEntity<String> response = uploadVideoOfSize(64 * 1024);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(response.getBody()).contains("FILE_TOO_LARGE");
	}

	// 한도 안이면 인증이 아니라 세션 유무로 판정돼야 한다.
	// 없는 세션이라 404 가 정상이고, 401 이면 다시 인증 경로로 샌 것이다.
	@Test
	void 한도_안의_영상은_인증에_막히지_않는다() {
		ResponseEntity<String> response = uploadVideoOfSize(1024);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("SESSION_NOT_FOUND");
	}
}
