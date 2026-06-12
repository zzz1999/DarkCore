package io.github.zzz1999.entityreskin.web.stats;

import io.github.zzz1999.entityreskin.web.server.GameServerService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-scoped live download statistics for a server. A frontend chart polls this endpoint; the
 * caller may only read statistics for servers it owns (enforced by {@link GameServerService}).
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final DownloadStatsRegistry registry;
    private final GameServerService gameServers;

    public StatsController(DownloadStatsRegistry registry, GameServerService gameServers) {
        this.registry = registry;
        this.gameServers = gameServers;
    }

    @GetMapping("/servers/{serverId}")
    public DownloadStatsRegistry.Snapshot serverStats(@PathVariable long serverId,
                                                      Authentication authentication) {
        gameServers.requireOwned(authentication.getName(), serverId);
        return registry.snapshot(serverId);
    }
}
