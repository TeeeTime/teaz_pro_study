package pro.teaz.study.lecture;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LectureController {

	private final LectureService service;

	public LectureController(LectureService service) {
		this.service = service;
	}

	@GetMapping("/api/lectures/{id}")
	public LectureDtos.LectureDetail detail(@PathVariable UUID id) {
		return service.detail(id);
	}

	/** Starts generating the pages; poll the lecture until pagesStatus is READY or FAILED. */
	@PostMapping("/api/lectures/{id}/generate")
	public LectureDtos.LectureDetail generate(@PathVariable UUID id) {
		return service.generate(id);
	}

	@PostMapping("/api/lectures/{id}/reset-progress")
	public LectureDtos.LectureDetail resetProgress(@PathVariable UUID id) {
		service.resetProgress(id);
		return service.detail(id);
	}

	@PutMapping("/api/pages/{id}/completed")
	public ResponseEntity<Void> completed(@PathVariable UUID id, @RequestBody LectureDtos.CompletedRequest request) {
		service.setCompleted(id, request.completed());
		return ResponseEntity.noContent().build();
	}
}
