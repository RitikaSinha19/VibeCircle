package com.vibecircle.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SignalRateLimiterTest {

    private static final long T0 = 1_000_000L;

    @Test
    void allowsTheLimitThenBlocksUntilTheWindowPasses() {
        SignalRateLimiter limiter = new SignalRateLimiter();
        for (int i = 0; i < SignalRateLimiter.LIMIT_PER_WINDOW; i++) {
            assertThat(limiter.tryAcquire(1L, 1L, T0 + i)).as("message %d", i).isTrue();
        }
        assertThat(limiter.tryAcquire(1L, 1L, T0 + 1000)).isFalse();
        assertThat(limiter.tryAcquire(1L, 1L, T0 + SignalRateLimiter.WINDOW_MILLIS + 1)).isTrue();
    }

    @Test
    void countsPerCallAndPerUserIndependently() {
        SignalRateLimiter limiter = new SignalRateLimiter();
        for (int i = 0; i < SignalRateLimiter.LIMIT_PER_WINDOW; i++) {
            limiter.tryAcquire(1L, 1L, T0);
        }
        assertThat(limiter.tryAcquire(1L, 1L, T0)).isFalse();
        assertThat(limiter.tryAcquire(1L, 2L, T0)).as("other user, same call").isTrue();
        assertThat(limiter.tryAcquire(2L, 1L, T0)).as("same user, other call").isTrue();
    }

    @Test
    void forgettingACallFreesItsCounters() {
        SignalRateLimiter limiter = new SignalRateLimiter();
        limiter.tryAcquire(7L, 1L, T0);
        limiter.tryAcquire(7L, 2L, T0);
        limiter.tryAcquire(8L, 1L, T0);
        limiter.forget(7L);
        assertThat(limiter.trackedWindows()).isEqualTo(1);
    }

    @Test
    void staleWindowsArePurged() {
        SignalRateLimiter limiter = new SignalRateLimiter();
        limiter.tryAcquire(1L, 1L, T0);
        limiter.purge(T0 + 11 * 60_000);
        assertThat(limiter.trackedWindows()).isZero();
    }
}
