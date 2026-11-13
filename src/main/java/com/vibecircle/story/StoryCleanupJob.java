package com.vibecircle.story;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs every 15 minutes by default; override with vibecircle.stories.cleanup-cron. */
@Component
public class StoryCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StoryCleanupJob.class);
    private static final int MAX_BATCHES_PER_RUN = 50;

    private final StoryCleanupService service;

    public StoryCleanupJob(StoryCleanupService service) {
        this.service = service;
    }

    @Scheduled(cron = "${vibecircle.stories.cleanup-cron:0 */15 * * * *}")
    public int run() {
        int total = 0;
        try {
            for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
                int removed = service.removeBatch();
                total += removed;
                if (removed < StoryCleanupService.BATCH_SIZE) {
                    break;
                }
            }
            if (total > 0) {
                log.info("Story cleanup removed {} expired stories", total);
            }
        } catch (RuntimeException e) {
            log.warn("Story cleanup failed; it will retry on the next run", e);
        }
        return total;
    }
}
