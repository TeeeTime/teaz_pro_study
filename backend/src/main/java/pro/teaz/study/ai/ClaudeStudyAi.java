package pro.teaz.study.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.MultipartField;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.files.FileUploadParams;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.FileDocumentSource;
import com.anthropic.models.messages.FileImageSource;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import pro.teaz.study.StudyProperties;
import pro.teaz.study.course.SourceFile;
import pro.teaz.study.storage.FileStorage;

/**
 * Study's Claude API calls. Three kinds:
 * - planLectures: analysis model (Opus) reads all new material via the Files API
 *   and returns a lecture plan (structured output).
 * - writePages: content model (Sonnet) gets one lecture's cut-out pages inline
 *   and returns its study pages (structured output).
 * - chat: content model answers, and may call propose_page_changes once.
 *
 * All calls stream (long outputs) and opt into server-side refusal fallbacks.
 */
public class ClaudeStudyAi implements StudyAi {

	private static final Logger log = LoggerFactory.getLogger(ClaudeStudyAi.class);

	private static final long MAX_TOKENS = 64_000L;
	private static final String PROPOSAL_TOOL = "propose_page_changes";
	/** Request limit is 32 MB including base64 overhead (x4/3); leave headroom. */
	private static final long MAX_INLINE_BYTES = 22L * 1024 * 1024;

	private final AnthropicClient client;
	private final StudyProperties.Ai config;
	private final FileStorage storage;
	private final JsonMapper json;

	public ClaudeStudyAi(AnthropicClient client, StudyProperties.Ai config, FileStorage storage, JsonMapper json) {
		this.client = client;
		this.config = config;
		this.storage = storage;
		this.json = json;
	}

	// --- lecture plan ---

