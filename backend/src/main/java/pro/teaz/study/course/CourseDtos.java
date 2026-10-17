package pro.teaz.study.course;

import java.util.List;
import java.util.UUID;

import pro.teaz.study.lecture.Lecture;

/** JSON shapes of the course API. */
public final class CourseDtos {

	private CourseDtos() {
	}

	/** Completed pages out of all generated pages. Lectures without pages count 0/0. */
	public record Progress(int completed, int total) {
		public static final Progress NONE = new Progress(0, 0);

		public Progress plus(Progress other) {
			return new Progress(completed + other.completed, total + other.total);
		}
	}

	public record CourseSummary(UUID id, String title, Course.Status status, String statusMessage, int lectureCount,
			Progress progress) {
	}

	public record FileView(UUID id, String name, String mediaType, long sizeBytes, Integer pageCount, boolean analyzed) {
	}

	public record LectureSummary(UUID id, int position, String title, String summary, Lecture.PagesStatus pagesStatus,
			String statusMessage, Progress progress) {
	}

	public record CourseDetail(UUID id, String title, Course.Status status, String statusMessage, List<FileView> files,
			List<LectureSummary> lectures) {
	}

	public record TitleRequest(String title) {
	}
}
