package com.fourcut.photo.composite.dto;

/** 앱이 만든 스트립을 올린 결과. QR 은 다운로드 페이지를 가리킨다. */
public record CompositeUploadResponse(String imageUrl, String qrCodeUrl, String downloadUrl) {
}
