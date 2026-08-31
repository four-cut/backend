package com.fourcut.photo.frame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fourcut.photo.storage.StorageService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
// @RequestPart 로 JSON 파트와 파일 파트를 함께 받는 게 이 코드베이스에서 처음이라, 실제 HTTP
// 멀티파트 요청으로 Jackson 바인딩까지 되는지 직접 확인한다(Boot 4 기본 Jackson 3 환경이라
// {@link RequestPart} 의 JSON 역직렬화가 조용히 깨지는 경우를 조기에 잡기 위함).
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FrameControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FrameTemplateRepository frameTemplateRepository;

	@MockitoBean
	private StorageService storageService;

	@Test
	void 슬롯과_이미지를_함께_등록하면_201과_생성된_프레임을_반환한다() throws Exception {
		when(storageService.getUrl(anyString())).thenReturn("https://cdn.example.com/frames/1/asset.png");

		String requestJson = """
			{
			  "name": "4컷 클래식",
			  "orientation": "PORTRAIT",
			  "canvasWidth": 600,
			  "canvasHeight": 1800,
			  "requiredShotCount": 4,
			  "slots": [
			    {"slotIndex": 0, "x": 0,   "y": 0,   "width": 600, "height": 400},
			    {"slotIndex": 1, "x": 0,   "y": 450, "width": 600, "height": 400},
			    {"slotIndex": 2, "x": 0,   "y": 900, "width": 600, "height": 400},
			    {"slotIndex": 3, "x": 0,   "y": 1350,"width": 600, "height": 400}
			  ]
			}
			""";
		MockMultipartFile request =
			new MockMultipartFile("request", "", "application/json", requestJson.getBytes(StandardCharsets.UTF_8));
		MockMultipartFile file =
			new MockMultipartFile("file", "asset.png", "image/png", "fake-png-bytes".getBytes());

		mockMvc.perform(multipart("/api/frames").file(request).file(file))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("4컷 클래식"))
			.andExpect(jsonPath("$.slots.length()").value(4))
			.andExpect(jsonPath("$.previewImageUrl").value("https://cdn.example.com/frames/1/asset.png"));

		verify(storageService).upload(anyString(), any(), anyLong(), anyString());
		assertThat(frameTemplateRepository.findByActiveTrue()).hasSize(1);
	}

	@Test
	void 이미지_형식이_지원되지_않으면_400과_ErrorResponse가_온다() throws Exception {
		String requestJson = """
			{"name":"테스트","orientation":"PORTRAIT","canvasWidth":100,"canvasHeight":100,
			 "requiredShotCount":1,"slots":[{"slotIndex":0,"x":0,"y":0,"width":50,"height":50}]}
			""";
		MockMultipartFile request =
			new MockMultipartFile("request", "", "application/json", requestJson.getBytes(StandardCharsets.UTF_8));
		MockMultipartFile file =
			new MockMultipartFile("file", "asset.gif", "image/gif", "not-supported".getBytes());

		mockMvc.perform(multipart("/api/frames").file(request).file(file))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_FILE"));
	}

	@Test
	void 슬롯이_캔버스를_벗어나면_400과_INVALID_FRAME이_온다() throws Exception {
		String requestJson = """
			{"name":"테스트","orientation":"PORTRAIT","canvasWidth":100,"canvasHeight":100,
			 "requiredShotCount":1,"slots":[{"slotIndex":0,"x":50,"y":50,"width":100,"height":100}]}
			""";
		MockMultipartFile request =
			new MockMultipartFile("request", "", "application/json", requestJson.getBytes(StandardCharsets.UTF_8));
		MockMultipartFile file =
			new MockMultipartFile("file", "asset.png", "image/png", "fake-png-bytes".getBytes());

		mockMvc.perform(multipart("/api/frames").file(request).file(file))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_FRAME"));
	}

	@Test
	void slotIndex가_연속되지_않으면_400과_INVALID_FRAME이_온다() throws Exception {
		String requestJson = """
			{"name":"테스트","orientation":"PORTRAIT","canvasWidth":600,"canvasHeight":600,
			 "requiredShotCount":2,
			 "slots":[{"slotIndex":0,"x":0,"y":0,"width":100,"height":100},
			          {"slotIndex":2,"x":0,"y":100,"width":100,"height":100}]}
			""";
		MockMultipartFile request =
			new MockMultipartFile("request", "", "application/json", requestJson.getBytes(StandardCharsets.UTF_8));
		MockMultipartFile file =
			new MockMultipartFile("file", "asset.png", "image/png", "fake-png-bytes".getBytes());

		mockMvc.perform(multipart("/api/frames").file(request).file(file))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_FRAME"));
	}
}
