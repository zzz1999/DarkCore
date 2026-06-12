package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Frames and dispatches packets. Wire format: {@code VarInt id} followed by the packet body.
 * Decoding looks up a reader by id; an unknown id is a {@link ProtocolException} (the caller
 * degrades gracefully). This is the single source of truth shared by client and server.
 */
public final class PacketCodec {

    private static final Map<Integer, Function<PacketBuffer, Packet>> READERS =
            new HashMap<Integer, Function<PacketBuffer, Packet>>();

    static {
        READERS.put(PacketIds.HANDSHAKE_REQUEST, new Function<PacketBuffer, Packet>() {
            public Packet apply(PacketBuffer b) {
                return HandshakeRequest.read(b);
            }
        });
        READERS.put(PacketIds.HANDSHAKE_RESPONSE, new Function<PacketBuffer, Packet>() {
            public Packet apply(PacketBuffer b) {
                return HandshakeResponse.read(b);
            }
        });
        READERS.put(PacketIds.SET_MANIFEST_SOURCE, new Function<PacketBuffer, Packet>() {
            public Packet apply(PacketBuffer b) {
                return SetManifestSource.read(b);
            }
        });
        READERS.put(PacketIds.PRELOAD, new Function<PacketBuffer, Packet>() {
            public Packet apply(PacketBuffer b) {
                return Preload.read(b);
            }
        });
        READERS.put(PacketIds.SET_IDENTIFIER, new Function<PacketBuffer, Packet>() {
            public Packet apply(PacketBuffer b) {
                return SetIdentifier.read(b);
            }
        });
        READERS.put(PacketIds.CLEAR_IDENTIFIER, new Function<PacketBuffer, Packet>() {
            public Packet apply(PacketBuffer b) {
                return ClearIdentifier.read(b);
            }
        });
        READERS.put(PacketIds.RESOURCE_READY, new Function<PacketBuffer, Packet>() {
            public Packet apply(PacketBuffer b) {
                return ResourceReady.read(b);
            }
        });
        READERS.put(PacketIds.RESOURCE_ERROR, new Function<PacketBuffer, Packet>() {
            public Packet apply(PacketBuffer b) {
                return ResourceError.read(b);
            }
        });
    }

    private PacketCodec() {
    }

    /** Encodes a packet to its full wire bytes (id + body). */
    public static byte[] encode(Packet packet) {
        PacketBuffer buf = new PacketBuffer();
        buf.writeVarInt(packet.id());
        packet.write(buf);
        return buf.toByteArray();
    }

    /** Decodes wire bytes back into a packet, or throws {@link ProtocolException}. */
    public static Packet decode(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        int id = buf.readVarInt();
        Function<PacketBuffer, Packet> reader = READERS.get(id);
        if (reader == null) {
            throw new ProtocolException("unknown packet id: 0x" + Integer.toHexString(id));
        }
        return reader.apply(buf);
    }
}
