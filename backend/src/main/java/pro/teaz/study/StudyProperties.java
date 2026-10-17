package pro.teaz.study;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code study.*}, see application.properties.
 *
 * @param storageDir where uploaded material is kept
 * @param ai         Claude API access and models
 * @param cors       other Teaz apps allowed to read /api/overview from the browser
 */
@ConfigurationProperties("study")
public record StudyProperties(Path storageDir, @DefaultValue Ai ai, @DefaultValue Cors cors) {

	public record Cors(@DefaultValue List<String> allowedOrigins) {
	}

	/**
	 * @param apiKey         unset or blank = MockStudyAi
	 * @param analysisModel  splits material into lectures
	 * @param contentModel   writes pages, answers the chat
	 */
	public record Ai(
			String apiKey,
			@DefaultValue("claude-opus-5-5") String analysisModel,
			@DefaultValue("claude-sonnet-5-5") String contentModel) {

		public boolean enabled() {
			return apiKey != null && !apiKey.isBlank();
		}
	}
}
