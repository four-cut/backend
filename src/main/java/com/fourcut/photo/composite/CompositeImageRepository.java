package com.fourcut.photo.composite;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompositeImageRepository extends JpaRepository<CompositeImage, Long> {

	Optional<CompositeImage> findBySessionId(UUID sessionId);
}
