package com.fourcut.photo.storage;

import java.io.InputStream;

public interface StorageService {

	void upload(String key, InputStream content, long contentLength, String contentType);

	InputStream download(String key);

	String getUrl(String key);

	/**
	 * 브라우저가 열어보지 않고 바로 저장하도록 content-disposition 을 붙인 URL.
	 * 모바일 브라우저는 mp4 를 그냥 열면 재생만 하고 저장 수단을 주지 않는다.
	 */
	String getDownloadUrl(String key, String filename);
}
