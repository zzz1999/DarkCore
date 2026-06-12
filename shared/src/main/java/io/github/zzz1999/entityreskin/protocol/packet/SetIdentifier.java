package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

import java.util.Objects;
import java.util.UUID;

/**
 * S-&gt;C: assign an appearance {@code identifier} to an entity. {@code entityId} is the network
 * entity id (used for immediate lookup via the client world); {@code entityUuid} is the stable
 * key the client stores so it can re-apply the skin when the entity re-enters view.
 * {@code scaleHint} is optional.
 */
public final class SetIdentifier implements Packet {

    /**
     * Sentinel for {@code entityId} when the entity is not currently loaded on the server (for
     * example during the full registry synchronization after a handshake). The client must then
     * resolve the entity by {@code entityUuid} when it enters view.
     */
    public static final int UNKNOWN_ENTITY_ID = -1;

    private static final int MAX_IDENTIFIER = 256;

    private final int entityId;
    private final UUID entityUuid;
    private final String identifier;
    private final Float scaleHint;

    public SetIdentifier(int entityId, UUID entityUuid, String identifier, Float scaleHint) {
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.identifier = identifier;
        this.scaleHint = scaleHint;
    }

    public SetIdentifier(int entityId, UUID entityUuid, String identifier) {
        this(entityId, entityUuid, identifier, null);
    }

    public int entityId() {
        return entityId;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public String identifier() {
        return identifier;
    }

    /** Optional scale multiplier hint, or {@code null} if not specified. */
    public Float scaleHint() {
        return scaleHint;
    }

    @Override
    public int id() {
        return PacketIds.SET_IDENTIFIER;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeVarInt(entityId);
        buf.writeUuid(entityUuid);
        buf.writeString(identifier);
        buf.writeBoolean(scaleHint != null);
        if (scaleHint != null) {
            buf.writeFloat(scaleHint);
        }
    }

    public static SetIdentifier read(PacketBuffer buf) {
        int entityId = buf.readVarInt();
        UUID entityUuid = buf.readUuid();
        String identifier = buf.readString(MAX_IDENTIFIER);
        Float scaleHint = buf.readBoolean() ? Float.valueOf(buf.readFloat()) : null;
        return new SetIdentifier(entityId, entityUuid, identifier, scaleHint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SetIdentifier)) {
            return false;
        }
        SetIdentifier other = (SetIdentifier) o;
        return entityId == other.entityId
                && entityUuid.equals(other.entityUuid)
                && identifier.equals(other.identifier)
                && Objects.equals(scaleHint, other.scaleHint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, entityUuid, identifier, scaleHint);
    }

    @Override
    public String toString() {
        return "SetIdentifier{entityId=" + entityId + ", entityUuid=" + entityUuid
                + ", identifier='" + identifier + "', scaleHint=" + scaleHint + '}';
    }
}
