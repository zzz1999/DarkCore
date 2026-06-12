package io.github.zzz1999.entityreskin.protocol.codec;

import io.github.zzz1999.entityreskin.protocol.ProtocolException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A minimal, version-independent binary buffer for protocol packets. Deliberately does NOT use
 * Minecraft's buffer types so the same encoding works across the entire supported version range
 * and on the (NMS-free) Bukkit server.
 *
 * <p>A single instance is either in write mode (no-arg constructor) or read mode (byte[]
 * constructor); mixing modes throws.</p>
 */
public final class PacketBuffer {

    private static final int DEFAULT_MAX_STRING = 32767;

    private final ByteArrayOutputStream out;
    private final byte[] in;
    private int pos;

    /** Creates a write-mode buffer. */
    public PacketBuffer() {
        this.out = new ByteArrayOutputStream();
        this.in = null;
        this.pos = 0;
    }

    /** Creates a read-mode buffer over the given bytes. */
    public PacketBuffer(byte[] data) {
        this.out = null;
        this.in = data;
        this.pos = 0;
    }

    private void requireWrite() {
        if (out == null) {
            throw new ProtocolException("PacketBuffer is in read mode");
        }
    }

    private void requireRead() {
        if (in == null) {
            throw new ProtocolException("PacketBuffer is in write mode");
        }
    }

    // ---------------------------------------------------------------- write

    public void writeByte(int b) {
        requireWrite();
        out.write(b & 0xFF);
    }

    public void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    public void writeVarInt(int value) {
        requireWrite();
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    public void writeInt(int value) {
        requireWrite();
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public void writeLong(long value) {
        requireWrite();
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) ((value >>> shift) & 0xFF));
        }
    }

    public void writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
    }

    public void writeString(String value) {
        requireWrite();
        String s = value == null ? "" : value;
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    public void writeUuid(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    public void writeBytes(byte[] data) {
        requireWrite();
        byte[] d = data == null ? new byte[0] : data;
        writeVarInt(d.length);
        out.write(d, 0, d.length);
    }

    public byte[] toByteArray() {
        requireWrite();
        return out.toByteArray();
    }

    // ----------------------------------------------------------------- read

    public int remaining() {
        requireRead();
        return in.length - pos;
    }

    public byte readByte() {
        requireRead();
        if (pos >= in.length) {
            throw new ProtocolException("read past end of buffer");
        }
        return in[pos++];
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public int readVarInt() {
        int value = 0;
        int position = 0;
        while (true) {
            byte current = readByte();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                break;
            }
            position += 7;
            if (position >= 32) {
                throw new ProtocolException("VarInt too big");
            }
        }
        return value;
    }

    public int readInt() {
        int b0 = readByte() & 0xFF;
        int b1 = readByte() & 0xFF;
        int b2 = readByte() & 0xFF;
        int b3 = readByte() & 0xFF;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public long readLong() {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (readByte() & 0xFF);
        }
        return value;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public String readString() {
        return readString(DEFAULT_MAX_STRING);
    }

    public String readString(int maxLength) {
        requireRead();
        int len = readVarInt();
        if (len < 0) {
            throw new ProtocolException("negative string length: " + len);
        }
        if (len > maxLength) {
            throw new ProtocolException("string too long: " + len + " > " + maxLength);
        }
        if (len > remaining()) {
            throw new ProtocolException("string length exceeds buffer: " + len);
        }
        String s = new String(in, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    public UUID readUuid() {
        long msb = readLong();
        long lsb = readLong();
        return new UUID(msb, lsb);
    }

    public byte[] readBytes(int maxLength) {
        requireRead();
        int len = readVarInt();
        if (len < 0) {
            throw new ProtocolException("negative byte array length: " + len);
        }
        if (len > maxLength) {
            throw new ProtocolException("byte array too long: " + len + " > " + maxLength);
        }
        if (len > remaining()) {
            throw new ProtocolException("byte array length exceeds buffer: " + len);
        }
        byte[] data = new byte[len];
        System.arraycopy(in, pos, data, 0, len);
        pos += len;
        return data;
    }
}
