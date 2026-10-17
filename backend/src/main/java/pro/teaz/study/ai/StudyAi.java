package pro.teaz.study.ai;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

import pro.teaz.study.course.SourceFile;

/**
 * What Study needs from an AI. {@link ClaudeStudyAi} calls the Claude API,
 * {@link MockStudyAi} returns canned content for local dev and tests; see AiConfig.
 */
public interface StudyAi {

	/**
	 * Splits new material into lectures.
	 *
	 * @param files            the not yet analyzed files of the course; implementations may set
	 *                         {@link SourceFile#setAnthropicFileId} (the caller saves it)
	 * @param existingLectures titles of the course's lectures so far; new ones continue after them
	 */
	List<PlannedLecture> planLectures(String courseTitle, List<SourceFile> files, List<String> existingLectures);

	/** The learning pages of one lecture, from its part of the material. */
	List<PageDraft> writePages(String courseTitle, String lectureTitle, String lectureSummary,
			List<MaterialPart> material);

	/** One chat turn about a lecture; may propose changes to its pages. */
	ChatReply chat(ChatRequest request);

	/** @param fromPage, toPage 1-based, inclusive; null for images or whole files */
	record LectureSource(UUID fileId, Integer fromPage, Integer toPage) {
	}

	record PlannedLecture(String title, String summary, List<LectureSource> sources) {
	}

	/** @param blocks JSON array of blocks, see AiSchemas */
	record PageDraft(String title, JsonNode blocks) {
	}

	/** A piece of a lecture's material: a cut-out PDF or an image. */
	record MaterialPart(String label, String mediaType, byte[] data) {
	}

	/** One earlier turn; assistant turns mention their proposal and what the user did with it. */
	record ChatTurn(boolean fromUser, String text) {
	}

	/**
	 * @param currentPages JSON array of {title, blocks}, as the lecture is now
	 */
	record ChatRequest(String courseTitle, String lectureTitle, List<MaterialPart> material,
			JsonNode currentPages, List<ChatTurn> history, String userText) {
	}

	/** @param proposal JSON {summary, operations}, null when the reply only answers */
	record ChatReply(String text, JsonNode proposal) {
	}

	/** The AI call failed in a way worth showing to the user (refusal, too long, invalid output). */
	class AiException extends RuntimeException {
		public AiException(String message) {
			super(message);
		}

		public AiException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
