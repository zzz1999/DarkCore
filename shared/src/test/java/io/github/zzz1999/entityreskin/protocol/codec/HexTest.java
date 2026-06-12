package io.github.zzz1999.entityreskin.protocol.codec;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HexTest {

    @Test
    void roundTrip() {
        byte[] data = {0x00, 0x01, 0x7F, (byte) 0x80, (byte) 0xAB, (byte) 0xFF};
        assertEquals("00017f80abff", Hex.encode(data));
        assertArrayEquals(data, Hex.decode("00017f80abff"));
    }

    @Test
    void decodeAcceptsUppercase() {
        assertArrayEquals(new byte[]{(byte) 0xAB, (byte) 0xCD}, Hex.decode("ABCD"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(ProtocolException.class, () -> Hex.decode("abc"));
        assertThrows(ProtocolException.class, () -> Hex.decode("zz"));
        assertThrows(ProtocolException.class, () -> Hex.decode(null));
    }
}
