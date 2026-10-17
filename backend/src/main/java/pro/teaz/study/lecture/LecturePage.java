package pro.teaz.study.lecture;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** One clickable page of a lecture: info text mixed with tasks, see ai/AiSchemas. */
@Entity
public class LecturePage {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID lectureId;

	private int position;

	@Column(nullable = false)
	private String title;

	/** JSON list of blocks. */
	@Column(nullable = false)
	private String blocks;

	/** Read (info-only page) or all tasks solved correctly. Counts towards progress. */
	private boolean completed;

	protected LecturePage() {
	}

	public LecturePage(UUID lectureId, int position, String title, String blocks) {
		this.id = UUID.randomUUID();
		this.lectureId = lectureId;
		this.position = position;
		this.title = title;
		this.blocks = blocks;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLectureId() {
		return lectureId;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public String getTitle() {
		return title;
	}

	public String getBlocks() {
		return blocks;
	}

	/** New content: whatever was learned on the old version no longer counts. */
	public void replaceContent(String title, String blocks) {
		this.title = title;
		this.blocks = blocks;
		this.completed = false;
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}
}
