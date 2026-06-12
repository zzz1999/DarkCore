package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

/**
 * A EntityReskin plugin-message payload. The leading byte(s) of every encoded packet are the
 * {@link #id()} (as a VarInt); {@link PacketCodec} handles framing and dispatch.
 */
public interface Packet {

    /** Stable numeric id; see {@link PacketIds}. */
    int id();

    /** Writes this packet's body (everything after the id) to the buffer. */
    void write(PacketBuffer buf);
}
