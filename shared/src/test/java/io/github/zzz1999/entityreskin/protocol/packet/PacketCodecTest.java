package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.Capabilities;
import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import io.github.zzz1999.entityreskin.protocol.ProtocolVersion;
import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketCodecTest {

    private static Packet roundTrip(Packet p) {
        return PacketCodec.decode(PacketCodec.encode(p));
    }

    @Test
    void handshakeRequestRoundTrip() {
        HandshakeRequest p = new HandshakeRequest(
                ProtocolVersion.CURRENT, Capabilities.GEOMETRY | Capabilities.ANIMATION);
        assertEquals(p, roundTrip(p));
    }

    @Test
    void handshakeResponseRoundTrip() {
        HandshakeResponse p = new HandshakeResponse(ProtocolVersion.CURRENT, Capabilities.MOLANG);
        assertEquals(p, roundTrip(p));
    }

    @Test
    void setManifestSourceRoundTrip() {
        byte[] hash = new byte[32];
        for (int i = 0; i < hash.length; i++) {
            hash[i] = (byte) i;
        }
        SetManifestSource p = new SetManifestSource(
                "https://cdn.example.com/entityreskin/", "manifest.json", hash);
        assertEquals(p, roundTrip(p));
    }

    @Test
    void setIdentifierWithScale() {
        SetIdentifier p = new SetIdentifier(42, UUID.randomUUID(), "entityreskin:dragon_red", 1.5f);
        assertEquals(p, roundTrip(p));
    }

    @Test
    void setIdentifierWithoutScale() {
        SetIdentifier p = new SetIdentifier(42, UUID.randomUUID(), "entityreskin:dragon_red");
        SetIdentifier decoded = (SetIdentifier) roundTrip(p);
        assertEquals(p, decoded);
        assertNull(decoded.scaleHint());
    }

    @Test
    void clearIdentifierRoundTrip() {
        ClearIdentifier p = new ClearIdentifier(7, UUID.randomUUID());
        assertEquals(p, roundTrip(p));
    }

    @Test
    void resourceReadyRoundTrip() {
        ResourceReady p = new ResourceReady("entityreskin:dragon_red");
        assertEquals(p, roundTrip(p));
    }

    @Test
    void resourceErrorRoundTrip() {
        ResourceError p = new ResourceError(
                "entityreskin:dragon_red", ResourceError.REASON_HASH_MISMATCH, "bad hash");
        assertEquals(p, roundTrip(p));
    }

    @Test
    void unknownPacketIdThrows() {
        PacketBuffer buf = new PacketBuffer();
        buf.writeVarInt(0x7E); // unregistered id
        byte[] bytes = buf.toByteArray();
        assertThrows(ProtocolException.class, () -> PacketCodec.decode(bytes));
    }

    @Test
    void idMatchesConstant() {
        assertEquals(PacketIds.SET_IDENTIFIER, new SetIdentifier(1, UUID.randomUUID(), "x").id());
    }
}
