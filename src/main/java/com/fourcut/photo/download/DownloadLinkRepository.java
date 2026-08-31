package com.fourcut.photo.download;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadLinkRepository extends JpaRepository<DownloadLink, Long> {

	Optional<DownloadLink> findByToken(String token);

	Optional<DownloadLink> findBySessionId(UUID sessionId);
}
