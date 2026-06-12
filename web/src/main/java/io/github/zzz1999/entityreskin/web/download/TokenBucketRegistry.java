package io.github.zzz1999.entityreskin.web.download;

import io.github.zzz1999.entityreskin.web.server.GameServer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One token bucket per registered server. A bucket is rebuilt when the server's configured rate
 * changes (tier upgrade), and removed when the server is deleted.
 */
@Component
public class TokenBucketRegistry {

    /** Floor for burst capacity so a single streaming chunk can always be consumed. */
    private static final long MINIMUM_CAPACITY_BYTES = 64 * 1024 * 2;

    private final Map<Long, TokenBucket> buckets = new ConcurrentHashMap<>();

    TokenBucket bucketFor(GameServer server) {
        return buckets.compute(server.getId(), (id, existing) -> {
            if (existing != null && existing.bytesPerSecond() == server.getBytesPerSecond()) {
                return existing;
            }
            long capacity = Math.max(server.getBytesPerSecond(), MINIMUM_CAPACITY_BYTES);
            return new TokenBucket(server.getBytesPerSecond(), capacity, System::nanoTime);
        });
    }

    public void remove(long serverId) {
        buckets.remove(serverId);
    }
}
