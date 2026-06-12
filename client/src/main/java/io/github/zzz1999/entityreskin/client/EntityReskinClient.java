package io.github.zzz1999.entityreskin.client;

import io.github.zzz1999.entityreskin.client.appearance.AppearanceKeys;
import io.github.zzz1999.entityreskin.client.appearance.AppearanceRendering;
import io.github.zzz1999.entityreskin.client.net.ControlChannel;
import io.github.zzz1999.entityreskin.client.render.ClientRuntime;
import io.github.zzz1999.entityreskin.client.render.TextureRegistry;
import io.github.zzz1999.entityreskin.client.session.ClientSession;
import io.github.zzz1999.entityreskin.protocol.Channel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point. Registers the EntityReskin control channel, announces the client to the server
 * on join (the server begins the appearance protocol only after the handshake), and discards all
 * session state on disconnect (memory-only policy). The in-memory asset store and GeckoLib
 * rendering build on this in later phases.
 */
public final class EntityReskinClient implements ClientModInitializer {

    /** Mod identifier; matches {@code fabric.mod.json}. */
    public static final String MOD_ID = "entityreskin";

    private static final Logger LOGGER = LoggerFactory.getLogger("EntityReskin");

    // The handshake is NOT sent at the raw JOIN instant: at that point the client is still loading
    // in and the plugin-message channel negotiation is incomplete, so the server's reply
    // (HandshakeResponse + SetManifestSource) is dropped client-side. Instead we send shortly after
    // join and re-send on an interval until the server acknowledges, which is robust against the
    // variable join-window timing. Re-sending is harmless: the server's handshake handling is
    // idempotent (a handshaked player is tracked in a set, and the manifest source is re-pinned).
    private static final int HANDSHAKE_FIRST_DELAY_TICKS = 10;     // ~0.5s after join
    private static final int HANDSHAKE_RETRY_INTERVAL_TICKS = 20;  // ~1s between attempts
    private static final int HANDSHAKE_MAX_ATTEMPTS = 12;          // give up after ~12s

    /** Ticks since join while the handshake is pending; -1 when disarmed (not connected / acknowledged). */
    private int handshakeTicks = -1;
    private int handshakeAttempts;

    @Override
    public void onInitializeClient() {
        ControlChannel.registerPayloadTypes();
        AppearanceRendering.register();

        ClientSession session = new ClientSession();
        ControlChannel channel = new ControlChannel(session);
        channel.registerReceiver();
        ClientRuntime.setSession(session);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientRuntime.setSession(session);
            handshakeTicks = 0;
            handshakeAttempts = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (handshakeTicks < 0) {
                return;
            }
            if (session.isHandshakeAcknowledged()) {
                handshakeTicks = -1;
                return;
            }
            if (handshakeAttempts >= HANDSHAKE_MAX_ATTEMPTS) {
                handshakeTicks = -1;
                LOGGER.warn("handshake not acknowledged after {} attempts; "
                        + "the server may not be running EntityReskin.", HANDSHAKE_MAX_ATTEMPTS);
                return;
            }
            int dueAt = HANDSHAKE_FIRST_DELAY_TICKS + handshakeAttempts * HANDSHAKE_RETRY_INTERVAL_TICKS;
            if (handshakeTicks >= dueAt) {
                channel.sendHandshake();
                handshakeAttempts++;
            }
            handshakeTicks++;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handshakeTicks = -1;
            channel.onDisconnect();
            session.clear();
            ClientRuntime.clearSession();
            TextureRegistry.releaseAll();
            AppearanceKeys.clearAll();
        });

        LOGGER.info("EntityReskin client initialised; control channel '{}'.", Channel.MODERN);
    }
}
