package pro.teaz.study;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import pro.teaz.study.jobs.Jobs;

/** Runs background jobs inline, so tests see their result right after the request. */
@TestConfiguration
public class SyncJobs {

	@Bean
	@Primary
	Jobs syncJobs() {
		return new Jobs() {
			@Override
			public void run(String name, Runnable job) {
				job.run();
			}
		};
	}
}
