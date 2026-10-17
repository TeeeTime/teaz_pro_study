package pro.teaz.study.lecture;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LectureRepository extends JpaRepository<Lecture, UUID> {

	List<Lecture> findByCourseIdOrderByPosition(UUID courseId);

	int countByCourseId(UUID courseId);

	/** Jobs don't survive a restart; see StaleJobReset. */
	@Modifying
	@Query("update Lecture l set l.pagesStatus = pro.teaz.study.lecture.Lecture.PagesStatus.FAILED, "
			+ "l.statusMessage = :message where l.pagesStatus = pro.teaz.study.lecture.Lecture.PagesStatus.GENERATING")
	int failRunning(String message);
}
