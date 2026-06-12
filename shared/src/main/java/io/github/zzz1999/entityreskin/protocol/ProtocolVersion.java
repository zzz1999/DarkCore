package io.github.zzz1999.entityreskin.protocol;

/**
 * Wire protocol version, exchanged during the handshake. Client and server
 * compare versions and gracefully degrade (vanilla appearance) when they do not match.
 */
public final class ProtocolVersion {

    /** Current protocol version. Increment on any breaking change to the packet layout. */
    public static final int CURRENT = 1;

    private ProtocolVersion() {
    }
}
