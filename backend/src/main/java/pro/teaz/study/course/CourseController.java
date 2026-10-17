package pro.teaz.study.course;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class CourseController {

	private final CourseService service;

	public CourseController(CourseService service) {
		this.service = service;
	}

	/** For other Teaz apps (Portal's Study tile): courses and progress in percent. CORS-enabled, see SecurityConfig. */
	@GetMapping("/overview")
	public CourseDtos.Overview overview() {
		return service.overview();
	}

	@GetMapping("/courses")
	public List<CourseDtos.CourseSummary> list() {
		return service.list();
	}

	@PostMapping("/courses")
	public CourseDtos.CourseDetail create(@RequestBody CourseDtos.TitleRequest request) {
		return service.create(request.title());
	}

	@GetMapping("/courses/{id}")
	public CourseDtos.CourseDetail detail(@PathVariable UUID id) {
		return service.detail(id);
	}

	@PatchMapping("/courses/{id}")
	public CourseDtos.CourseDetail rename(@PathVariable UUID id, @RequestBody CourseDtos.TitleRequest request) {
		service.rename(id, request.title());
		return service.detail(id);
	}

	@DeleteMapping("/courses/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	/** Multipart field "files", one or more PDFs/images. Starts the analysis. */
	@PostMapping("/courses/{id}/files")
	public CourseDtos.CourseDetail upload(@PathVariable UUID id, @RequestParam("files") List<MultipartFile> files) {
		return service.upload(id, files);
	}

	/** Retry a failed analysis. */
	@PostMapping("/courses/{id}/analyze")
	public CourseDtos.CourseDetail analyze(@PathVariable UUID id) {
		return service.analyze(id);
	}
}
