package com.vibecircle.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters in memory: simple, fast and correct for ONE server instance.
 * When you run several instances, swap this class for a Redis-backed one (same method) so the limits are shared.
 */
@Component
public class RateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds, int remaining) {
    }

    private record Window(long start, long lengthMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public Decision tryAcquire(String key, int limit, Duration window) {
        return tryAcquire(key, limit, window.toMillis(), System.currentTimeMillis());
    }

    Decision tryAcquire(String key, int limit, long windowMillis, long nowMillis) {
        Decision[] result = new Decision[1];
        windows.compute(key, (k, w) -> {
            if (w == null || nowMillis - w.start() >= windowMillis) {
                result[0] = new Decision(true, 0, limit - 1);
                return new Window(nowMillis, windowMillis, 1);
            }
            if (w.count() >= limit) {
                long retry = Math.max(1, (w.start() + windowMillis - nowMillis + 999) / 1000);
                result[0] = new Decision(false, retry, 0);
                return w;
            }
            result[0] = new Decision(true, 0, limit - w.count() - 1);
            return new Window(w.start(), w.lengthMillis(), w.count() + 1);
        });
        return result[0];
    }

    @Scheduled(fixedDelay = 60_000)
    void purgeExpired() {
        purge(System.currentTimeMillis());
    }

    void purge(long nowMillis) {
        windows.values().removeIf(w -> nowMillis - w.start() >= w.lengthMillis());
    }

    int trackedKeys() {
        return windows.size();
    }
}
