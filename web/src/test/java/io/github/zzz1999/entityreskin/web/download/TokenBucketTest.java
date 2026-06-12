package io.github.zzz1999.entityreskin.web.download;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenBucketTest {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @Test
    void consumptionWithinCapacityIncursNoDelay() {
        AtomicLong nanos = new AtomicLong();
        TokenBucket bucket = new TokenBucket(1000, 1000, nanos::get);
        assertEquals(0, bucket.consumeAndComputeDelayMillis(1000));
    }

    @Test
    void debtIsRepaidAtTheConfiguredRate() {
        AtomicLong nanos = new AtomicLong();
        TokenBucket bucket = new TokenBucket(1000, 1000, nanos::get);
        bucket.consumeAndComputeDelayMillis(1000);
        // 500 bytes of debt at 1000 B/s requires a 500 ms delay.
        assertEquals(500, bucket.consumeAndComputeDelayMillis(500));
        // After one simulated second the bucket has refilled past zero.
        nanos.addAndGet(NANOS_PER_SECOND);
        assertEquals(0, bucket.consumeAndComputeDelayMillis(500));
    }

    @Test
    void refillIsCappedAtCapacity() {
        AtomicLong nanos = new AtomicLong();
        TokenBucket bucket = new TokenBucket(1000, 1000, nanos::get);
        bucket.consumeAndComputeDelayMillis(1000);
        // Ten idle seconds must not accumulate more than one capacity of burst.
        nanos.addAndGet(10 * NANOS_PER_SECOND);
        assertEquals(0, bucket.consumeAndComputeDelayMillis(1000));
        assertEquals(1, bucket.consumeAndComputeDelayMillis(1));
    }
}
