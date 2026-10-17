package pro.teaz.study.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import pro.teaz.study.course.SourceFile;

/**
 * Local-dev stand-in for {@link ClaudeStudyAi}: no network, no cost. One lecture
 * per file (PDFs in chunks of 20 pages) and the same sample pages for every
 * lecture, covering every block type, so the whole UI can be built and tried
 * without an API key. Used whenever study.ai.api-key is unset.
 */
public class MockStudyAi implements StudyAi {

	private static final int PAGES_PER_LECTURE = 20;

	private final JsonMapper json;

	public MockStudyAi(JsonMapper json) {
		this.json = json;
	}

	@Override
	public List<PlannedLecture> planLectures(String courseTitle, List<SourceFile> files, List<String> existingLectures) {
		List<PlannedLecture> lectures = new ArrayList<>();
		for (SourceFile file : files) {
			String name = file.getOriginalName().replaceFirst("\\.[^.]+$", "");
			if (!file.isPdf()) {
				lectures.add(new PlannedLecture(name, "Inhalte aus dem Bild " + file.getOriginalName() + ".",
						List.of(new LectureSource(file.getId(), null, null))));
				continue;
			}
			int pages = file.getPageCount();
			for (int from = 1; from <= pages; from += PAGES_PER_LECTURE) {
				int to = Math.min(pages, from + PAGES_PER_LECTURE - 1);
				String title = pages <= PAGES_PER_LECTURE ? name : name + " (S. " + from + "-" + to + ")";
				lectures.add(new PlannedLecture(title,
						"Beispielvorlesung aus " + file.getOriginalName() + ", Seiten " + from + " bis " + to
								+ ". Die echten Inhalte erzeugt Claude, sobald ein API-Key gesetzt ist.",
						List.of(new LectureSource(file.getId(), from, to))));
			}
		}
		return lectures;
	}

	@Override
	public List<PageDraft> writePages(String courseTitle, String lectureTitle, String lectureSummary,
			List<MaterialPart> material) {
		return List.of(
				page("Worum es geht", """
						[{"type": "text", "markdown": "Dies ist eine **Beispielseite** für *%s*. Ohne API-Key erzeugt Study keine echten Inhalte, zeigt aber alle Aufgabentypen.\\n\\nFormeln werden mit LaTeX gesetzt, etwa $e^{i\\\\pi} + 1 = 0$ oder abgesetzt:\\n\\n$$\\\\sum_{k=1}^{n} k = \\\\frac{n(n+1)}{2}$$"}]
						""".formatted(lectureTitle)),
				page("Kurz geprüft", """
						[{"type": "text", "markdown": "Ein lineares Programm in Standardform maximiert $c^T x$ unter $Ax \\\\le b$, $x \\\\ge 0$."},
						 {"type": "multiple_choice", "question": "Welche Aussagen gelten für die Standardform?", "options": ["Alle Variablen sind nichtnegativ", "Die Zielfunktion wird minimiert", "Die Nebenbedingungen sind $\\\\le$-Ungleichungen", "Es gibt genau eine Nebenbedingung"], "correct": [0, 2], "explanation": "Standardform: $\\\\max c^T x$, $Ax \\\\le b$, $x \\\\ge 0$."}]
						"""),
				page("Rechnen mit Lücken", """
						[{"type": "cloze", "instruction": "Berechne die Summe der ersten $n$ natürlichen Zahlen.", "text": "Für $n = 10$ ist $\\\\sum_{k=1}^{10} k =$ {{1}}. Die Formel heißt nach {{2}}.", "gaps": [{"answers": [], "numeric": 55, "tolerance": null}, {"answers": ["Gauß", "Gauss"], "numeric": null, "tolerance": null}], "explanation": "$\\\\frac{10 \\\\cdot 11}{2} = 55$, bekannt als Gaußsche Summenformel."}]
						"""),
				page("Simplex-Tableau", """
						[{"type": "text", "markdown": "Starttableau für $\\\\max\\\\ 3x_1 + 2x_2$ unter $x_1 + x_2 \\\\le 4$, $x_1 + 3x_2 \\\\le 6$."},
						 {"type": "table", "instruction": "Fülle die fehlenden Einträge des Starttableaus aus.", "columns": ["Basis", "$x_1$", "$x_2$", "$s_1$", "$s_2$", "RS"],
						  "rows": [
						   [{"given": "$s_1$", "answers": [], "numeric": null, "tolerance": null}, {"given": "1", "answers": [], "numeric": null, "tolerance": null}, {"given": "1", "answers": [], "numeric": null, "tolerance": null}, {"given": "1", "answers": [], "numeric": null, "tolerance": null}, {"given": "0", "answers": [], "numeric": null, "tolerance": null}, {"given": null, "answers": [], "numeric": 4, "tolerance": null}],
						   [{"given": "$s_2$", "answers": [], "numeric": null, "tolerance": null}, {"given": null, "answers": [], "numeric": 1, "tolerance": null}, {"given": null, "answers": [], "numeric": 3, "tolerance": null}, {"given": "0", "answers": [], "numeric": null, "tolerance": null}, {"given": "1", "answers": [], "numeric": null, "tolerance": null}, {"given": "6", "answers": [], "numeric": null, "tolerance": null}],
						   [{"given": "$z$", "answers": [], "numeric": null, "tolerance": null}, {"given": null, "answers": [], "numeric": -3, "tolerance": null}, {"given": null, "answers": [], "numeric": -2, "tolerance": null}, {"given": "0", "answers": [], "numeric": null, "tolerance": null}, {"given": "0", "answers": [], "numeric": null, "tolerance": null}, {"given": "0", "answers": [], "numeric": null, "tolerance": null}]
						  ],
						  "explanation": "Die Zeilen übernehmen die Koeffizienten der Nebenbedingungen, die Zielfunktionszeile die negierten Zielkoeffizienten."}]
						"""));
	}

	@Override
	public ChatReply chat(ChatRequest request) {
		String question = request.userText().toLowerCase(Locale.ROOT);
		boolean wantsChange = question.contains("übung") || question.contains("aufgabe") || question.contains("mehr");
		if (!wantsChange) {
			return new ChatReply("(Mock) Ohne API-Key kann ich nur so tun: Du hast gefragt: *"
					+ request.userText() + "*. Frag nach **mehr Übungen**, um einen Änderungsvorschlag zu sehen.", null);
		}
		JsonNode proposal = json.readTree("""
				{"summary": "Eine zusätzliche Übungsseite am Ende (Mock).",
				 "operations": [{"op": "insert", "position": %d, "page": %s}]}
				""".formatted(request.currentPages().size(), json.writeValueAsString(pageJson(page("Zusatzübung", """
				[{"type": "cloze", "instruction": "Rechne nach.", "text": "$2^{10} =$ {{1}}", "gaps": [{"answers": [], "numeric": 1024, "tolerance": null}], "explanation": "$2^{10} = 1024$."}]
				""")))));
		return new ChatReply("(Mock) Hier ist ein Vorschlag mit einer zusätzlichen Übungsseite.", proposal);
	}

	private PageDraft page(String title, String blocks) {
		return new PageDraft(title, json.readTree(blocks));
	}

	private ObjectNode pageJson(PageDraft page) {
		ObjectNode node = json.createObjectNode();
		node.put("title", page.title());
		node.set("blocks", page.blocks());
		return node;
	}
}
