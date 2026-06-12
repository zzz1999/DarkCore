package io.github.zzz1999.entityreskin.protocol.codec;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;

/**
 * Lowercase hexadecimal encoding for hashes. Java 8 compatible (the JDK gained
 * {@code java.util.HexFormat} only in Java 17, and the server plugin runs on Java 8).
 */
public final class Hex {

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(DIGITS[(b >> 4) & 0xF]);
            sb.append(DIGITS[b & 0xF]);
        }
        return sb.toString();
    }

    /** Decodes a hex string (either case), or throws {@link ProtocolException}. */
    public static byte[] decode(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new ProtocolException("invalid hex string length");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = digit(hex.charAt(i * 2));
            int low = digit(hex.charAt(i * 2 + 1));
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    private static int digit(char c) {
        int value = Character.digit(c, 16);
        if (value < 0) {
            throw new ProtocolException("invalid hex character: " + c);
        }
        return value;
    }
}
