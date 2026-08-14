package com.fourcut.photo.storage;

import java.io.InputStream;

public interface StorageService {

	void upload(String key, InputStream content, long contentLength, String contentType);

	InputStream download(String key);

	String getUrl(String key);
}
