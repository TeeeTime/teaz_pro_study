package pro.teaz.study;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.anthropic.errors.AnthropicException;

import pro.teaz.study.ai.StudyAi;

/** AI failures in synchronous calls (the chat) as 502 with a readable detail. */
@RestControllerAdvice
public class ApiErrors {

	private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

	@ExceptionHandler(StudyAi.AiException.class)
	public ProblemDetail ai(StudyAi.AiException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
	}

	@ExceptionHandler(AnthropicException.class)
	public ProblemDetail anthropic(AnthropicException e) {
		log.warn("Claude API call failed", e);
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"The Claude API is not available right now. Please try again in a moment.");
	}
}
