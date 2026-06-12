package io.github.zzz1999.entityreskin.client.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory record of which appearance identifier is assigned to each entity, keyed by the stable
 * entity UUID (the network id is logged but may be unknown when the assignment arrives during the
 * post-handshake synchronization). Rendering, added in a later phase, reads this to decide how to
 * draw an entity. It is cleared on disconnect so nothing persists across sessions.
 */
public final class IdentifierStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("EntityReskin");

    /** An appearance assigned to an entity, with an optional scale multiplier hint. */
    public record Assignment(String identifier, Float scaleHint) {
    }

    private final Map<UUID, Assignment> byUuid = new ConcurrentHashMap<>();

    public void apply(int entityId, UUID entityUuid, String identifier, Float scaleHint) {
        byUuid.put(entityUuid, new Assignment(identifier, scaleHint));
        LOGGER.info("appearance assigned: entity {} (network id {}) -> {}", entityUuid, entityId, identifier);
    }

    public void clear(int entityId, UUID entityUuid) {
        if (byUuid.remove(entityUuid) != null) {
            LOGGER.info("appearance cleared: entity {} (network id {})", entityUuid, entityId);
        }
    }

    public Assignment get(UUID entityUuid) {
        return byUuid.get(entityUuid);
    }

    /** The distinct appearance identifiers currently assigned to any entity. */
    public Set<String> assignedIdentifiers() {
        Set<String> identifiers = new HashSet<>();
        for (Assignment assignment : byUuid.values()) {
            identifiers.add(assignment.identifier());
        }
        return identifiers;
    }

    public void clearAll() {
        byUuid.clear();
    }
}
