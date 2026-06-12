package io.github.zzz1999.entityreskin.server.net;

import io.github.zzz1999.entityreskin.protocol.Capabilities;
import io.github.zzz1999.entityreskin.protocol.Channel;
import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import io.github.zzz1999.entityreskin.protocol.ProtocolVersion;
import io.github.zzz1999.entityreskin.protocol.packet.ClearIdentifier;
import io.github.zzz1999.entityreskin.protocol.packet.HandshakeRequest;
import io.github.zzz1999.entityreskin.protocol.packet.HandshakeResponse;
import io.github.zzz1999.entityreskin.protocol.packet.Packet;
import io.github.zzz1999.entityreskin.protocol.packet.PacketCodec;
import io.github.zzz1999.entityreskin.protocol.packet.Preload;
import io.github.zzz1999.entityreskin.protocol.packet.ResourceError;
import io.github.zzz1999.entityreskin.protocol.packet.ResourceReady;
import io.github.zzz1999.entityreskin.protocol.packet.SetIdentifier;
import io.github.zzz1999.entityreskin.protocol.packet.SetManifestSource;
import io.github.zzz1999.entityreskin.server.EntityReskinPlugin;
import io.github.zzz1999.entityreskin.server.PluginSettings;
import io.github.zzz1999.entityreskin.server.model.IdentifierRegistry;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plugin-messaging endpoint: registers the EntityReskin channel, tracks which players completed the
 * protocol handshake, and relays appearance state to them. Bukkit dispatches plugin messages
 * and events on the main thread, so entity lookups here are thread-safe; asynchronous callers
 * (the backend poller) hand off to the main thread before invoking broadcast methods.
 */
public final class PluginMessageGateway implements PluginMessageListener, Listener {

    private static final long SERVER_CAPABILITIES = Capabilities.GEOMETRY | Capabilities.ANIMATION;

    private final EntityReskinPlugin plugin;
    private final IdentifierRegistry registry;
    private final Set<UUID> handshaked =
            Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final AtomicReference<SetManifestSource> manifestSource =
            new AtomicReference<SetManifestSource>();
    private volatile List<String> preloadIdentifiers = Collections.emptyList();
    private String channel;

    private final Map<UUID, Set<String>> pendingPreload = new ConcurrentHashMap<UUID, Set<String>>();
    private final Map<UUID, Integer> preloadTotal = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, BukkitTask> preloadTimeouts = new ConcurrentHashMap<UUID, BukkitTask>();
    private final Map<UUID, BossBar> preloadBars = new ConcurrentHashMap<UUID, BossBar>();
    private volatile PluginSettings preloadConfig;

    public PluginMessageGateway(EntityReskinPlugin plugin, IdentifierRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /** Registers the namespaced messaging channel (required since 1.13). */
    public void register() {
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, Channel.MODERN);
        messenger.registerIncomingPluginChannel(plugin, Channel.MODERN, this);
        channel = Channel.MODERN;
        plugin.getLogger().info("Plugin messaging channel registered: " + channel);
    }

    /** Updates the preload list announced to clients after each handshake. */
    public void setPreload(List<String> identifiers) {
        this.preloadIdentifiers = identifiers;
    }

    /** Applies the preload progress/failure display configuration. */
    public void configurePreload(PluginSettings settings) {
        this.preloadConfig = settings;
    }

    @Override
    public void onPluginMessageReceived(String receivedChannel, Player player, byte[] message) {
        if (!receivedChannel.equals(channel)) {
            return;
        }
        Packet packet;
        try {
            packet = PacketCodec.decode(message);
        } catch (ProtocolException e) {
            return;
        }
        if (packet instanceof HandshakeRequest) {
            handleHandshake(player, (HandshakeRequest) packet);
        } else if (packet instanceof ResourceReady) {
            markPreloadResolved(player, ((ResourceReady) packet).identifier(), false);
        } else if (packet instanceof ResourceError) {
            ResourceError error = (ResourceError) packet;
            plugin.getLogger().warning("Client " + player.getName() + " failed to process resources: "
                    + error.identifier() + "(code " + error.reasonCode() + ")" + error.message());
            markPreloadResolved(player, error.identifier(), true);
        }
    }

    private void handleHandshake(Player player, HandshakeRequest request) {
        send(player, new HandshakeResponse(ProtocolVersion.CURRENT, SERVER_CAPABILITIES));
        if (request.protocolVersion() != ProtocolVersion.CURRENT) {
            plugin.getLogger().info("Player " + player.getName() + " client protocol version mismatch ("
                    + request.protocolVersion() + " != " + ProtocolVersion.CURRENT + "); both sides downgraded.");
            return;
        }
        handshaked.add(player.getUniqueId());
        SetManifestSource source = manifestSource.get();
        if (source != null) {
            send(player, source);
            List<String> preload = preloadIdentifiers;
            if (!preload.isEmpty()) {
                send(player, new Preload(preload));
                beginPreload(player, preload);
            }
        }
        syncRegistry(player);
    }

    /**
     * Sends the full registry. Entities not currently loaded are sent with the unknown-id
     * sentinel; the client applies them by UUID when the entity enters view.
     */
    private void syncRegistry(Player player) {
        for (Map.Entry<UUID, String> entry : registry.snapshot().entrySet()) {
            send(player, new SetIdentifier(resolveEntityId(entry.getKey()), entry.getKey(), entry.getValue()));
        }
    }

