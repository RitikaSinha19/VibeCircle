package com.vibecircle.call;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Caps signalling messages per (call, user): a real call needs a few dozen (offer, answer, ICE candidates),
 * so 300 per minute only stops floods. In memory, so it is per server instance.
 */
@Component
public class SignalRateLimiter {

    static final int LIMIT_PER_WINDOW = 300;
    static final long WINDOW_MILLIS = 60_000;
    private static final long STALE_MILLIS = 10 * 60_000;

    private record Window(long start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long callId, Long userId) {
        return tryAcquire(callId, userId, System.currentTimeMillis());
    }

    boolean tryAcquire(Long callId, Long userId, long nowMillis) {
        boolean[] allowed = {false};
        windows.compute(callId + ":" + userId, (key, w) -> {
            if (w == null || nowMillis - w.start() >= WINDOW_MILLIS) {
                allowed[0] = true;
                return new Window(nowMillis, 1);
            }
            if (w.count() >= LIMIT_PER_WINDOW) {
                return w;
            }
            allowed[0] = true;
            return new Window(w.start(), w.count() + 1);
        });
        return allowed[0];
    }

    public void forget(Long callId) {
        windows.keySet().removeIf(key -> key.startsWith(callId + ":"));
    }

    @Scheduled(fixedDelay = 300_000)
    void purgeStale() {
        purge(System.currentTimeMillis());
    }

    void purge(long nowMillis) {
        windows.values().removeIf(w -> nowMillis - w.start() > STALE_MILLIS);
    }

    int trackedWindows() {
        return windows.size();
    }
}
