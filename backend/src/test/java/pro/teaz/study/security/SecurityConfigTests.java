package pro.teaz.study.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class SecurityConfigTests {

	@Autowired
	MockMvc mvc;

	@Test
	void rejectsRequestWithoutToken() throws Exception {
		mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
	}

	@Test
	void needsStudyUserRole() throws Exception {
		mvc.perform(get("/api/courses").with(jwt())).andExpect(status().isForbidden());
		mvc.perform(get("/api/courses")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.USER_ROLE))))
			.andExpect(status().isOk());
	}
}
