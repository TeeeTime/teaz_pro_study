package pro.teaz.study.chat;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** JSON shapes of the chat API. */
public final class ChatDtos {

	private ChatDtos() {
	}

	/** @param proposal {summary, operations} or null */
	public record MessageView(UUID id, ChatMessage.Role role, String text, JsonNode proposal,
			ChatMessage.ProposalStatus proposalStatus, Instant createdAt) {
	}

	public record SendRequest(String text) {
	}
}
