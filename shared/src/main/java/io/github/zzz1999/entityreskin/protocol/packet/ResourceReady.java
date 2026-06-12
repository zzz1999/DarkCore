package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

/** C-&gt;S acknowledgement: the client has the resources for {@code identifier} ready to render. */
public final class ResourceReady implements Packet {

    private static final int MAX_IDENTIFIER = 256;

    private final String identifier;

    public ResourceReady(String identifier) {
        this.identifier = identifier;
    }

    public String identifier() {
        return identifier;
    }

    @Override
    public int id() {
        return PacketIds.RESOURCE_READY;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(identifier);
    }

    public static ResourceReady read(PacketBuffer buf) {
        return new ResourceReady(buf.readString(MAX_IDENTIFIER));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceReady)) {
            return false;
        }
        return identifier.equals(((ResourceReady) o).identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public String toString() {
        return "ResourceReady{identifier='" + identifier + "'}";
    }
}
