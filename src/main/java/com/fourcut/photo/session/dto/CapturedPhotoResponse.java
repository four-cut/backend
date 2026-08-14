package com.fourcut.photo.session.dto;

import com.fourcut.photo.session.CapturedPhoto;

public record CapturedPhotoResponse(Long photoId, int shotIndex, String url) {

	public static CapturedPhotoResponse of(CapturedPhoto photo, String url) {
		return new CapturedPhotoResponse(photo.getId(), photo.getShotIndex(), url);
	}
}