    public void broadcastSet(UUID entityUuid, String identifier) {
        SetIdentifier packet = new SetIdentifier(resolveEntityId(entityUuid), entityUuid, identifier);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (handshaked.contains(player.getUniqueId())) {
                send(player, packet);
            }
        }
    }

    public void broadcastClear(UUID entityUuid) {
        ClearIdentifier packet = new ClearIdentifier(resolveEntityId(entityUuid), entityUuid);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (handshaked.contains(player.getUniqueId())) {
                send(player, packet);
            }
        }
    }

    /** Stores the new manifest source and re-pins every handshaked client. */
    public void updateManifestSource(SetManifestSource source) {
        manifestSource.set(source);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (handshaked.contains(player.getUniqueId())) {
                send(player, source);
            }
        }
    }

    public void clearManifestSource() {
        manifestSource.set(null);
    }

    /**
     * Begins tracking a player's preload set: an optional chat notice naming the appearances, an
     * optional BossBar progress bar, and (when the failure policy is KICK) a timeout. Only the
     * preload set is tracked; on-sight downloads (entities entering view) are never announced here.
     */
    private void beginPreload(Player player, List<String> preload) {
        PluginSettings config = preloadConfig;
        if (config == null) {
            return;
        }
        // The client retries the handshake until acknowledged (responses sent in the join window are
        // dropped client-side), so this may run more than once per connection. Clear any prior
        // tracking first, so a re-handshake neither stacks BossBars nor orphans the previous one.
        clearPreloadTracking(player.getUniqueId());
        if (config.preloadNotifyText()) {
            player.sendMessage(config.preloadTextMessage().replace("{appearances}", String.join("、", preload)));
        }
        boolean kick = config.preloadFailurePolicy() == PluginSettings.PreloadFailurePolicy.KICK;
        boolean bar = config.preloadProgressBarEnabled();
        if (!kick && !bar) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        pendingPreload.put(uuid, Collections.synchronizedSet(new HashSet<String>(preload)));
        preloadTotal.put(uuid, preload.size());
        if (bar) {
            BossBar bossBar = Bukkit.createBossBar(config.preloadProgressBarTitle(), BarColor.BLUE, BarStyle.SOLID);
            bossBar.setProgress(0.0);
            bossBar.addPlayer(player);
            preloadBars.put(uuid, bossBar);
        }
        long timeoutSeconds = config.preloadTimeoutSeconds();
        if (timeoutSeconds > 0) {
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    onPreloadTimeout(uuid);
                }
            }, timeoutSeconds * 20L);
            preloadTimeouts.put(uuid, task);
        }
    }

    private void onPreloadTimeout(UUID uuid) {
        preloadTimeouts.remove(uuid);
        Set<String> pending = pendingPreload.get(uuid);
        PluginSettings config = preloadConfig;
        if (pending != null && !pending.isEmpty() && config != null
                && config.preloadFailurePolicy() == PluginSettings.PreloadFailurePolicy.KICK) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                kickForPreload(player, pending.iterator().next());
                return;
            }
        }
        clearPreloadTracking(uuid);
    }

    /** Records that a preload resource finished (or failed); updates the bar, kicks, or completes. */
    private void markPreloadResolved(Player player, String identifier, boolean failed) {
        UUID uuid = player.getUniqueId();
        Set<String> pending = pendingPreload.get(uuid);
        if (pending == null || !pending.contains(identifier)) {
            return;
        }
        PluginSettings config = preloadConfig;
        if (failed && config != null && config.preloadFailurePolicy() == PluginSettings.PreloadFailurePolicy.KICK) {
            kickForPreload(player, identifier);
            return;
        }
        pending.remove(identifier);
        updatePreloadBar(uuid);
        if (pending.isEmpty()) {
            clearPreloadTracking(uuid);
        }
    }

    private void updatePreloadBar(UUID uuid) {
        BossBar bar = preloadBars.get(uuid);
        Integer total = preloadTotal.get(uuid);
        Set<String> pending = pendingPreload.get(uuid);
        if (bar != null && total != null && total > 0 && pending != null) {
            double progress = (total - pending.size()) / (double) total;
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }
    }

    private void kickForPreload(Player player, String appearance) {
        clearPreloadTracking(player.getUniqueId());
        PluginSettings config = preloadConfig;
        String message = config != null
                ? config.preloadKickMessage().replace("{appearance}", appearance)
                : "Failed to download appearance resources: " + appearance;
        player.kickPlayer(message);
        plugin.getLogger().info("Player " + player.getName() + " was kicked: a preloaded appearance failed to download: " + appearance);
    }

    private void clearPreloadTracking(UUID uuid) {
        pendingPreload.remove(uuid);
        preloadTotal.remove(uuid);
        BukkitTask task = preloadTimeouts.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        BossBar bar = preloadBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    private void send(Player player, Packet packet) {
        player.sendPluginMessage(plugin, channel, PacketCodec.encode(packet));
    }

    private static int resolveEntityId(UUID entityUuid) {
        Entity entity = Bukkit.getEntity(entityUuid);
        return entity != null ? entity.getEntityId() : SetIdentifier.UNKNOWN_ENTITY_ID;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        handshaked.remove(uuid);
        clearPreloadTracking(uuid);
    }

    public String channelName() {
        return channel;
    }

    public int handshakedCount() {
        return handshaked.size();
    }
}
