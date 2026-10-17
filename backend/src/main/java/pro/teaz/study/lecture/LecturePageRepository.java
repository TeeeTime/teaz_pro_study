package pro.teaz.study.lecture;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LecturePageRepository extends JpaRepository<LecturePage, UUID> {

	List<LecturePage> findByLectureIdOrderByPosition(UUID lectureId);

	/** Progress per lecture: [lectureId, completed pages, all pages]. */
	@Query("select p.lectureId, sum(case when p.completed then 1 else 0 end), count(p) "
			+ "from LecturePage p where p.lectureId in :lectureIds group by p.lectureId")
	List<Object[]> progressByLecture(Collection<UUID> lectureIds);
}
