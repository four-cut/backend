package com.fourcut.photo.video.dto;

/**
 * 영상 업로드 결과.
 *
 * qrCodeUrl 은 QR 이미지(PNG) 주소고, 그 QR 안에 들어 있는 것이 downloadUrl 이다.
 * 예전에는 QR 이 영상 파일을 바로 가리켜서 사진을 함께 줄 수 없었다(OQ-13).
 */
public record VideoUploadResponse(String videoUrl, String qrCodeUrl, String downloadUrl) {
}
