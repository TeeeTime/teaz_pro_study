package pro.teaz.study.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import tools.jackson.databind.json.JsonMapper;

import pro.teaz.study.StudyProperties;
import pro.teaz.study.storage.FileStorage;

/**
 * Picks the AI: Claude when study.ai.api-key is set (ANTHROPIC_API_KEY),
 * otherwise the mock, like the frontend's mock auth and Pulse's mock Docker.
 */
@Configuration
public class AiConfig {

	private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

	@Bean
	StudyAi studyAi(StudyProperties properties, FileStorage storage, JsonMapper json) {
		StudyProperties.Ai ai = properties.ai();
		if (!ai.enabled()) {
			log.info("No ANTHROPIC_API_KEY: using MockStudyAi (canned lectures and pages)");
			return new MockStudyAi(json);
		}
		log.info("Using Claude: {} for analysis, {} for pages and chat", ai.analysisModel(), ai.contentModel());
		return new ClaudeStudyAi(AnthropicOkHttpClient.builder().apiKey(ai.apiKey()).build(), ai, storage, json);
	}
}
