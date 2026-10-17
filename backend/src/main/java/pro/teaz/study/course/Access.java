package pro.teaz.study.course;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import pro.teaz.study.lecture.Lecture;
import pro.teaz.study.lecture.LecturePage;
import pro.teaz.study.lecture.LecturePageRepository;
import pro.teaz.study.lecture.LectureRepository;
import pro.teaz.study.security.CurrentUser;

/**
 * Loads an entity only if it belongs to the current user's courses. Someone
 * else's course answers 404, same as one that doesn't exist.
 */
@Component
public class Access {

	private final CurrentUser user;
	private final CourseRepository courses;
	private final LectureRepository lectures;
	private final LecturePageRepository pages;

	public Access(CurrentUser user, CourseRepository courses, LectureRepository lectures, LecturePageRepository pages) {
		this.user = user;
		this.courses = courses;
		this.lectures = lectures;
		this.pages = pages;
	}

	public Course course(UUID courseId) {
		return courses.findByIdAndOwnerId(courseId, user.id()).orElseThrow(() -> notFound("Course"));
	}

	public Lecture lecture(UUID lectureId) {
		Lecture lecture = lectures.findById(lectureId).orElseThrow(() -> notFound("Lecture"));
		course(lecture.getCourseId());
		return lecture;
	}

	public LecturePage page(UUID pageId) {
		LecturePage page = pages.findById(pageId).orElseThrow(() -> notFound("Page"));
		lecture(page.getLectureId());
		return page;
	}

	private static ResponseStatusException notFound(String what) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found.");
	}
}
