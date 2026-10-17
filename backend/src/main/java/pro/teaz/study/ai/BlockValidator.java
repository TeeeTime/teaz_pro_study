package pro.teaz.study.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Checks what the schema can't: that every task can actually be solved and
 * checked by the frontend (indices in range, one gap per {{n}}, rows as wide as
 * the header, an answer for every cell to fill in). Structured outputs already
 * guarantee the shape; this guards the meaning before anything is stored.
 */
public final class BlockValidator {

	private static final Pattern GAP = Pattern.compile("\\{\\{(\\d+)}}");

	private BlockValidator() {
	}

	/** @return problems, empty when the page is fine */
	public static List<String> validatePage(JsonNode page) {
		List<String> problems = new ArrayList<>();
		if (page.path("title").asString("").isBlank()) problems.add("page without title");
		JsonNode blocks = page.path("blocks");
		if (!blocks.isArray() || blocks.isEmpty()) {
			problems.add("page '" + page.path("title").asString("") + "' has no blocks");
			return problems;
		}
		int index = 0;
		for (JsonNode block : blocks) {
			String where = "page '" + page.path("title").asString("") + "', block " + (index++ + 1) + ": ";
			validateBlock(block).forEach(problem -> problems.add(where + problem));
		}
		return problems;
	}

	static List<String> validateBlock(JsonNode block) {
		List<String> problems = new ArrayList<>();
		switch (block.path("type").asString("")) {
			case "text" -> {
				if (block.path("markdown").asString("").isBlank()) problems.add("empty text");
			}
			case "multiple_choice" -> {
				int options = block.path("options").size();
				if (options < 2) problems.add("fewer than 2 options");
				JsonNode correct = block.path("correct");
				if (correct.isEmpty()) problems.add("no correct option");
				for (JsonNode c : correct) {
					if (c.intValue() < 0 || c.intValue() >= options) problems.add("correct index out of range: " + c);
				}
			}
			case "cloze" -> {
				TreeSet<Integer> numbers = new TreeSet<>();
				Matcher matcher = GAP.matcher(block.path("text").asString(""));
				while (matcher.find()) numbers.add(Integer.parseInt(matcher.group(1)));
				int gaps = block.path("gaps").size();
				if (numbers.isEmpty()) problems.add("cloze without {{n}} gaps");
				else if (numbers.first() != 1 || numbers.last() != numbers.size() || numbers.size() != gaps) {
					problems.add("gaps {{1}}..{{n}} don't match the " + gaps + " gap answers");
				}
				for (JsonNode gap : block.path("gaps")) {
					if (!hasAnswer(gap)) problems.add("gap without answer");
				}
			}
			case "table" -> {
				int columns = block.path("columns").size();
				if (columns == 0) problems.add("table without columns");
				int toFill = 0;
				for (JsonNode row : block.path("rows")) {
					if (row.size() != columns) problems.add("row with " + row.size() + " cells for " + columns + " columns");
					for (JsonNode cell : row) {
						if (cell.path("given").isNull()) {
							toFill++;
							if (!hasAnswer(cell)) problems.add("cell to fill in without answer");
						}
					}
				}
				if (toFill == 0) problems.add("table with nothing to fill in");
			}
			default -> problems.add("unknown block type '" + block.path("type").asString("") + "'");
		}
		return problems;
	}

	private static boolean hasAnswer(JsonNode answerFields) {
		return !answerFields.path("answers").isEmpty() || answerFields.path("numeric").isNumber();
	}
}
