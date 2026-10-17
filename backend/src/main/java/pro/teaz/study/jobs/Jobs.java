package pro.teaz.study.jobs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Runs the long AI jobs (analysis, page generation) in the background; the UI
 * polls the course/lecture status. In-memory only: a restart drops running
 * jobs, and StaleJobReset marks them as failed so they can be retried.
 * Tests replace this with a synchronous version.
 */
@Component
public class Jobs implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(Jobs.class);

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/** @param job must handle its own failures (mark the entity as failed); this only logs leftovers */
	public void run(String name, Runnable job) {
		executor.submit(() -> {
			try {
				job.run();
			} catch (RuntimeException e) {
				log.error("Job {} failed", name, e);
			}
		});
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}
}
