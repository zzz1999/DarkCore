package io.github.zzz1999.entityreskin.client.net;

import io.github.zzz1999.entityreskin.client.download.ResourceCoordinator;
import io.github.zzz1999.entityreskin.client.session.ClientSession;
import io.github.zzz1999.entityreskin.protocol.Capabilities;
import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import io.github.zzz1999.entityreskin.protocol.ProtocolVersion;
import io.github.zzz1999.entityreskin.protocol.packet.ClearIdentifier;
import io.github.zzz1999.entityreskin.protocol.packet.HandshakeRequest;
import io.github.zzz1999.entityreskin.protocol.packet.HandshakeResponse;
import io.github.zzz1999.entityreskin.protocol.packet.Packet;
import io.github.zzz1999.entityreskin.protocol.packet.PacketCodec;
import io.github.zzz1999.entityreskin.protocol.packet.Preload;
import io.github.zzz1999.entityreskin.protocol.packet.SetIdentifier;
import io.github.zzz1999.entityreskin.protocol.packet.SetManifestSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges Minecraft's custom-payload transport to the EntityReskin protocol: registers the
 * {@code entityreskin:main} payload codec, receives the server's control packets, decodes them with
 * the shared {@link PacketCodec}, and applies them to the {@link ClientSession}. Outbound packets
 * (the handshake now; resource acknowledgements later) are sent the same way.
 */
public final class ControlChannel {

    /** Capabilities this client can render today; announced to the server in the handshake. */
    private static final long CLIENT_CAPABILITIES = Capabilities.GEOMETRY | Capabilities.ANIMATION;

    private static final Logger LOGGER = LoggerFactory.getLogger("EntityReskin");

    private final ClientSession session;
    private final ResourceCoordinator resources;

    public ControlChannel(ClientSession session) {
        this.session = session;
        this.resources = new ResourceCoordinator(session, this);
    }

    /** Registers the payload codec in both directions. Must run during client initialization. */
    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(ControlPayload.TYPE, ControlPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ControlPayload.TYPE, ControlPayload.CODEC);
    }

    /** Registers the receiver that dispatches inbound control packets on the client thread. */
    public void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ControlPayload.TYPE, (payload, context) -> {
            byte[] data = payload.data();
            context.client().execute(() -> dispatch(data));
        });
    }

    /** Sends the handshake that asks the server to begin the appearance protocol. */
    public void sendHandshake() {
        send(new HandshakeRequest(ProtocolVersion.CURRENT, CLIENT_CAPABILITIES));
    }

    public void send(Packet packet) {
        try {
            ClientPlayNetworking.send(new ControlPayload(PacketCodec.encode(packet)));
        } catch (RuntimeException e) {
            // No active play connection (e.g. the player disconnected mid-download); drop quietly.
            LOGGER.debug("control packet not sent: {}", e.getMessage());
        }
    }

    /** Invalidates in-flight downloads on disconnect so none leak into the next connection. */
    public void onDisconnect() {
        resources.reset();
    }

    private void dispatch(byte[] data) {
        Packet packet;
        try {
            packet = PacketCodec.decode(data);
        } catch (ProtocolException e) {
            LOGGER.warn("discarding malformed control packet: {}", e.getMessage());
            return;
        }
        if (packet instanceof HandshakeResponse response) {
            session.onHandshakeResponse(response.protocolVersion(), response.serverCapabilities());
        } else if (packet instanceof SetManifestSource source) {
            session.setManifestSource(source);
            // A late source: re-drive preloads and already-assigned identifiers that ensure() dropped
            // because no manifest source was available when they first arrived.
            session.preload().forEach(resources::ensure);
            session.identifiers().assignedIdentifiers().forEach(resources::ensure);
        } else if (packet instanceof Preload preload) {
            session.setPreload(preload.identifiers());
            preload.identifiers().forEach(resources::ensure);
        } else if (packet instanceof SetIdentifier set) {
            session.identifiers().apply(set.entityId(), set.entityUuid(), set.identifier(), set.scaleHint());
            resources.ensure(set.identifier());
        } else if (packet instanceof ClearIdentifier clear) {
            session.identifiers().clear(clear.entityId(), clear.entityUuid());
        }
    }
}
