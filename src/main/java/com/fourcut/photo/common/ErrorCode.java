package com.fourcut.photo.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	FRAME_NOT_FOUND(HttpStatus.NOT_FOUND, "프레임을 찾을 수 없습니다."),
	SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
	SESSION_EXPIRED(HttpStatus.GONE, "세션이 만료되었습니다."),
	PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "촬영된 사진을 찾을 수 없습니다."),
	INVALID_SHOT_INDEX(HttpStatus.BAD_REQUEST, "유효하지 않은 촬영 순번입니다."),
	SHOT_COUNT_NOT_MET(HttpStatus.CONFLICT, "아직 필요한 장수만큼 촬영되지 않았습니다."),
	INVALID_ARRANGEMENT(HttpStatus.BAD_REQUEST, "슬롯 배치 정보가 프레임과 일치하지 않습니다."),
	ARRANGEMENT_NOT_READY(HttpStatus.CONFLICT, "아직 사진 배치가 완료되지 않았습니다."),
	COMPOSITE_NOT_READY(HttpStatus.CONFLICT, "아직 합성된 이미지가 없습니다."),
	VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "촬영 영상을 찾을 수 없습니다."),
	INVALID_FILE(HttpStatus.BAD_REQUEST, "업로드된 파일이 유효하지 않습니다."),
	STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
}
