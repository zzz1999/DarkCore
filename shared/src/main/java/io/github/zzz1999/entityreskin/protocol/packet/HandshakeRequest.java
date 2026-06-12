package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

import java.util.Objects;

/** C-&gt;S handshake: announces the client's protocol version and supported capabilities. */
public final class HandshakeRequest implements Packet {

    private final int protocolVersion;
    private final long capabilities;

    public HandshakeRequest(int protocolVersion, long capabilities) {
        this.protocolVersion = protocolVersion;
        this.capabilities = capabilities;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public long capabilities() {
        return capabilities;
    }

    @Override
    public int id() {
        return PacketIds.HANDSHAKE_REQUEST;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeVarInt(protocolVersion);
        buf.writeLong(capabilities);
    }

    public static HandshakeRequest read(PacketBuffer buf) {
        int protocolVersion = buf.readVarInt();
        long capabilities = buf.readLong();
        return new HandshakeRequest(protocolVersion, capabilities);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HandshakeRequest)) {
            return false;
        }
        HandshakeRequest other = (HandshakeRequest) o;
        return protocolVersion == other.protocolVersion && capabilities == other.capabilities;
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolVersion, capabilities);
    }

    @Override
    public String toString() {
        return "HandshakeRequest{protocolVersion=" + protocolVersion + ", capabilities=" + capabilities + '}';
    }
}
