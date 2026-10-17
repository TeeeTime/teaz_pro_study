package pro.teaz.study.lecture;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class Lecture {

	public enum PagesStatus {
		/** Planned from the material; pages are generated on first open. */
		NOT_GENERATED,
		GENERATING,
		READY,
		/** Generation failed, see statusMessage; can be retried. */
		FAILED
	}

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID courseId;

	private int position;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String summary;

	/** JSON list of {@link LectureSource}. */
	@Column(nullable = false)
	private String sources;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PagesStatus pagesStatus;

	private String statusMessage;

	@Column(nullable = false)
	private Instant createdAt;

	protected Lecture() {
	}

	public Lecture(UUID courseId, int position, String title, String summary, String sources) {
		this.id = UUID.randomUUID();
		this.courseId = courseId;
		this.position = position;
		this.title = title;
		this.summary = summary;
		this.sources = sources;
		this.pagesStatus = PagesStatus.NOT_GENERATED;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getCourseId() {
		return courseId;
	}

	public int getPosition() {
		return position;
	}

	public String getTitle() {
		return title;
	}

	public String getSummary() {
		return summary;
	}

	public String getSources() {
		return sources;
	}

	public PagesStatus getPagesStatus() {
		return pagesStatus;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setPagesStatus(PagesStatus pagesStatus, String statusMessage) {
		this.pagesStatus = pagesStatus;
		this.statusMessage = statusMessage;
	}
}
