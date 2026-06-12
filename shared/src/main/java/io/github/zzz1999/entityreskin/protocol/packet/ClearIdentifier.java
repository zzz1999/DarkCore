package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

import java.util.Objects;
import java.util.UUID;

/** S-&gt;C: remove a previously assigned identifier, reverting the entity to vanilla appearance. */
public final class ClearIdentifier implements Packet {

    private final int entityId;
    private final UUID entityUuid;

    public ClearIdentifier(int entityId, UUID entityUuid) {
        this.entityId = entityId;
        this.entityUuid = entityUuid;
    }

    public int entityId() {
        return entityId;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    @Override
    public int id() {
        return PacketIds.CLEAR_IDENTIFIER;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeVarInt(entityId);
        buf.writeUuid(entityUuid);
    }

    public static ClearIdentifier read(PacketBuffer buf) {
        int entityId = buf.readVarInt();
        UUID entityUuid = buf.readUuid();
        return new ClearIdentifier(entityId, entityUuid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClearIdentifier)) {
            return false;
        }
        ClearIdentifier other = (ClearIdentifier) o;
        return entityId == other.entityId && entityUuid.equals(other.entityUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, entityUuid);
    }

    @Override
    public String toString() {
        return "ClearIdentifier{entityId=" + entityId + ", entityUuid=" + entityUuid + '}';
    }
}
