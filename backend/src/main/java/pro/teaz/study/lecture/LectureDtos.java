package pro.teaz.study.lecture;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** JSON shapes of the lecture API. */
public final class LectureDtos {

	private LectureDtos() {
	}

	/** @param blocks the page's blocks as stored, see ai/AiSchemas */
	public record PageView(UUID id, int position, String title, JsonNode blocks, boolean completed) {
	}

	public record LectureDetail(UUID id, UUID courseId, String courseTitle, String title, String summary,
			Lecture.PagesStatus pagesStatus, String statusMessage, UUID previousLectureId, UUID nextLectureId,
			List<PageView> pages) {
	}

	public record CompletedRequest(boolean completed) {
	}
}
