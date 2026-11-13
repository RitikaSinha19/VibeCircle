package com.vibecircle.story;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * vibecircle.stories.ttl-hours               how long a story is visible (default 24)
 * vibecircle.stories.retention-after-expiry  how long expired/deleted rows and files are kept before cleanup (default 1 hour)
 */
@ConfigurationProperties(prefix = "vibecircle.stories")
public record StoryProperties(
        @DefaultValue("24") int ttlHours,
        @DefaultValue("PT1H") Duration retentionAfterExpiry) {
}
