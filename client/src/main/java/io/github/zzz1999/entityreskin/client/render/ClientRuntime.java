package io.github.zzz1999.entityreskin.client.render;

import io.github.zzz1999.entityreskin.client.session.ClientSession;
import io.github.zzz1999.entityreskin.client.session.IdentifierStore;

import java.util.UUID;

/**
 * Render-thread-reachable handle to the current connection's {@link ClientSession}. Entity
 * renderers and models are registered once at client initialization, but the session (and its
 * in-memory asset store) is per-connection, so the rendering code looks it up here at render time.
 * Set on join and cleared on disconnect.
 */
public final class ClientRuntime {

    private static volatile ClientSession session;

    private ClientRuntime() {
    }

    public static void setSession(ClientSession current) {
        session = current;
    }

    public static void clearSession() {
        session = null;
    }

    public static ClientSession session() {
        return session;
    }

    /** The appearance identifier assigned to the given entity, or {@code null} if none. */
    public static String appearanceFor(UUID entityUuid) {
        ClientSession current = session;
        if (current == null) {
            return null;
        }
        IdentifierStore.Assignment assignment = current.identifiers().get(entityUuid);
        return assignment == null ? null : assignment.identifier();
    }
}
