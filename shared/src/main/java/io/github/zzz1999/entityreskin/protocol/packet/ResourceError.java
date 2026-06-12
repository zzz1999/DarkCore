package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

import java.util.Objects;

/**
 * C-&gt;S: the client could not prepare resources for {@code identifier}; the entity falls back to
 * vanilla rendering. {@code reasonCode} is one of the {@code REASON_*} constants.
 */
public final class ResourceError implements Packet {

    public static final int REASON_UNKNOWN = 0;
    public static final int REASON_UNKNOWN_IDENTIFIER = 1;
    public static final int REASON_DOWNLOAD_FAILED = 2;
    public static final int REASON_HASH_MISMATCH = 3;
    public static final int REASON_TOO_LARGE = 4;
    public static final int REASON_SECURITY_REJECTED = 5;
    public static final int REASON_PARSE_FAILED = 6;

    private static final int MAX_IDENTIFIER = 256;
    private static final int MAX_MESSAGE = 1024;

    private final String identifier;
    private final int reasonCode;
    private final String message;

    public ResourceError(String identifier, int reasonCode, String message) {
        this.identifier = identifier;
        this.reasonCode = reasonCode;
        this.message = message;
    }

    public String identifier() {
        return identifier;
    }

    public int reasonCode() {
        return reasonCode;
    }

    public String message() {
        return message;
    }

    @Override
    public int id() {
        return PacketIds.RESOURCE_ERROR;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(identifier);
        buf.writeVarInt(reasonCode);
        buf.writeString(message);
    }

    public static ResourceError read(PacketBuffer buf) {
        String identifier = buf.readString(MAX_IDENTIFIER);
        int reasonCode = buf.readVarInt();
        String message = buf.readString(MAX_MESSAGE);
        return new ResourceError(identifier, reasonCode, message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceError)) {
            return false;
        }
        ResourceError other = (ResourceError) o;
        return reasonCode == other.reasonCode
                && identifier.equals(other.identifier)
                && message.equals(other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, reasonCode, message);
    }

    @Override
    public String toString() {
        return "ResourceError{identifier='" + identifier + "', reasonCode=" + reasonCode
                + ", message='" + message + "'}";
    }
}
