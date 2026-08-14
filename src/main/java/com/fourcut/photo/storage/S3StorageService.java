package com.fourcut.photo.storage;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import java.io.InputStream;
import java.time.Duration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3StorageService implements StorageService {

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final S3Properties s3Properties;

	public S3StorageService(S3Client s3Client, S3Presigner s3Presigner, S3Properties s3Properties) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.s3Properties = s3Properties;
	}

	@Override
	public void upload(String key, InputStream content, long contentLength, String contentType) {
		try {
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(s3Properties.bucket())
				.key(key)
				.contentType(contentType)
				.build();
			s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
		} catch (S3Exception e) {
			throw new ApiException(ErrorCode.STORAGE_ERROR, e.getMessage());
		}
	}

	@Override
	public InputStream download(String key) {
		try {
			GetObjectRequest request = GetObjectRequest.builder()
				.bucket(s3Properties.bucket())
				.key(key)
				.build();
			return s3Client.getObject(request);
		} catch (S3Exception e) {
			throw new ApiException(ErrorCode.STORAGE_ERROR, e.getMessage());
		}
	}

	@Override
	public String getUrl(String key) {
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
			.bucket(s3Properties.bucket())
			.key(key)
			.build();
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(s3Properties.presignedUrlExpiryMinutes()))
			.getObjectRequest(getObjectRequest)
			.build();
		return s3Presigner.presignGetObject(presignRequest).url().toString();
	}
}
