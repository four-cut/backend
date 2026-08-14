package com.fourcut.photo.session;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoSessionRepository extends JpaRepository<PhotoSession, UUID> {
}
