package io.github.zzz1999.entityreskin.web.stats;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadStatsRegistryTest {

    private final DownloadStatsRegistry registry =
            new DownloadStatsRegistry(Clock.fixed(Instant.ofEpochSecond(1_000_000), ZoneOffset.UTC));

    @Test
    void aggregatesBytesDownloadsAndDistinctPlayers() {
        registry.record(1L, 100, "Alice", "aabbccdd");
        registry.record(1L, 250, "Bob", "11223344");
        registry.record(1L, 50, "Alice", "aabbccdd");

        DownloadStatsRegistry.Snapshot snapshot = registry.snapshot(1L);
        assertEquals(400, snapshot.totalBytes());
        assertEquals(3, snapshot.downloads());
        assertEquals(2, snapshot.activePlayers());
        assertEquals(3, snapshot.recentEvents().size());
        assertEquals("Alice", snapshot.recentEvents().get(0).playerName());
    }

    @Test
    void isolatesServersAndReportsEmptyForUnknown() {
        registry.record(1L, 100, "Alice", "aabbccdd");
        assertEquals(0, registry.snapshot(2L).downloads());
        assertTrue(registry.snapshot(2L).recentEvents().isEmpty());
    }

    @Test
    void countsAnonymousDownloadsWithoutAPlayerName() {
        registry.record(5L, 100, null, "deadbeef");
        DownloadStatsRegistry.Snapshot snapshot = registry.snapshot(5L);
        assertEquals(1, snapshot.downloads());
        assertEquals(0, snapshot.activePlayers());
    }
}
