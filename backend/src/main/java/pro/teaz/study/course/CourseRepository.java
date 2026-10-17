package pro.teaz.study.course;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CourseRepository extends JpaRepository<Course, UUID> {

	List<Course> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

	Optional<Course> findByIdAndOwnerId(UUID id, String ownerId);

	/** Jobs don't survive a restart; see StaleJobReset. */
	@Modifying
	@Query("update Course c set c.status = pro.teaz.study.course.Course.Status.FAILED, c.statusMessage = :message "
			+ "where c.status = pro.teaz.study.course.Course.Status.ANALYZING")
	int failRunning(String message);
}
