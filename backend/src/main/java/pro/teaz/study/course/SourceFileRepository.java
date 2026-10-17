package pro.teaz.study.course;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {

	List<SourceFile> findByCourseIdOrderByUploadedAt(UUID courseId);

	List<SourceFile> findByCourseIdAndAnalyzedFalseOrderByUploadedAt(UUID courseId);
}
