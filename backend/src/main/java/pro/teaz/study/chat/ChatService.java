package pro.teaz.study.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import pro.teaz.study.ai.StudyAi;
import pro.teaz.study.ai.StudyAi.ChatReply;
import pro.teaz.study.ai.StudyAi.ChatTurn;
import pro.teaz.study.course.Access;
import pro.teaz.study.course.CourseRepository;
import pro.teaz.study.lecture.Lecture;
import pro.teaz.study.lecture.LectureMaterial;
import pro.teaz.study.lecture.LecturePage;
import pro.teaz.study.lecture.LecturePageRepository;

/**
 * The per-lecture chat. Only the visible text of each turn is stored, plus the
 * proposal; every request rebuilds the conversation from that and sends the
 * lecture's current pages along. So applied proposals never need replaying,
 * and nothing depends on keeping Claude's raw turns (thinking blocks etc.).
 */
@Service
public class ChatService {

	private final Access access;
	private final CourseRepository courses;
	private final ChatMessageRepository messages;
	private final LecturePageRepository pages;
	private final LectureMaterial material;
	private final StudyAi ai;
	private final TransactionTemplate tx;
	private final JsonMapper json;

	public ChatService(Access access, CourseRepository courses, ChatMessageRepository messages,
			LecturePageRepository pages, LectureMaterial material, StudyAi ai, TransactionTemplate tx, JsonMapper json) {
		this.access = access;
		this.courses = courses;
		this.messages = messages;
		this.pages = pages;
		this.material = material;
		this.ai = ai;
		this.tx = tx;
		this.json = json;
	}

	public List<ChatDtos.MessageView> list(UUID lectureId) {
		access.lecture(lectureId);
		return messages.findByLectureIdOrderByCreatedAt(lectureId).stream().map(this::view).toList();
	}

	/** Synchronous: a chat answer takes seconds, not minutes. Both turns are stored only on success. */
	public List<ChatDtos.MessageView> send(UUID lectureId, String text) {
		if (text == null || text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty message.");
		Lecture lecture = access.lecture(lectureId);
		if (lecture.getPagesStatus() != Lecture.PagesStatus.READY) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "The chat starts once the lecture's pages exist.");
		}
		String courseTitle = courses.findById(lecture.getCourseId()).orElseThrow().getTitle();
		List<ChatTurn> history = messages.findByLectureIdOrderByCreatedAt(lectureId).stream()
				.map(message -> new ChatTurn(message.getRole() == ChatMessage.Role.USER, historyText(message)))
				.toList();

		ChatReply reply = ai.chat(new StudyAi.ChatRequest(courseTitle, lecture.getTitle(), material.parts(lecture),
				currentPages(lectureId), history, text.strip()));

		return tx.execute(status -> {
			ChatMessage question = messages.save(new ChatMessage(lectureId, ChatMessage.Role.USER, text.strip(), null));
			ChatMessage answer = messages.save(new ChatMessage(lectureId, ChatMessage.Role.ASSISTANT,
					reply.text().isBlank() ? "(no text)" : reply.text(),
					reply.proposal() == null ? null : json.writeValueAsString(reply.proposal())));
			return List.of(view(question), view(answer));
		});
	}

	/** What Claude sees of an earlier turn: its text, and for proposals what became of them. */
	private String historyText(ChatMessage message) {
		if (message.getProposal() == null) return message.getText();
		String summary = json.readTree(message.getProposal()).path("summary").asString("");
		return message.getText() + "\n\n[Proposed page changes: " + summary + " Status: "
				+ message.getProposalStatus().name().toLowerCase() + "]";
	}

	private ArrayNode currentPages(UUID lectureId) {
		ArrayNode list = json.createArrayNode();
		for (LecturePage page : pages.findByLectureIdOrderByPosition(lectureId)) {
			ObjectNode node = list.addObject();
			node.put("title", page.getTitle());
			node.set("blocks", json.readTree(page.getBlocks()));
		}
		return list;
	}

	/**
	 * Applies the operations in order. Positions refer to the page list as it is
	 * after the previous operation, as the tool description tells Claude.
	 */
	public ChatDtos.MessageView apply(UUID messageId) {
		return tx.execute(status -> {
			ChatMessage message = pendingProposal(messageId);
			List<LecturePage> list = new ArrayList<>(pages.findByLectureIdOrderByPosition(message.getLectureId()));
			List<LecturePage> removed = new ArrayList<>();
			for (JsonNode operation : json.readTree(message.getProposal()).path("operations")) {
				int position = operation.path("position").intValue();
				JsonNode page = operation.path("page");
				switch (operation.path("op").asString("")) {
					case "replace" -> {
						checkPosition(position, list.size() - 1);
						list.get(position).replaceContent(page.path("title").asString(),
								json.writeValueAsString(page.path("blocks")));
					}
					case "insert" -> {
						checkPosition(position, list.size());
						list.add(position, new LecturePage(message.getLectureId(), position,
								page.path("title").asString(), json.writeValueAsString(page.path("blocks"))));
					}
					case "delete" -> {
						checkPosition(position, list.size() - 1);
						removed.add(list.remove(position));
					}
					default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Unknown operation in proposal.");
				}
			}
			pages.deleteAll(removed);
			for (int i = 0; i < list.size(); i++) list.get(i).setPosition(i);
			pages.saveAll(list);
			message.setProposalStatus(ChatMessage.ProposalStatus.APPLIED);
			return view(message);
		});
	}

	public ChatDtos.MessageView discard(UUID messageId) {
		return tx.execute(status -> {
			ChatMessage message = pendingProposal(messageId);
			message.setProposalStatus(ChatMessage.ProposalStatus.DISCARDED);
			return view(message);
		});
	}

	private ChatMessage pendingProposal(UUID messageId) {
		ChatMessage message = messages.findById(messageId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found."));
		access.lecture(message.getLectureId());
		if (message.getProposalStatus() != ChatMessage.ProposalStatus.PENDING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This proposal was already applied or discarded.");
		}
		return message;
	}

	private static void checkPosition(int position, int max) {
		if (position < 0 || position > max) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"The proposal no longer fits the lecture's pages. Ask again in the chat.");
		}
	}

	private ChatDtos.MessageView view(ChatMessage message) {
		return new ChatDtos.MessageView(message.getId(), message.getRole(), message.getText(),
				message.getProposal() == null ? null : json.readTree(message.getProposal()),
				message.getProposalStatus(), message.getCreatedAt());
	}
}
