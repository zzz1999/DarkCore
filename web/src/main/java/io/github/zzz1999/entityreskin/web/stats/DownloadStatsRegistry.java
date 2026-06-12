package io.github.zzz1999.entityreskin.web.stats;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory, per-server download statistics for a live (polled) dashboard: cumulative bytes and
 * download count, a bounded ring of recent events, and the set of players seen within a recent
 * window. No raw personal information is persisted — only a self-reported player name and a
 * salted IP-hash prefix are kept, in memory. A scheduled sweep evicts inactive servers and stale
 * players so memory stays bounded even if an owner never polls. Single-instance MVP; persisted
 * aggregates (bytes/counts only) and a distributed store are future work.
 */
@Component
public class DownloadStatsRegistry {

    public record Event(long epochMillis, String playerName, String ipHashPrefix, long bytes) {
    }

    public record Snapshot(long totalBytes, long downloads, int activePlayers, List<Event> recentEvents) {
    }

    private static final int MAX_RECENT_EVENTS = 100;
    private static final long ACTIVE_WINDOW_MILLIS = 5 * 60 * 1000L;
    private static final long SERVER_RETENTION_MILLIS = 60 * 60 * 1000L;

    private static final class ServerStats {
        final AtomicLong totalBytes = new AtomicLong();
        final AtomicLong downloads = new AtomicLong();
        final Deque<Event> recent = new ConcurrentLinkedDeque<>();
        final AtomicLong recentSize = new AtomicLong();
        final Map<String, Long> playerLastSeenMillis = new ConcurrentHashMap<>();
        volatile long lastRecordMillis;
    }

    private final Map<Long, ServerStats> perServer = new ConcurrentHashMap<>();
    private final Clock clock;

    public DownloadStatsRegistry(Clock clock) {
        this.clock = clock;
    }

    public void record(long serverId, long bytes, String playerName, String ipHashPrefix) {
        ServerStats stats = perServer.computeIfAbsent(serverId, id -> new ServerStats());
        stats.totalBytes.addAndGet(bytes);
        stats.downloads.incrementAndGet();
        long now = clock.millis();
        stats.lastRecordMillis = now;
        stats.recent.addFirst(new Event(now, playerName, ipHashPrefix, bytes));
        if (stats.recentSize.incrementAndGet() > MAX_RECENT_EVENTS && stats.recent.pollLast() != null) {
            stats.recentSize.decrementAndGet();
        }
        if (playerName != null) {
            stats.playerLastSeenMillis.put(playerName, now);
        }
    }

    public Snapshot snapshot(long serverId) {
        ServerStats stats = perServer.get(serverId);
        if (stats == null) {
            return new Snapshot(0, 0, 0, List.of());
        }
        long now = clock.millis();
        stats.playerLastSeenMillis.values().removeIf(seen -> now - seen > ACTIVE_WINDOW_MILLIS);
        return new Snapshot(stats.totalBytes.get(), stats.downloads.get(),
                stats.playerLastSeenMillis.size(), new ArrayList<>(stats.recent));
    }

    /**
     * Evicts servers with no downloads within the retention window and clears stale players from
     * the rest, bounding memory regardless of whether owners poll their statistics.
     */
    @Scheduled(fixedDelayString = "${entityreskin.stats.sweep-interval-millis:600000}")
    public void sweep() {
        long now = clock.millis();
        perServer.entrySet().removeIf(entry -> {
            ServerStats stats = entry.getValue();
            if (now - stats.lastRecordMillis > SERVER_RETENTION_MILLIS) {
                return true;
            }
            stats.playerLastSeenMillis.values().removeIf(seen -> now - seen > ACTIVE_WINDOW_MILLIS);
            return false;
        });
    }
}
