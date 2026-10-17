package pro.teaz.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pro.teaz.study.security.SecurityConfig;

/**
 * The whole learning flow against MockStudyAi, with the real prod security:
 * create a course, upload a PDF, analysis, pages on demand, progress, chat with
 * a proposal that gets applied. Jobs run inline (SyncJobs).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
@Import(SyncJobs.class)
class StudyFlowTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	private static JwtRequestPostProcessor user(String id) {
		return jwt().jwt(token -> token.subject(id))
				.authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.USER_ROLE));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private static byte[] pdf(int pages) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (int i = 0; i < pages; i++) document.addPage(new PDPage());
			document.save(out);
			return out.toByteArray();
		}
	}

	@Test
	void courseFromUploadToAppliedChatProposal() throws Exception {
		JsonNode course = body(mvc.perform(post("/api/courses").with(user("alice"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"Operations Research\"}"))
				.andExpect(status().isOk()));
		String courseId = course.path("id").asString();

		// 25 pages: the mock plans lectures of 20 pages, so two lectures.
		JsonNode analyzed = body(mvc.perform(multipart("/api/courses/" + courseId + "/files")
				.file(new MockMultipartFile("files", "skript.pdf", "application/pdf", pdf(25)))
				.with(user("alice")))
				.andExpect(status().isOk()));
		assertThat(analyzed.path("status").asString()).isEqualTo("READY");
		assertThat(analyzed.path("files").get(0).path("pageCount").intValue()).isEqualTo(25);
		assertThat(analyzed.path("files").get(0).path("analyzed").booleanValue()).isTrue();
		assertThat(analyzed.path("lectures").size()).isEqualTo(2);
		String lectureId = analyzed.path("lectures").get(0).path("id").asString();
		assertThat(analyzed.path("lectures").get(0).path("pagesStatus").asString()).isEqualTo("NOT_GENERATED");

		// Pages on demand.
		JsonNode lecture = body(mvc.perform(post("/api/lectures/" + lectureId + "/generate").with(user("alice")))
				.andExpect(status().isOk()));
		assertThat(lecture.path("pagesStatus").asString()).isEqualTo("READY");
		assertThat(lecture.path("pages").size()).isEqualTo(4);
		assertThat(lecture.path("nextLectureId").isNull()).isFalse();
		mvc.perform(post("/api/lectures/" + lectureId + "/generate").with(user("alice"))).andExpect(status().isConflict());

		// Progress.
		String firstPage = lecture.path("pages").get(0).path("id").asString();
		mvc.perform(put("/api/pages/" + firstPage + "/completed").with(user("alice"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"completed\": true}"))
			.andExpect(status().isNoContent());
		JsonNode progress = body(mvc.perform(get("/api/courses/" + courseId).with(user("alice"))))
				.path("lectures").get(0).path("progress");
		assertThat(progress.path("completed").intValue()).isEqualTo(1);
		assertThat(progress.path("total").intValue()).isEqualTo(4);

		// Overview for other apps: lecture 1 at 1/4, lecture 2 not generated (0 %) -> 12 %.
		JsonNode overview = body(mvc.perform(get("/api/overview").with(user("alice"))).andExpect(status().isOk()));
		assertThat(overview.path("courses").size()).isEqualTo(1);
		assertThat(overview.path("courses").get(0).path("title").asString()).isEqualTo("Operations Research");
		assertThat(overview.path("courses").get(0).path("percent").intValue()).isEqualTo(12);

		// Chat: the mock proposes an extra page when asked for more practice.
		JsonNode turn = body(mvc.perform(post("/api/lectures/" + lectureId + "/chat").with(user("alice"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Bitte mehr Übungen\"}"))
				.andExpect(status().isOk()));
		JsonNode answer = turn.get(1);
		assertThat(answer.path("role").asString()).isEqualTo("ASSISTANT");
		assertThat(answer.path("proposalStatus").asString()).isEqualTo("PENDING");

		mvc.perform(post("/api/chat/" + answer.path("id").asString() + "/apply").with(user("alice")))
			.andExpect(status().isOk());
		JsonNode changed = body(mvc.perform(get("/api/lectures/" + lectureId).with(user("alice"))));
		assertThat(changed.path("pages").size()).isEqualTo(5);
		assertThat(changed.path("pages").get(4).path("title").asString()).isEqualTo("Zusatzübung");
		// Applying twice is refused.
		mvc.perform(post("/api/chat/" + answer.path("id").asString() + "/apply").with(user("alice")))
			.andExpect(status().isConflict());
	}

	@Test
	void coursesArePrivate() throws Exception {
		String courseId = body(mvc.perform(post("/api/courses").with(user("bob"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"Bobs Kurs\"}")))
				.path("id").asString();

		mvc.perform(get("/api/courses/" + courseId).with(user("mallory"))).andExpect(status().isNotFound());
		assertThat(body(mvc.perform(get("/api/courses").with(user("mallory")))).size()).isZero();
		assertThat(body(mvc.perform(get("/api/courses").with(user("bob")))).size()).isEqualTo(1);
	}

	@Test
	void rejectsUnsupportedFiles() throws Exception {
		String courseId = body(mvc.perform(post("/api/courses").with(user("carol"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"Kurs\"}")))
				.path("id").asString();

		mvc.perform(multipart("/api/courses/" + courseId + "/files")
				.file(new MockMultipartFile("files", "notes.docx", "application/msword", new byte[] {1}))
				.with(user("carol")))
			.andExpect(status().isBadRequest());
		mvc.perform(multipart("/api/courses/" + courseId + "/files")
				.file(new MockMultipartFile("files", "kaputt.pdf", "application/pdf", "no pdf".getBytes()))
				.with(user("carol")))
			.andExpect(status().isBadRequest());
	}
}
