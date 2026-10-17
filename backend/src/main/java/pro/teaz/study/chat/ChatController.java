package pro.teaz.study.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

	private final ChatService service;

	public ChatController(ChatService service) {
		this.service = service;
	}

	@GetMapping("/api/lectures/{id}/chat")
	public List<ChatDtos.MessageView> list(@PathVariable UUID id) {
		return service.list(id);
	}

	/** @return the stored question and answer */
	@PostMapping("/api/lectures/{id}/chat")
	public List<ChatDtos.MessageView> send(@PathVariable UUID id, @RequestBody ChatDtos.SendRequest request) {
		return service.send(id, request.text());
	}

	@PostMapping("/api/chat/{messageId}/apply")
	public ChatDtos.MessageView apply(@PathVariable UUID messageId) {
		return service.apply(messageId);
	}

	@PostMapping("/api/chat/{messageId}/discard")
	public ChatDtos.MessageView discard(@PathVariable UUID messageId) {
		return service.discard(messageId);
	}
}
