package io.github.zzz1999.entityreskin.client.session;

import io.github.zzz1999.entityreskin.client.render.InMemoryAssetStore;
import io.github.zzz1999.entityreskin.protocol.packet.SetManifestSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Per-connection state: the negotiated server capabilities, the pinned manifest source, the
 * preload list, and the entity appearance assignments. Everything here lives only in memory and is
 * discarded on disconnect, consistent with the memory-only asset policy.
 */
public final class ClientSession {

    private static final Logger LOGGER = LoggerFactory.getLogger("EntityReskin");

    private final IdentifierStore identifiers = new IdentifierStore();
    private final InMemoryAssetStore assets = new InMemoryAssetStore();
    private volatile long serverCapabilities;
    private volatile boolean handshakeAcknowledged;
    private volatile SetManifestSource manifestSource;
    private volatile List<String> preload = Collections.emptyList();

    public IdentifierStore identifiers() {
        return identifiers;
    }

    public InMemoryAssetStore assets() {
        return assets;
    }

    public long serverCapabilities() {
        return serverCapabilities;
    }

    public SetManifestSource manifestSource() {
        return manifestSource;
    }

    /** True once the server's {@code HandshakeResponse} has been received this connection. */
    public boolean isHandshakeAcknowledged() {
        return handshakeAcknowledged;
    }

    public List<String> preload() {
        return preload;
    }

    public void onHandshakeResponse(int protocolVersion, long capabilities) {
        this.serverCapabilities = capabilities;
        this.handshakeAcknowledged = true;
        LOGGER.info("handshake complete; server protocol {}, capabilities 0x{}",
                protocolVersion, Long.toHexString(capabilities));
    }

    public void setManifestSource(SetManifestSource source) {
        this.manifestSource = source;
        LOGGER.info("manifest source set: {}{}", source.baseUrl(), source.manifestPath());
    }

    public void setPreload(List<String> identifiers) {
        this.preload = identifiers;
        LOGGER.info("preload list received: {} identifier(s)", identifiers.size());
    }

    public void clear() {
        identifiers.clearAll();
        assets.clearAll();
        serverCapabilities = 0;
        handshakeAcknowledged = false;
        manifestSource = null;
        preload = Collections.emptyList();
    }
}
