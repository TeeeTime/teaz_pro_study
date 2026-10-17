package pro.teaz.study.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pro.teaz.study.course.CourseRepository;
import pro.teaz.study.lecture.LectureRepository;

/** Jobs live in memory: whatever was running when the app stopped is marked failed, so it can be retried. */
@Component
public class StaleJobReset implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StaleJobReset.class);
	private static final String MESSAGE = "Interrupted by a restart. Please try again.";

	private final CourseRepository courses;
	private final LectureRepository lectures;

	public StaleJobReset(CourseRepository courses, LectureRepository lectures) {
		this.courses = courses;
		this.lectures = lectures;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		int reset = courses.failRunning(MESSAGE) + lectures.failRunning(MESSAGE);
		if (reset > 0) log.warn("Marked {} interrupted job(s) as failed", reset);
	}
}
