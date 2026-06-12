package io.github.zzz1999.entityreskin.server.model;

import io.github.zzz1999.entityreskin.protocol.Identifiers;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The entity-to-identifier assignments, keyed by entity UUID (stable across restarts because
 * entity UUIDs persist in chunk data). Persisted to a small YAML file so appearances survive a
 * server restart.
 */
public final class IdentifierRegistry {

    private final Map<UUID, String> entries = new ConcurrentHashMap<UUID, String>();
    private final Logger logger;

    public IdentifierRegistry(Logger logger) {
        this.logger = logger;
    }

    public void set(UUID entityUuid, String identifier) {
        entries.put(entityUuid, identifier);
    }

    /** Removes the assignment and returns the previous identifier, or {@code null}. */
    public String clear(UUID entityUuid) {
        return entries.remove(entityUuid);
    }

    public String get(UUID entityUuid) {
        return entries.get(entityUuid);
    }

    public Map<UUID, String> snapshot() {
        return new HashMap<UUID, String>(entries);
    }

    public int size() {
        return entries.size();
    }

    public void load(File file) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            String identifier = config.getString(key);
            try {
                UUID uuid = UUID.fromString(key);
                if (Identifiers.isValid(identifier)) {
                    entries.put(uuid, identifier);
                } else {
                    logger.warning("Skipping invalid appearance identifier in identifiers.yml: " + key);
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Skipping invalid UUID in identifiers.yml: " + key);
            }
        }
    }

    public void save(File file) {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : entries.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            logger.warning("Failed to save the appearance registry: " + e.getMessage());
        }
    }
}
