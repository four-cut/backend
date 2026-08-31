package com.fourcut.photo.download.dto;

import java.time.LocalDateTime;

/**
 * 다운로드 페이지가 보여줄 것.
 *
 * 사진과 영상은 각각 없을 수 있다. 앱이 스트립을 안 올렸으면 photoUrl 이,
 * 영상 업로드가 실패했으면 videoUrl 이 null 이다.
 *
 * photoUrl 은 저장을 강제하는 주소라 <img> 에 그대로 쓰면 브라우저마다 동작이 갈린다.
 * 화면에 미리 보여줄 때는 photoPreviewUrl 을 쓴다.
 */
public record DownloadContentsResponse(
	String photoUrl,
	String photoPreviewUrl,
	String videoUrl,
	LocalDateTime expiresAt
) {

	public boolean isEmpty() {
		return photoUrl == null && videoUrl == null;
	}
}
