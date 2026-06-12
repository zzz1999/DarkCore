package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreloadTest {

    @Test
    void roundTrip() {
        Preload original = new Preload(Arrays.asList("entityreskin:dragon_red", "entityreskin:dragon_blue"));
        Packet decoded = PacketCodec.decode(PacketCodec.encode(original));
        assertEquals(original, decoded);
        assertEquals(original.identifiers(), ((Preload) decoded).identifiers());
    }

    @Test
    void emptyRoundTrip() {
        Preload original = new Preload(Collections.emptyList());
        assertEquals(original, PacketCodec.decode(PacketCodec.encode(original)));
    }

    @Test
    void rejectsNegativeCount() {
        PacketBuffer buf = new PacketBuffer();
        buf.writeVarInt(PacketIds.PRELOAD);
        buf.writeVarInt(-1);
        byte[] bytes = buf.toByteArray();
        assertThrows(ProtocolException.class, () -> PacketCodec.decode(bytes));
    }
}
