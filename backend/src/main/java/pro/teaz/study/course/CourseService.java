package pro.teaz.study.course;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import pro.teaz.study.ai.StudyAi;
import pro.teaz.study.ai.StudyAi.PlannedLecture;
import pro.teaz.study.jobs.Jobs;
import pro.teaz.study.lecture.Lecture;
import pro.teaz.study.lecture.LecturePageRepository;
import pro.teaz.study.lecture.LectureRepository;
import pro.teaz.study.security.CurrentUser;
import pro.teaz.study.storage.FileStorage;

@Service
public class CourseService {

	private static final Logger log = LoggerFactory.getLogger(CourseService.class);

	static final Set<String> ALLOWED_TYPES = Set.of(SourceFile.PDF, "image/png", "image/jpeg", "image/gif", "image/webp");
	/**
	 * Per analysis run (all not yet analyzed files go into one Claude request):
	 * stays below the API's 600 pages and image limits with some headroom.
	 */
	static final int MAX_PAGES_PER_ANALYSIS = 550;
	static final int MAX_IMAGES_PER_ANALYSIS = 90;

	private final CurrentUser user;
	private final Access access;
	private final CourseRepository courses;
	private final SourceFileRepository files;
	private final LectureRepository lectures;
	private final LecturePageRepository pages;
	private final FileStorage storage;
	private final StudyAi ai;
	private final Jobs jobs;
	private final TransactionTemplate tx;
	private final JsonMapper json;

	public CourseService(CurrentUser user, Access access, CourseRepository courses, SourceFileRepository files,
			LectureRepository lectures, LecturePageRepository pages, FileStorage storage, StudyAi ai, Jobs jobs,
			TransactionTemplate tx, JsonMapper json) {
		this.user = user;
		this.access = access;
		this.courses = courses;
		this.files = files;
		this.lectures = lectures;
		this.pages = pages;
		this.storage = storage;
		this.ai = ai;
		this.jobs = jobs;
		this.tx = tx;
		this.json = json;
	}

	public List<CourseDtos.CourseSummary> list() {
		return tx.execute(status -> courses.findByOwnerIdOrderByCreatedAtDesc(user.id()).stream()
				.map(course -> {
					List<Lecture> courseLectures = lectures.findByCourseIdOrderByPosition(course.getId());
					CourseDtos.Progress progress = progress(courseLectures).values().stream()
							.reduce(CourseDtos.Progress.NONE, CourseDtos.Progress::plus);
					return new CourseDtos.CourseSummary(course.getId(), course.getTitle(), course.getStatus(),
							course.getStatusMessage(), courseLectures.size(), progress);
				})
				.toList());
	}

