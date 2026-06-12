package io.github.zzz1999.entityreskin.server.backend;

import io.github.zzz1999.entityreskin.protocol.codec.Hex;
import io.github.zzz1999.entityreskin.protocol.packet.SetManifestSource;
import io.github.zzz1999.entityreskin.server.EntityReskinPlugin;
import io.github.zzz1999.entityreskin.server.PluginSettings;
import io.github.zzz1999.entityreskin.server.net.PluginMessageGateway;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodically polls the backend for the current manifest SHA-256. A changed hash means either
 * the server owner updated appearances or the signing window rolled over; both require
 * re-pinning every handshaked client via {@code SET_MANIFEST_SOURCE}. Polling runs off the main
 * thread; broadcasts are handed back to it.
 */
public final class ManifestHashPoller {

    private static final long TICKS_PER_SECOND = 20L;
    private static final long INITIAL_DELAY_TICKS = 20L;

    private final EntityReskinPlugin plugin;
    private final PluginSettings settings;
    private final BackendClient client;
    private final PluginMessageGateway gateway;

    private BukkitTask task;
    private volatile String lastHash;
    private volatile int consecutiveFailures;

    public ManifestHashPoller(EntityReskinPlugin plugin, PluginSettings settings,
                              BackendClient client, PluginMessageGateway gateway) {
        this.plugin = plugin;
        this.settings = settings;
        this.client = client;
        this.gateway = gateway;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                poll();
            }
        }, INITIAL_DELAY_TICKS, settings.pollIntervalSeconds() * TICKS_PER_SECOND);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Triggers one immediate asynchronous poll (used by the reload command). */
    public void pollNow() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                poll();
            }
        });
    }

    private void poll() {
        BackendClient.HashResponse response = client.fetchManifestSha256();
        switch (response.status()) {
            case OK:
                consecutiveFailures = 0;
                String hash = response.sha256Hex();
                if (!hash.equals(lastHash)) {
                    lastHash = hash;
                    final SetManifestSource source = new SetManifestSource(
                            settings.baseUrl(),
                            "api/manifest?srv=" + settings.serverToken(),
                            Hex.decode(hash));
                    plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            gateway.updateManifestSource(source);
                        }
                    });
                    plugin.getLogger().info("Manifest updated; re-sending to online clients (hash "
                            + hash.substring(0, 12) + "...).");
                }
                break;
            case NO_CONTENT:
                consecutiveFailures = 0;
                if (lastHash != null) {
                    lastHash = null;
                    plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            gateway.clearManifestSource();
                        }
                    });
                }
                break;
            case ERROR:
            default:
                consecutiveFailures++;
                if (consecutiveFailures == 1) {
                    plugin.getLogger().warning("Could not reach the resource backend " + settings.baseUrl()
                            + "; will retry on the next poll cycle.");
                }
                break;
        }
    }

    /** The most recent manifest hash, or {@code null} when the backend has none. */
    public String lastHashHex() {
        return lastHash;
    }
}
