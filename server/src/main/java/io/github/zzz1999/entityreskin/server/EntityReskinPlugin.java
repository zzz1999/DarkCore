package io.github.zzz1999.entityreskin.server;

import io.github.zzz1999.entityreskin.server.api.EntityReskinApi;
import io.github.zzz1999.entityreskin.server.backend.BackendClient;
import io.github.zzz1999.entityreskin.server.backend.ManifestHashPoller;
import io.github.zzz1999.entityreskin.server.command.EntityReskinCommand;
import io.github.zzz1999.entityreskin.server.model.IdentifierRegistry;
import io.github.zzz1999.entityreskin.server.net.PluginMessageGateway;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * EntityReskin server plugin. Uses only stable Bukkit API (no NMS), so a single jar runs on the
 * full 1.13-to-latest range. Responsibilities: maintain the entity-to-identifier registry,
 * relay it to handshaked clients over plugin messaging, and keep the pinned manifest hash
 * current by polling the resource backend.
 */
public final class EntityReskinPlugin extends JavaPlugin {

    private IdentifierRegistry registry;
    private PluginMessageGateway gateway;
    private ManifestHashPoller poller;
    private File registryFile;

    @Override
    public void onEnable() {
        printBanner();
        saveDefaultConfig();
        registryFile = new File(getDataFolder(), "identifiers.yml");
        registry = new IdentifierRegistry(getLogger());
        registry.load(registryFile);

        gateway = new PluginMessageGateway(this, registry);
        gateway.register();
        getServer().getPluginManager().registerEvents(gateway, this);

        startPoller();

        PluginCommand command = getCommand("entityreskin");
        if (command != null) {
            EntityReskinCommand executor = new EntityReskinCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        EntityReskinApi.register(new EntityReskinApi(this));
        getLogger().info("EntityReskin enabled with " + registry.size() + " appearance entries.");
    }

    @Override
    public void onDisable() {
        if (poller != null) {
            poller.stop();
        }
        if (registry != null && registryFile != null) {
            registry.save(registryFile);
        }
        EntityReskinApi.unregister();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    /** Re-reads the configuration and restarts the backend poller. */
    public void reloadPlugin() {
        if (poller != null) {
            poller.stop();
            poller = null;
        }
        reloadConfig();
        gateway.clearManifestSource();
        startPoller();
    }

    private void startPoller() {
        PluginSettings settings = PluginSettings.load(this);
        gateway.setPreload(settings.preloadIdentifiers());
        gateway.configurePreload(settings);
        if (!settings.isConfigured()) {
            getLogger().warning("Resource backend not configured (backend.base-url and "
                    + "backend.server-token in config.yml); clients will not receive a manifest.");
            return;
        }
        poller = new ManifestHashPoller(this, settings, new BackendClient(settings), gateway);
        poller.start();
    }

    public IdentifierRegistry registry() {
        return registry;
    }

    public PluginMessageGateway gateway() {
        return gateway;
    }

    public ManifestHashPoller poller() {
        return poller;
    }

    /** Logs a concise startup line on enable. */
    private void printBanner() {
        getLogger().info("EntityReskin v" + getDescription().getVersion()
                + " (runtime entity re-skinning for Minecraft 1.13+).");
    }

    /** Persists the registry off the main thread (the registry file is small). */
    public void saveRegistryAsync() {
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                registry.save(registryFile);
            }
        });
    }
}
