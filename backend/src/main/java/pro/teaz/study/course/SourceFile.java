package pro.teaz.study.course;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** One uploaded PDF or image of a course. The bytes live in FileStorage. */
@Entity
public class SourceFile {

	public static final String PDF = "application/pdf";

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID courseId;

	@Column(nullable = false)
	private String originalName;

	@Column(nullable = false)
	private String mediaType;

	private long sizeBytes;

	/** PDFs only. */
	private Integer pageCount;

	@Column(nullable = false)
	private String storagePath;

	/** Set once uploaded to Claude's Files API for the analysis. */
	private String anthropicFileId;

	/** Already split into lectures. */
	private boolean analyzed;

	@Column(nullable = false)
	private Instant uploadedAt;

	protected SourceFile() {
	}

	public SourceFile(UUID id, UUID courseId, String originalName, String mediaType, long sizeBytes,
			Integer pageCount, String storagePath) {
		this.id = id;
		this.courseId = courseId;
		this.originalName = originalName;
		this.mediaType = mediaType;
		this.sizeBytes = sizeBytes;
		this.pageCount = pageCount;
		this.storagePath = storagePath;
		this.uploadedAt = Instant.now();
	}

	public boolean isPdf() {
		return PDF.equals(mediaType);
	}

	public UUID getId() {
		return id;
	}

	public UUID getCourseId() {
		return courseId;
	}

	public String getOriginalName() {
		return originalName;
	}

	public String getMediaType() {
		return mediaType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Integer getPageCount() {
		return pageCount;
	}

	public String getStoragePath() {
		return storagePath;
	}

	public String getAnthropicFileId() {
		return anthropicFileId;
	}

	public void setAnthropicFileId(String anthropicFileId) {
		this.anthropicFileId = anthropicFileId;
	}

	public boolean isAnalyzed() {
		return analyzed;
	}

	public void setAnalyzed(boolean analyzed) {
		this.analyzed = analyzed;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}
}