	@Override
	public List<PlannedLecture> planLectures(String courseTitle, List<SourceFile> files, List<String> existingLectures) {
		List<ContentBlockParam> content = new ArrayList<>();
		StringBuilder listing = new StringBuilder("Course: " + courseTitle + "\n\nFiles:\n");
		for (int i = 0; i < files.size(); i++) {
			SourceFile file = files.get(i);
			String label = "File " + (i + 1) + ": " + file.getOriginalName();
			String fileId = ensureUploaded(file);
			if (file.isPdf()) {
				listing.append(label).append(" (PDF, ").append(file.getPageCount()).append(" pages)\n");
				content.add(ContentBlockParam.ofDocument(DocumentBlockParam.builder()
						.source(FileDocumentSource.builder().fileId(fileId).build())
						.title(label)
						.build()));
			} else {
				listing.append(label).append(" (image)\n");
				content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(label).build()));
				content.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
						.source(FileImageSource.builder().fileId(fileId).build())
						.build()));
			}
		}
		listing.append(existingLectures.isEmpty()
				? "\nThe course has no lectures yet."
				: "\nExisting lectures, in order:\n" + existingLectures.stream()
						.map(title -> "- " + title).collect(Collectors.joining("\n")));
		listing.append("\n\nSplit the files into lectures.");
		content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(listing.toString()).build()));

		MessageCreateParams params = base(config.analysisModel(), Prompts.PLAN)
				.outputConfig(structured(OutputConfig.Effort.HIGH, AiSchemas.plan()))
				.addUserMessageOfBlockParams(content)
				.build();
		JsonNode plan = parse(text(stream(params)));

		List<PlannedLecture> lectures = new ArrayList<>();
		for (JsonNode lecture : plan.path("lectures")) {
			List<LectureSource> sources = new ArrayList<>();
			for (JsonNode source : lecture.path("sources")) {
				LectureSource checked = checkSource(files, source);
				if (checked != null) sources.add(checked);
			}
			if (sources.isEmpty()) {
				log.warn("Dropping planned lecture without valid sources: {}", lecture.path("title").asString(""));
				continue;
			}
			lectures.add(new PlannedLecture(lecture.path("title").asString(), lecture.path("summary").asString(), sources));
		}
		if (lectures.isEmpty()) throw new AiException("Claude found no lectures in the material.");
		return lectures;
	}

	/** File numbers and page ranges come from the model: clamp them to what exists. */
	private static LectureSource checkSource(List<SourceFile> files, JsonNode source) {
		int index = source.path("file").intValue() - 1;
		if (index < 0 || index >= files.size()) return null;
		SourceFile file = files.get(index);
		if (!file.isPdf() || source.path("fromPage").isNull()) return new LectureSource(file.getId(), null, null);
		int pages = file.getPageCount();
		int from = Math.clamp(source.path("fromPage").intValue(), 1, pages);
		int to = source.path("toPage").isNull() ? pages : Math.clamp(source.path("toPage").intValue(), 1, pages);
		return new LectureSource(file.getId(), Math.min(from, to), Math.max(from, to));
	}

	/** Files API: the analysis references big uploads by id instead of inlining them. */
	private String ensureUploaded(SourceFile file) {
		if (file.getAnthropicFileId() != null) return file.getAnthropicFileId();
		try (InputStream in = Files.newInputStream(storage.resolve(file.getStoragePath()))) {
			String id = client.files().upload(FileUploadParams.builder()
					.file(MultipartField.<InputStream>builder()
							.value(in)
							.filename(file.getOriginalName())
							.contentType(file.getMediaType())
							.build())
					.build()).id();
			file.setAnthropicFileId(id);
			return id;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// --- pages ---

	@Override
	public List<PageDraft> writePages(String courseTitle, String lectureTitle, String lectureSummary,
			List<MaterialPart> material) {
		List<ContentBlockParam> content = new ArrayList<>(materialBlocks(material, false));
		content.add(ContentBlockParam.ofText(TextBlockParam.builder()
				.text("Course: " + courseTitle + "\nLecture: " + lectureTitle + "\nCovers: " + lectureSummary
						+ "\n\nWrite this lecture's study pages from the material above.")
				.build()));

		MessageCreateParams params = base(config.contentModel(), Prompts.PAGES)
				.outputConfig(structured(OutputConfig.Effort.HIGH, AiSchemas.pages()))
				.addUserMessageOfBlockParams(content)
				.build();
		JsonNode output = parse(text(stream(params)));

		List<PageDraft> pages = new ArrayList<>();
		for (JsonNode page : output.path("pages")) {
			JsonNode cleaned = dropInvalidBlocks(page);
			if (cleaned != null) pages.add(new PageDraft(cleaned.path("title").asString(), cleaned.path("blocks")));
		}
		if (pages.isEmpty()) throw new AiException("Claude returned no usable pages for this lecture.");
		return pages;
	}

	/**
	 * A broken task (e.g. a gap without answer) would be unsolvable; drop it rather
	 * than the whole lecture. A page left without blocks is dropped too.
	 */
	private JsonNode dropInvalidBlocks(JsonNode page) {
		ArrayNode kept = json.createArrayNode();
		for (JsonNode block : page.path("blocks")) {
			List<String> problems = BlockValidator.validateBlock(block);
			if (problems.isEmpty()) kept.add(block);
			else log.warn("Dropping invalid block on page '{}': {}", page.path("title").asString(""), problems);
		}
		if (kept.isEmpty()) return null;
		ObjectNode cleaned = json.createObjectNode();
		cleaned.put("title", page.path("title").asString(""));
		cleaned.set("blocks", kept);
		return cleaned;
	}

	// --- chat ---

	@Override
	public ChatReply chat(ChatRequest request) {
		List<MessageParam> messages = new ArrayList<>();
		// The material first and marked for caching: it's the same on every turn of this chat.
		List<ContentBlockParam> intro = new ArrayList<>(materialBlocks(request.material(), true));
		intro.add(ContentBlockParam.ofText(TextBlockParam.builder()
				.text("Course: " + request.courseTitle() + "\nLecture: " + request.lectureTitle())
				.build()));
		messages.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(intro).build());
		for (ChatTurn turn : request.history()) {
			messages.add(MessageParam.builder()
					.role(turn.fromUser() ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
					.content(turn.text())
					.build());
		}
		messages.add(MessageParam.builder().role(MessageParam.Role.USER)
				.content("Current pages of the lecture (JSON):\n" + json.writeValueAsString(request.currentPages())
						+ "\n\n" + request.userText())
				.build());

		StringBuilder reply = new StringBuilder();
		JsonNode proposal = null;
		// At most: reply with a proposal, one retry for an invalid proposal, final text.
		for (int round = 0; round < 3; round++) {
			Message message = stream(base(config.contentModel(), Prompts.CHAT)
					.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
					.addTool(proposalTool())
					.messages(messages)
					.build());
			append(reply, text(message));
			if (!StopReason.TOOL_USE.equals(message.stopReason().orElse(null))) break;

			messages.add(message.toParam());
			List<ContentBlockParam> results = new ArrayList<>();
			for (ContentBlock block : message.content()) {
				if (block.toolUse().isEmpty()) continue;
				ToolUseBlock toolUse = block.toolUse().get();
				JsonNode input = json.valueToTree(toolUse._input().convert(Map.class));
				List<String> problems = new ArrayList<>();
				for (JsonNode operation : input.path("operations")) {
					if (!operation.path("page").isNull()) problems.addAll(BlockValidator.validatePage(operation.path("page")));
				}
				String result;
				if (proposal != null) {
					result = "Only one proposal per reply; this one was ignored.";
				} else if (!problems.isEmpty()) {
					result = "Invalid proposal, nothing was shown. Fix and call again:\n- " + String.join("\n- ", problems);
				} else {
					proposal = input;
					result = "The proposal is shown to the learner, who will apply or discard it. "
							+ "Finish your reply briefly; don't repeat the pages.";
				}
				results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
						.toolUseId(toolUse.id())
						.content(result)
						.isError(!problems.isEmpty())
						.build()));
			}
			messages.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build());
		}
		return new ChatReply(reply.toString().strip(), proposal);
	}

	private Tool proposalTool() {
		Map<String, Object> schema = AiSchemas.proposal();
		Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
		((Map<?, ?>) schema.get("properties")).forEach((key, value) ->
				properties.putAdditionalProperty((String) key, JsonValue.from(value)));
		return Tool.builder()
				.name(PROPOSAL_TOOL)
				.description("Propose changes to this lecture's pages. The learner sees the proposal and applies or "
						+ "discards it. Give complete pages for replace and insert. Positions are 0-based.")
				.inputSchema(Tool.InputSchema.builder()
						.properties(properties.build())
						.required(List.of("summary", "operations"))
						.putAdditionalProperty("additionalProperties", JsonValue.from(false))
						.build())
				.strict(true)
				.build();
	}

	// --- shared ---

	private MessageCreateParams.Builder base(String model, String system) {
		return MessageCreateParams.builder()
				.model(model)
				.maxTokens(MAX_TOKENS)
				.systemOfTextBlockParams(List.of(TextBlockParam.builder()
						.text(system)
						.cacheControl(CacheControlEphemeral.builder().build())
						.build()))
				// On a policy decline, the API re-runs the request on a fallback model.
				.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
				.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
	}

	private static OutputConfig structured(OutputConfig.Effort effort, Map<String, Object> schema) {
		JsonOutputFormat.Schema.Builder format = JsonOutputFormat.Schema.builder();
		schema.forEach((key, value) -> format.putAdditionalProperty(key, JsonValue.from(value)));
		return OutputConfig.builder()
				.effort(effort)
				.format(JsonOutputFormat.builder().schema(format.build()).build())
				.build();
	}

	/** PDFs and images inline (base64); a lecture's cut-out pages are small enough. */
	private List<ContentBlockParam> materialBlocks(List<MaterialPart> material, boolean cacheLast) {
		long total = material.stream().mapToLong(part -> part.data().length).sum();
		if (total > MAX_INLINE_BYTES) {
			throw new AiException("This lecture's material is too large to send (" + (total / 1024 / 1024)
					+ " MB). Split it into smaller files and upload them again.");
		}
		List<ContentBlockParam> blocks = new ArrayList<>();
		for (int i = 0; i < material.size(); i++) {
			MaterialPart part = material.get(i);
			String data = Base64.getEncoder().encodeToString(part.data());
			CacheControlEphemeral cache = cacheLast && i == material.size() - 1 ? CacheControlEphemeral.builder().build() : null;
			if (SourceFile.PDF.equals(part.mediaType())) {
				DocumentBlockParam.Builder doc = DocumentBlockParam.builder()
						.source(Base64PdfSource.builder().data(data).build())
						.title(part.label());
				if (cache != null) doc.cacheControl(cache);
				blocks.add(ContentBlockParam.ofDocument(doc.build()));
			} else {
				blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(part.label()).build()));
				ImageBlockParam.Builder image = ImageBlockParam.builder()
						.source(Base64ImageSource.builder()
								.data(data)
								.mediaType(Base64ImageSource.MediaType.of(part.mediaType()))
								.build());
				if (cache != null) image.cacheControl(cache);
				blocks.add(ContentBlockParam.ofImage(image.build()));
			}
		}
		return blocks;
	}

	private Message stream(MessageCreateParams params) {
		MessageAccumulator accumulator = MessageAccumulator.create();
		try (StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params)) {
			response.stream().forEach(accumulator::accumulate);
		}
		Message message = accumulator.message();
		StopReason stop = message.stopReason().orElse(null);
		if (StopReason.REFUSAL.equals(stop)) {
			throw new AiException("Claude declined to process this material.");
		}
		if (StopReason.MAX_TOKENS.equals(stop)) {
			throw new AiException("Claude's answer got too long and was cut off. Try smaller lectures.");
		}
		log.info("Claude {}: {} in / {} out tokens (cache read {})", params.model(),
				message.usage().inputTokens(), message.usage().outputTokens(),
				message.usage().cacheReadInputTokens().orElse(0L));
		return message;
	}

	private static String text(Message message) {
		return message.content().stream()
				.flatMap(block -> block.text().stream())
				.map(TextBlock::text)
				.collect(Collectors.joining());
	}

	private static void append(StringBuilder reply, String text) {
		if (text.isBlank()) return;
		if (!reply.isEmpty()) reply.append("\n\n");
		reply.append(text.strip());
	}

	private JsonNode parse(String text) {
		try {
			return json.readTree(text);
		} catch (RuntimeException e) {
			throw new AiException("Claude returned invalid JSON.", e);
		}
	}
}
