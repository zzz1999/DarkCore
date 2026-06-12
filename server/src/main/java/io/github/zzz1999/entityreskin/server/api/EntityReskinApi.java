package io.github.zzz1999.entityreskin.server.api;

import io.github.zzz1999.entityreskin.protocol.Identifiers;
import io.github.zzz1999.entityreskin.server.EntityReskinPlugin;
import org.bukkit.entity.Entity;

/**
 * Public API for other plugins (quest systems, MythicMobs integrations, and similar). Obtain
 * the instance via {@link #get()} after EntityReskin has enabled. All methods must be called on the
 * main server thread, mirroring Bukkit's own threading rules for entities.
 */
public final class EntityReskinApi {

    private static volatile EntityReskinApi instance;

    private final EntityReskinPlugin plugin;

    public EntityReskinApi(EntityReskinPlugin plugin) {
        this.plugin = plugin;
    }

    public static EntityReskinApi get() {
        EntityReskinApi api = instance;
        if (api == null) {
            throw new IllegalStateException("EntityReskin is not enabled");
        }
        return api;
    }

    public static void register(EntityReskinApi api) {
        instance = api;
    }

    public static void unregister() {
        instance = null;
    }

    /** Assigns an appearance identifier to the entity and announces it to handshaked clients. */
    public void setIdentifier(Entity entity, String identifier) {
        if (!Identifiers.isValid(identifier)) {
            throw new IllegalArgumentException("invalid appearance identifier: " + identifier);
        }
        plugin.registry().set(entity.getUniqueId(), identifier);
        plugin.gateway().broadcastSet(entity.getUniqueId(), identifier);
        plugin.saveRegistryAsync();
    }

    /** Removes the entity's appearance, reverting it to vanilla on all clients. */
    public void clearIdentifier(Entity entity) {
        if (plugin.registry().clear(entity.getUniqueId()) != null) {
            plugin.gateway().broadcastClear(entity.getUniqueId());
            plugin.saveRegistryAsync();
        }
    }

    /** Returns the entity's appearance identifier, or {@code null} if none is assigned. */
    public String getIdentifier(Entity entity) {
        return plugin.registry().get(entity.getUniqueId());
    }
}
