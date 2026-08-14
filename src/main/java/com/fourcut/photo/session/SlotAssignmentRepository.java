package com.fourcut.photo.session;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotAssignmentRepository extends JpaRepository<SlotAssignment, Long> {

	List<SlotAssignment> findBySessionIdOrderByFrameSlot_SlotIndexAsc(UUID sessionId);

	void deleteBySessionId(UUID sessionId);
}
