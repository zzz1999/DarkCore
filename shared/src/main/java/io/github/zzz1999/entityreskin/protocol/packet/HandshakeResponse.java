package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

import java.util.Objects;

/** S-&gt;C handshake reply: announces the server's protocol version and capabilities. */
public final class HandshakeResponse implements Packet {

    private final int protocolVersion;
    private final long serverCapabilities;

    public HandshakeResponse(int protocolVersion, long serverCapabilities) {
        this.protocolVersion = protocolVersion;
        this.serverCapabilities = serverCapabilities;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public long serverCapabilities() {
        return serverCapabilities;
    }

    @Override
    public int id() {
        return PacketIds.HANDSHAKE_RESPONSE;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(serverCapabilities);
    }

    public static HandshakeResponse read(PacketBuffer buf) {
        int protocolVersion = buf.readVarInt();
        long serverCapabilities = buf.readLong();
        return new HandshakeResponse(protocolVersion, serverCapabilities);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HandshakeResponse)) {
            return false;
        }
        HandshakeResponse other = (HandshakeResponse) o;
        return protocolVersion == other.protocolVersion && serverCapabilities == other.serverCapabilities;
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolVersion, serverCapabilities);
    }

    @Override
    public String toString() {
        return "HandshakeResponse{protocolVersion=" + protocolVersion
                + ", serverCapabilities=" + serverCapabilities + '}';
    }
}
