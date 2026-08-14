package com.fourcut.photo.video;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptureVideoRepository extends JpaRepository<CaptureVideo, Long> {

	Optional<CaptureVideo> findBySessionId(UUID sessionId);
}
