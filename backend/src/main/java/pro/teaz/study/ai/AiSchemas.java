package pro.teaz.study.ai;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON schemas Claude's output must follow (structured outputs / strict tools).
 *
 * The "sandbox": every page is a list of blocks, and a block is one of a fixed
 * set of types that the frontend can render and check on its own (BlockView.vue).
 * Claude picks whichever mix fits the material; a new task type means a new
 * schema here, a check in BlockValidator and a component in the frontend.
 * Practice exercises are meant to reuse exactly these blocks.
 *
 * Built in code rather than as $ref'd JSON so the chat's proposal tool can embed
 * the page schema directly. Strict schemas need every property required and
 * additionalProperties false; optional values are "x or null".
 */
public final class AiSchemas {

	private AiSchemas() {
	}

	private static Map<String, Object> object(Map<String, Object> properties) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", List.copyOf(properties.keySet()));
		schema.put("additionalProperties", false);
		return schema;
	}

	private static Map<String, Object> props(Object... keysAndSchemas) {
		Map<String, Object> props = new LinkedHashMap<>();
		for (int i = 0; i < keysAndSchemas.length; i += 2) {
			props.put((String) keysAndSchemas[i], keysAndSchemas[i + 1]);
		}
		return props;
	}

	private static Map<String, Object> type(String type, String description) {
		return Map.of("type", type, "description", description);
	}

	private static Map<String, Object> nullable(String type, String description) {
		return Map.of("type", List.of(type, "null"), "description", description);
	}

	private static Map<String, Object> array(Object items, String description) {
		return Map.of("type", "array", "items", items, "description", description);
	}

	private static Map<String, Object> constant(String value) {
		return Map.of("type", "string", "enum", List.of(value));
	}

	/** An answer field the frontend compares against: accepted strings and/or a number with tolerance. */
	private static Object[] answerFields() {
		return new Object[] {
				"answers", array(Map.of("type", "string"),
						"Accepted answers, compared trimmed and case-insensitively. May be empty when numeric is set."),
				"numeric", nullable("number", "For number answers: the correct value; the input is parsed as a number "
						+ "(comma or dot as decimal separator). Null for text answers."),
				"tolerance", nullable("number", "Allowed absolute deviation for numeric, e.g. 0.01. Null = exact."),
		};
	}

	private static Object[] concat(Object[]... parts) {
		return Arrays.stream(parts).flatMap(Arrays::stream).toArray();
	}

	static Map<String, Object> textBlock() {
		return object(props(
				"type", constant("text"),
				"markdown", type("string", "Explanation in Markdown. Math in LaTeX: $...$ inline, $$...$$ on its own line.")));
	}

	static Map<String, Object> multipleChoiceBlock() {
		return object(props(
				"type", constant("multiple_choice"),
				"question", type("string", "Markdown with LaTeX."),
				"options", array(Map.of("type", "string"), "2 to 6 options, Markdown with LaTeX."),
				"correct", array(Map.of("type", "integer"),
						"0-based indices of all correct options; more than one means 'select all that apply'."),
				"explanation", type("string", "Shown after answering: why the right options are right.")));
	}

	static Map<String, Object> clozeBlock() {
		Map<String, Object> gap = object(props(answerFields()));
		return object(props(
				"type", constant("cloze"),
				"instruction", type("string", "What to do, Markdown with LaTeX."),
				"text", type("string", "Markdown with LaTeX and gaps written as {{1}}, {{2}}, ... in order. "
						+ "Gaps can sit inside a calculation, e.g. '$x_1 =$ {{1}}'. Never put a gap inside $...$."),
				"gaps", array(gap, "One entry per gap: gaps[0] is {{1}}, gaps[1] is {{2}}, ..."),
				"explanation", type("string", "Shown after answering: the solution path.")));
	}

	static Map<String, Object> tableBlock() {
		Map<String, Object> cell = object(props(concat(
				new Object[] {"given", nullable("string",
						"Fixed content shown as is (Markdown with LaTeX); null = the learner fills this cell in.")},
				answerFields())));
		return object(props(
				"type", constant("table"),
				"instruction", type("string", "What to fill in, Markdown with LaTeX."),
				"columns", array(Map.of("type", "string"), "Column headers."),
				"rows", array(array(cell, "One cell per column."),
						"For tableaus (e.g. simplex), truth tables, value tables, matching. "
								+ "Given cells have answers [] and numeric null."),
				"explanation", type("string", "Shown after answering: how the table is filled in.")));
	}

	static Map<String, Object> page() {
		return object(props(
				"title", type("string", "Short page title."),
				"blocks", array(Map.of("anyOf", List.of(textBlock(), multipleChoiceBlock(), clozeBlock(), tableBlock())),
						"The page's content, top to bottom.")));
	}

	/** Output of the page generation. */
	public static Map<String, Object> pages() {
		return object(props("pages", array(page(), "The lecture's pages, in learning order.")));
	}

	/** Output of the analysis. */
	public static Map<String, Object> plan() {
		Map<String, Object> source = object(props(
				"file", type("integer", "Number of the file as listed in the request."),
				"fromPage", nullable("integer", "First PDF page (1-based); null for images or the whole file."),
				"toPage", nullable("integer", "Last PDF page, inclusive; null for images or the whole file.")));
		Map<String, Object> lecture = object(props(
				"title", type("string", "Lecture title, in German."),
				"summary", type("string", "2-3 sentences on what the lecture covers, in German."),
				"sources", array(source, "The material this lecture is made of.")));
		return object(props("lectures", array(lecture, "Lectures in teaching order.")));
	}

	/** Input of the chat's propose_page_changes tool. */
	public static Map<String, Object> proposal() {
		Map<String, Object> operation = object(props(
				"op", Map.of("type", "string", "enum", List.of("replace", "insert", "delete")),
				"position", type("integer", "0-based page position, in the page list as it is after the previous "
						+ "operations. insert puts the new page at this position (= page count to append)."),
				"page", Map.of("anyOf", List.of(page(), Map.of("type", "null")),
						"description", "The new page for replace and insert; null for delete.")));
		return object(props(
				"summary", type("string", "One or two sentences for the learner: what changes and why. In German."),
				"operations", array(operation, "Applied one after another.")));
	}
}
