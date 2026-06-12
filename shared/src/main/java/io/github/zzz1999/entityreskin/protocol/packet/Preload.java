package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * S-&gt;C: appearance identifiers the client should prefetch into memory as soon as it joins, so
 * the resources are ready before any entity uses them (no wait on first appearance). The list is
 * configured by the server owner in the plugin {@code config.yml}.
 */
public final class Preload implements Packet {

    private static final int MAX_IDENTIFIER = 256;
    private static final int MAX_COUNT = 4096;

    private final List<String> identifiers;

    public Preload(List<String> identifiers) {
        this.identifiers = Collections.unmodifiableList(new ArrayList<String>(identifiers));
    }

    public List<String> identifiers() {
        return identifiers;
    }

    @Override
    public int id() {
        return PacketIds.PRELOAD;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeVarInt(identifiers.size());
        for (String identifier : identifiers) {
            buf.writeString(identifier);
        }
    }

    public static Preload read(PacketBuffer buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_COUNT) {
            throw new ProtocolException("preload count out of range: " + count);
        }
        List<String> identifiers = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            identifiers.add(buf.readString(MAX_IDENTIFIER));
        }
        return new Preload(identifiers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Preload)) {
            return false;
        }
        return identifiers.equals(((Preload) o).identifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifiers);
    }

    @Override
    public String toString() {
        return "Preload{identifiers=" + identifiers + '}';
    }
}
