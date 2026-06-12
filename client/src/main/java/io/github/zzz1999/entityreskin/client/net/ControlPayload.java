package io.github.zzz1999.entityreskin.client.net;

import io.github.zzz1999.entityreskin.protocol.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Carries the raw EntityReskin control bytes across Minecraft's custom-payload transport on the
 * {@code entityreskin:main} plugin-messaging channel. The body is the version-independent
 * {@code PacketCodec} encoding (a VarInt id followed by the packet body); this type only moves
 * those bytes and does not interpret them. Registering this codec is what stops the client from
 * discarding the server's plugin messages as an unknown payload.
 */
public record ControlPayload(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ControlPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse(Channel.MODERN));

    /** Reads and writes the entire payload slice verbatim; framing is handled by PacketCodec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data()),
            buf -> {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                return new ControlPayload(data);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
