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
	INVALID_FRAME(HttpStatus.BAD_REQUEST, "프레임 정보가 유효하지 않습니다."),
	ARRANGEMENT_NOT_READY(HttpStatus.CONFLICT, "아직 사진 배치가 완료되지 않았습니다."),
	COMPOSITE_NOT_READY(HttpStatus.CONFLICT, "아직 합성된 이미지가 없습니다."),
	VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "촬영 영상을 찾을 수 없습니다."),
	INVALID_FILE(HttpStatus.BAD_REQUEST, "업로드된 파일이 유효하지 않습니다."),
	STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다."),

	UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다."),
	INVALID_OAUTH_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰이 유효하지 않습니다."),
	OAUTH_APP_MISMATCH(HttpStatus.UNAUTHORIZED, "다른 앱에서 발급된 토큰입니다."),
	OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "소셜 로그인 제공자와 통신에 실패했습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "액세스 토큰이 유효하지 않습니다."),
	EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."),
	EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),
	REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "이미 사용된 리프레시 토큰입니다. 다시 로그인해 주세요."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");

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
