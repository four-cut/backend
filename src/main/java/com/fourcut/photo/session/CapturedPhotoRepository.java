package com.fourcut.photo.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapturedPhotoRepository extends JpaRepository<CapturedPhoto, Long> {

	List<CapturedPhoto> findBySessionIdOrderByShotIndexAsc(UUID sessionId);

	Optional<CapturedPhoto> findBySessionIdAndShotIndex(UUID sessionId, int shotIndex);

	long countBySessionId(UUID sessionId);
}
