package com.fourcut.photo.frame;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FrameTemplateRepository extends JpaRepository<FrameTemplate, Long> {

	List<FrameTemplate> findByActiveTrueAndOrientation(FrameOrientation orientation);

	List<FrameTemplate> findByActiveTrue();

	@EntityGraph(attributePaths = "slots")
	Optional<FrameTemplate> findWithSlotsById(Long id);
}
