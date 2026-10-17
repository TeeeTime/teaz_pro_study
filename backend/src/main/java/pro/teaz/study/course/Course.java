package pro.teaz.study.course;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class Course {

	public enum Status {
		/** Nothing running; lectures (if any) can be opened. */
		READY,
		/** New material is being split into lectures. */
		ANALYZING,
		/** Last analysis failed, see statusMessage; can be retried. */
		FAILED
	}

	@Id
	private UUID id;

	@Column(nullable = false)
	private String ownerId;

	@Column(nullable = false)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	private String statusMessage;

	@Column(nullable = false)
	private Instant createdAt;

	protected Course() {
	}

	public Course(String ownerId, String title) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
		this.title = title;
		this.status = Status.READY;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Status getStatus() {
		return status;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setStatus(Status status, String statusMessage) {
		this.status = status;
		this.statusMessage = statusMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
