package pro.teaz.study.lecture;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import pro.teaz.study.ai.StudyAi;
import pro.teaz.study.ai.StudyAi.PageDraft;
import pro.teaz.study.course.Access;
import pro.teaz.study.course.Course;
import pro.teaz.study.course.CourseRepository;
import pro.teaz.study.jobs.Jobs;

@Service
public class LectureService {

	private static final Logger log = LoggerFactory.getLogger(LectureService.class);

	private final Access access;
	private final CourseRepository courses;
	private final LectureRepository lectures;
	private final LecturePageRepository pages;
	private final LectureMaterial material;
	private final StudyAi ai;
	private final Jobs jobs;
	private final TransactionTemplate tx;
	private final JsonMapper json;

	public LectureService(Access access, CourseRepository courses, LectureRepository lectures,
			LecturePageRepository pages, LectureMaterial material, StudyAi ai, Jobs jobs, TransactionTemplate tx,
			JsonMapper json) {
		this.access = access;
		this.courses = courses;
		this.lectures = lectures;
		this.pages = pages;
		this.material = material;
		this.ai = ai;
		this.jobs = jobs;
		this.tx = tx;
		this.json = json;
	}

	public LectureDtos.LectureDetail detail(UUID lectureId) {
		return tx.execute(status -> {
			Lecture lecture = access.lecture(lectureId);
			Course course = courses.findById(lecture.getCourseId()).orElseThrow();
			List<Lecture> siblings = lectures.findByCourseIdOrderByPosition(course.getId());
			int index = siblings.indexOf(lecture);
			return new LectureDtos.LectureDetail(lecture.getId(), course.getId(), course.getTitle(), lecture.getTitle(),
					lecture.getSummary(), lecture.getPagesStatus(), lecture.getStatusMessage(),
					index > 0 ? siblings.get(index - 1).getId() : null,
					index >= 0 && index < siblings.size() - 1 ? siblings.get(index + 1).getId() : null,
					pages.findByLectureIdOrderByPosition(lectureId).stream()
							.map(page -> new LectureDtos.PageView(page.getId(), page.getPosition(), page.getTitle(),
									json.readTree(page.getBlocks()), page.isCompleted()))
							.toList());
		});
	}

	/**
	 * Pages are made on first open (or retry), not right after the upload: only
	 * lectures someone actually studies cost anything.
	 */
	public LectureDtos.LectureDetail generate(UUID lectureId) {
		tx.executeWithoutResult(status -> {
			Lecture lecture = access.lecture(lectureId);
			switch (lecture.getPagesStatus()) {
				case GENERATING -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Pages are already being generated.");
				case READY -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Pages already exist.");
				default -> lecture.setPagesStatus(Lecture.PagesStatus.GENERATING, null);
			}
		});
		jobs.run("pages " + lectureId, () -> runGeneration(lectureId));
		return detail(lectureId);
	}

	/** Background job: the Claude call runs outside any transaction. */
	void runGeneration(UUID lectureId) {
		Lecture lecture = lectures.findById(lectureId).orElse(null);
		if (lecture == null) return;
		try {
			Course course = courses.findById(lecture.getCourseId()).orElseThrow();
			List<PageDraft> drafts = ai.writePages(course.getTitle(), lecture.getTitle(), lecture.getSummary(),
					material.parts(lecture));
			tx.executeWithoutResult(status -> {
				pages.deleteAll(pages.findByLectureIdOrderByPosition(lectureId));
				for (int i = 0; i < drafts.size(); i++) {
					pages.save(new LecturePage(lectureId, i, drafts.get(i).title(),
							json.writeValueAsString(drafts.get(i).blocks())));
				}
				lectures.findById(lectureId).ifPresent(l -> l.setPagesStatus(Lecture.PagesStatus.READY, null));
			});
		} catch (RuntimeException e) {
			log.warn("Page generation for lecture {} failed", lectureId, e);
			String message = e instanceof StudyAi.AiException ? e.getMessage() : "Generation failed: " + e.getMessage();
			tx.executeWithoutResult(status ->
					lectures.findById(lectureId).ifPresent(l -> l.setPagesStatus(Lecture.PagesStatus.FAILED, message)));
		}
	}

	/** Info pages complete when read, task pages when every task is solved: decided by the frontend. */
	public void setCompleted(UUID pageId, boolean completed) {
		tx.executeWithoutResult(status -> access.page(pageId).setCompleted(completed));
	}

	public void resetProgress(UUID lectureId) {
		tx.executeWithoutResult(status -> {
			access.lecture(lectureId);
			pages.findByLectureIdOrderByPosition(lectureId).forEach(page -> page.setCompleted(false));
		});
	}
}
