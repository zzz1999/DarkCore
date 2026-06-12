package io.github.zzz1999.entityreskin.protocol.packet;

/**
 * Stable packet ids. Ranges: 0x0x = handshake/session, 0x1x = server-&gt;client appearance
 * control, 0x2x = client-&gt;server acknowledgements. 0x30 (PLAY_ANIMATION) is reserved for
 * phase 2 and intentionally not yet implemented.
 */
public final class PacketIds {

    public static final int HANDSHAKE_REQUEST = 0x01;
    public static final int HANDSHAKE_RESPONSE = 0x02;
    public static final int SET_MANIFEST_SOURCE = 0x03;
    public static final int PRELOAD = 0x04;

    public static final int SET_IDENTIFIER = 0x10;
    public static final int CLEAR_IDENTIFIER = 0x11;

    public static final int RESOURCE_READY = 0x20;
    public static final int RESOURCE_ERROR = 0x21;

    private PacketIds() {
    }
}
