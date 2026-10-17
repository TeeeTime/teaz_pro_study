package pro.teaz.study.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class BlockValidatorTests {

	private final JsonMapper json = JsonMapper.builder().build();

	private List<String> problems(String block) {
		return BlockValidator.validateBlock(json.readTree(block));
	}

	@Test
	void acceptsWellFormedTasks() {
		assertThat(problems("""
				{"type": "multiple_choice", "question": "?", "options": ["a", "b"], "correct": [1], "explanation": ""}
				""")).isEmpty();
		assertThat(problems("""
				{"type": "cloze", "instruction": "", "text": "$x =$ {{1}} und {{2}}", "explanation": "",
				 "gaps": [{"answers": [], "numeric": 3, "tolerance": null}, {"answers": ["ja"], "numeric": null, "tolerance": null}]}
				""")).isEmpty();
	}

	@Test
	void catchesUnsolvableTasks() {
		assertThat(problems("""
				{"type": "multiple_choice", "question": "?", "options": ["a", "b"], "correct": [2], "explanation": ""}
				""")).anyMatch(p -> p.contains("out of range"));
		assertThat(problems("""
				{"type": "cloze", "instruction": "", "text": "{{1}} {{3}}", "explanation": "",
				 "gaps": [{"answers": ["a"], "numeric": null, "tolerance": null}, {"answers": ["b"], "numeric": null, "tolerance": null}]}
				""")).isNotEmpty();
		assertThat(problems("""
				{"type": "cloze", "instruction": "", "text": "{{1}}", "explanation": "",
				 "gaps": [{"answers": [], "numeric": null, "tolerance": null}]}
				""")).anyMatch(p -> p.contains("without answer"));
		assertThat(problems("""
				{"type": "table", "instruction": "", "explanation": "", "columns": ["a", "b"],
				 "rows": [[{"given": "1", "answers": [], "numeric": null, "tolerance": null}]]}
				""")).anyMatch(p -> p.contains("row with 1 cells"));
		assertThat(problems("{\"type\": \"essay\"}")).anyMatch(p -> p.contains("unknown block type"));
	}

	/** Local dev shows the mock's pages: they must be valid, solvable examples of every block type. */
	@Test
	void mockPagesAreValid() {
		for (StudyAi.PageDraft page : new MockStudyAi(json).writePages("Kurs", "Vorlesung", "", List.of())) {
			var node = json.createObjectNode().put("title", page.title()).set("blocks", page.blocks());
			assertThat(BlockValidator.validatePage(node)).as(page.title()).isEmpty();
		}
	}

	/** Strict structured outputs reject schemas where an object allows extra or optional properties. */
	@Test
	void schemasAreStrict() {
		for (Map<String, Object> schema : List.of(AiSchemas.pages(), AiSchemas.plan(), AiSchemas.proposal())) {
			assertStrict(schema);
		}
	}

	@SuppressWarnings("unchecked")
	private static void assertStrict(Object node) {
		if (node instanceof Map<?, ?> map) {
			if ("object".equals(map.get("type"))) {
				assertThat(map.get("additionalProperties")).isEqualTo(false);
				assertThat((List<String>) map.get("required"))
						.containsExactlyInAnyOrderElementsOf(((Map<String, Object>) map.get("properties")).keySet());
			}
			map.values().forEach(BlockValidatorTests::assertStrict);
		} else if (node instanceof List<?> list) {
			list.forEach(BlockValidatorTests::assertStrict);
		}
	}
}
