package com.fourcut.photo.download;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.download.dto.DownloadContentsResponse;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * QR 을 찍으면 열리는 페이지. 손님 폰의 브라우저가 여는 유일한 화면이다.
 *
 * 앱용 API 가 아니라서 JSON 에러를 띄울 수 없다. 링크가 없거나 만료된 것도
 * 예외로 던지지 않고 페이지 안에서 설명한다.
 */
@Hidden
@Controller
public class DownloadPageController {

	private static final DateTimeFormatter EXPIRES_AT_FORMAT =
		DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");

	private final DownloadService downloadService;

	public DownloadPageController(DownloadService downloadService) {
		this.downloadService = downloadService;
	}

	@GetMapping("/d/{token}")
	public String page(@PathVariable String token, Model model) {
		try {
			DownloadContentsResponse contents = downloadService.getContents(token);
			model.addAttribute("contents", contents);
			// 날짜 포맷은 자바에서 끝낸다. 템플릿에서 #temporals 를 쓰려면 별도 방언이 필요하다.
			model.addAttribute("expiresAtText", EXPIRES_AT_FORMAT.format(contents.expiresAt()));
		} catch (ApiException e) {
			model.addAttribute("errorMessage", e.getMessage());
		}
		return "download";
	}
}
