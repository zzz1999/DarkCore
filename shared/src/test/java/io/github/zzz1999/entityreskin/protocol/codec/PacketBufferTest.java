package io.github.zzz1999.entityreskin.protocol.codec;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketBufferTest {

    private static int roundTripVarInt(int value) {
        PacketBuffer w = new PacketBuffer();
        w.writeVarInt(value);
        return new PacketBuffer(w.toByteArray()).readVarInt();
    }

    @Test
    void varIntRoundTrip() {
        int[] values = {
                0, 1, 2, 127, 128, 255, 256, 16383, 16384, 2097151, 2097152,
                Integer.MAX_VALUE, -1, Integer.MIN_VALUE
        };
        for (int v : values) {
            assertEquals(v, roundTripVarInt(v), "varint " + v);
        }
    }

    @Test
    void stringRoundTripIncludingUnicode() {
        PacketBuffer w = new PacketBuffer();
        w.writeString("");
        w.writeString("hello");
        w.writeString("红龙 dragon");
        PacketBuffer r = new PacketBuffer(w.toByteArray());
        assertEquals("", r.readString());
        assertEquals("hello", r.readString());
        assertEquals("红龙 dragon", r.readString());
    }

    @Test
    void primitivesRoundTrip() {
        UUID uuid = UUID.fromString("12345678-1234-5678-1234-567812345678");
        PacketBuffer w = new PacketBuffer();
        w.writeInt(-42);
        w.writeLong(1234567890123L);
        w.writeFloat(3.5f);
        w.writeBoolean(true);
        w.writeBoolean(false);
        w.writeUuid(uuid);
        w.writeBytes(new byte[]{1, 2, 3, 4});
        PacketBuffer r = new PacketBuffer(w.toByteArray());
        assertEquals(-42, r.readInt());
        assertEquals(1234567890123L, r.readLong());
        assertEquals(3.5f, r.readFloat(), 0.0f);
        assertTrue(r.readBoolean());
        assertFalse(r.readBoolean());
        assertEquals(uuid, r.readUuid());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, r.readBytes(16));
    }

    @Test
    void readStringRejectsOverMax() {
        PacketBuffer w = new PacketBuffer();
        w.writeString("abcdefghij"); // length 10
        PacketBuffer r = new PacketBuffer(w.toByteArray());
        assertThrows(ProtocolException.class, () -> r.readString(4));
    }

    @Test
    void readPastEndThrows() {
        PacketBuffer r = new PacketBuffer(new byte[0]);
        assertThrows(ProtocolException.class, r::readByte);
    }

    @Test
    void mixingModesThrows() {
        PacketBuffer w = new PacketBuffer();
        assertThrows(ProtocolException.class, w::readByte);
        PacketBuffer r = new PacketBuffer(new byte[]{1});
        assertThrows(ProtocolException.class, () -> r.writeByte(1));
    }
}
