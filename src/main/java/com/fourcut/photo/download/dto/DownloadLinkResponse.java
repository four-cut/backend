package com.fourcut.photo.download.dto;

import java.time.LocalDateTime;

/** 발급된 다운로드 링크. QR 이미지는 이 주소를 담고 있다. */
public record DownloadLinkResponse(String downloadUrl, String qrCodeUrl, LocalDateTime expiresAt) {
}
