package com.fourcut.photo.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fourcut.s3")
public record S3Properties(String bucket, String region, long presignedUrlExpiryMinutes) {
}