	public CourseDtos.CourseDetail create(String title) {
		if (title == null || title.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
		Course course = courses.save(new Course(user.id(), title.strip()));
		return detail(course.getId());
	}

	public CourseDtos.CourseDetail detail(UUID courseId) {
		return tx.execute(status -> {
			Course course = access.course(courseId);
			List<Lecture> courseLectures = lectures.findByCourseIdOrderByPosition(courseId);
			Map<UUID, CourseDtos.Progress> progress = progress(courseLectures);
			return new CourseDtos.CourseDetail(course.getId(), course.getTitle(), course.getStatus(),
					course.getStatusMessage(),
					files.findByCourseIdOrderByUploadedAt(courseId).stream()
							.map(f -> new CourseDtos.FileView(f.getId(), f.getOriginalName(), f.getMediaType(),
									f.getSizeBytes(), f.getPageCount(), f.isAnalyzed()))
							.toList(),
					courseLectures.stream()
							.map(l -> new CourseDtos.LectureSummary(l.getId(), l.getPosition(), l.getTitle(),
									l.getSummary(), l.getPagesStatus(), l.getStatusMessage(),
									progress.getOrDefault(l.getId(), CourseDtos.Progress.NONE)))
							.toList());
		});
	}

	public void rename(UUID courseId, String title) {
		if (title == null || title.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
		tx.executeWithoutResult(status -> access.course(courseId).setTitle(title.strip()));
	}

	public void delete(UUID courseId) {
		tx.executeWithoutResult(status -> courses.delete(access.course(courseId)));
		storage.deleteCourse(courseId);
	}

	/** Stores the files and starts splitting them (with any earlier unanalyzed ones) into lectures. */
	public CourseDtos.CourseDetail upload(UUID courseId, List<MultipartFile> uploads) {
		Course course = access.course(courseId);
		if (course.getStatus() == Course.Status.ANALYZING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is still being analyzed. Upload more afterwards.");
		}
		if (uploads == null || uploads.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files.");
		for (MultipartFile upload : uploads) {
			if (!ALLOWED_TYPES.contains(upload.getContentType())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						upload.getOriginalFilename() + ": only PDFs and images (PNG, JPEG, GIF, WebP) are supported.");
			}
		}

		List<SourceFile> stored = new ArrayList<>();
		try {
			for (MultipartFile upload : uploads) stored.add(store(courseId, upload));
			checkAnalysisLimits(courseId, stored);
		} catch (RuntimeException e) {
			stored.forEach(file -> storage.resolve(file.getStoragePath()).toFile().delete());
			throw e;
		}
		files.saveAll(stored);
		startAnalysis(courseId);
		return detail(courseId);
	}

	private SourceFile store(UUID courseId, MultipartFile upload) {
		UUID fileId = UUID.randomUUID();
		String path;
		try (InputStream in = upload.getInputStream()) {
			path = storage.save(courseId, fileId, in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		Integer pageCount = null;
		if (SourceFile.PDF.equals(upload.getContentType())) {
			try {
				pageCount = storage.pageCount(path);
			} catch (FileStorage.InvalidPdfException e) {
				storage.resolve(path).toFile().delete();
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, upload.getOriginalFilename() + " is not a readable PDF.");
			}
		}
		String name = upload.getOriginalFilename() == null ? "upload" : upload.getOriginalFilename();
		return new SourceFile(fileId, courseId, name, upload.getContentType(), upload.getSize(), pageCount, path);
	}

	private void checkAnalysisLimits(UUID courseId, List<SourceFile> added) {
		List<SourceFile> pending = new ArrayList<>(files.findByCourseIdAndAnalyzedFalseOrderByUploadedAt(courseId));
		pending.addAll(added);
		int pdfPages = pending.stream().filter(SourceFile::isPdf).mapToInt(SourceFile::getPageCount).sum();
		long images = pending.stream().filter(file -> !file.isPdf()).count();
		if (pdfPages > MAX_PAGES_PER_ANALYSIS || images > MAX_IMAGES_PER_ANALYSIS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too much material at once (" + pdfPages
					+ " PDF pages, " + images + " images; at most " + MAX_PAGES_PER_ANALYSIS + " pages and "
					+ MAX_IMAGES_PER_ANALYSIS + " images per upload). Upload it in several parts.");
		}
	}

	/** Retry after a failed analysis. */
	public CourseDtos.CourseDetail analyze(UUID courseId) {
		access.course(courseId);
		if (files.findByCourseIdAndAnalyzedFalseOrderByUploadedAt(courseId).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "All material is already analyzed.");
		}
		startAnalysis(courseId);
		return detail(courseId);
	}

	private void startAnalysis(UUID courseId) {
		tx.executeWithoutResult(status -> {
			Course course = access.course(courseId);
			if (course.getStatus() == Course.Status.ANALYZING) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is already being analyzed.");
			}
			course.setStatus(Course.Status.ANALYZING, null);
		});
		jobs.run("analysis " + courseId, () -> runAnalysis(courseId));
	}

	/** Background job: the Claude call runs outside any transaction (it takes minutes). */
	void runAnalysis(UUID courseId) {
		Course course = courses.findById(courseId).orElse(null);
		if (course == null) return;
		List<SourceFile> pending = files.findByCourseIdAndAnalyzedFalseOrderByUploadedAt(courseId);
		List<String> existing = lectures.findByCourseIdOrderByPosition(courseId).stream().map(Lecture::getTitle).toList();
		try {
			List<PlannedLecture> planned = ai.planLectures(course.getTitle(), pending, existing);
			tx.executeWithoutResult(status -> {
				int position = lectures.countByCourseId(courseId);
				for (PlannedLecture lecture : planned) {
					lectures.save(new Lecture(courseId, position++, lecture.title(), lecture.summary(),
							json.writeValueAsString(lecture.sources())));
				}
				pending.forEach(file -> file.setAnalyzed(true));
				files.saveAll(pending);
				courses.findById(courseId).ifPresent(c -> c.setStatus(Course.Status.READY, null));
			});
		} catch (RuntimeException e) {
			log.warn("Analysis of course {} failed", courseId, e);
			String message = e instanceof StudyAi.AiException ? e.getMessage() : "Analysis failed: " + e.getMessage();
			tx.executeWithoutResult(status -> {
				// Keep Files API ids, so a retry doesn't upload again.
				files.saveAll(pending);
				courses.findById(courseId).ifPresent(c -> c.setStatus(Course.Status.FAILED, message));
			});
		}
	}

	private Map<UUID, CourseDtos.Progress> progress(List<Lecture> courseLectures) {
		Map<UUID, CourseDtos.Progress> progress = new HashMap<>();
		if (courseLectures.isEmpty()) return progress;
		for (Object[] row : pages.progressByLecture(courseLectures.stream().map(Lecture::getId).toList())) {
			progress.put((UUID) row[0], new CourseDtos.Progress(((Number) row[1]).intValue(), ((Number) row[2]).intValue()));
		}
		return progress;
	}
}
