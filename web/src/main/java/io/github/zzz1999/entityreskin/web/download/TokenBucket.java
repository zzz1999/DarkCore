package io.github.zzz1999.entityreskin.web.download;

import java.util.function.LongSupplier;

/**
 * A byte-rate token bucket shared by all concurrent downloads of one server. Consumption uses a
 * debt model: a caller may take the bucket below zero and is told how long to wait before
 * writing, which yields a smooth aggregate rate without rejecting requests. The nanosecond time
 * source is injectable for deterministic tests.
 */
final class TokenBucket {

    private final long bytesPerSecond;
    private final long capacityBytes;
    private final LongSupplier nanoTimeSource;

    private double availableBytes;
    private long lastRefillNanos;

    TokenBucket(long bytesPerSecond, long capacityBytes, LongSupplier nanoTimeSource) {
        this.bytesPerSecond = bytesPerSecond;
        this.capacityBytes = capacityBytes;
        this.nanoTimeSource = nanoTimeSource;
        this.availableBytes = capacityBytes;
        this.lastRefillNanos = nanoTimeSource.getAsLong();
    }

    long bytesPerSecond() {
        return bytesPerSecond;
    }

    /**
     * Consumes {@code bytes} and returns the delay in milliseconds the caller must wait before
     * proceeding (zero when within budget).
     */
    synchronized long consumeAndComputeDelayMillis(long bytes) {
        refill();
        availableBytes -= bytes;
        if (availableBytes >= 0) {
            return 0;
        }
        return (long) Math.ceil(-availableBytes * 1000.0 / bytesPerSecond);
    }

    /** Consumes {@code bytes}, sleeping as required to honor the configured rate. */
    void consumeBlocking(long bytes) throws InterruptedException {
        long delayMillis = consumeAndComputeDelayMillis(bytes);
        if (delayMillis > 0) {
            Thread.sleep(delayMillis);
        }
    }

    private void refill() {
        long now = nanoTimeSource.getAsLong();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        availableBytes = Math.min(capacityBytes, availableBytes + elapsedSeconds * bytesPerSecond);
        lastRefillNanos = now;
    }
}
