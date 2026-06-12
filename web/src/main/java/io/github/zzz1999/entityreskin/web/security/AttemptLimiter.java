package io.github.zzz1999.entityreskin.web.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window attempt limiter for authentication endpoints (verification-code
 * brute force, registration flooding). Keys are caller-defined, for example
 * {@code "verify:" + email}. Sufficient for a single-instance MVP deployment; a distributed
 * store would replace it when the backend scales horizontally.
 */
@Component
public class AttemptLimiter {

    /** Each entry carries its own window length so cleanup honors per-key policies. */
    private record Window(long startEpochSeconds, long windowSeconds, int count) {
    }

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records an attempt for {@code key} and returns whether it is still within
     * {@code maxAttempts} per {@code window}.
     */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        long now = clock.instant().getEpochSecond();
        long windowSeconds = window.getSeconds();
        cleanupIfOversized(now);
        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startEpochSeconds() >= existing.windowSeconds()) {
                return new Window(now, windowSeconds, 1);
            }
            return new Window(existing.startEpochSeconds(), existing.windowSeconds(), existing.count() + 1);
        });
        return updated.count() <= maxAttempts;
    }

    private void cleanupIfOversized(long now) {
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().startEpochSeconds() >= e.getValue().windowSeconds());
        }
    }
}
