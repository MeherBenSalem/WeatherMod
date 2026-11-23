package dev.livingworld.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

/**
 * One of the mod's packets as it travels: the channel it belongs to and its body, exactly as the common
 * code wrote it. Every channel in {@link LWNetworking} has a payload type of its own, so a client
 * announces each one, and a server only sends to clients that announced it.
 */
public record LWPayload(CustomPacketPayload.Type<LWPayload> type, byte[] body) implements CustomPacketPayload {
    /** Reads and writes the body as it is: the payload type already says which channel it is on. */
    public static StreamCodec<FriendlyByteBuf, LWPayload> codec(CustomPacketPayload.Type<LWPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.body()), buf -> {
            byte[] body = new byte[buf.readableBytes()];
            buf.readBytes(body);
            return new LWPayload(type, body);
        });
    }

    /** A packet on the given channel, its body written by {@code writer}. */
    public static LWPayload of(CustomPacketPayload.Type<LWPayload> type, Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        byte[] body = new byte[buf.readableBytes()];
        buf.readBytes(body);
        return new LWPayload(type, body);
    }

    /** The body, ready to be read by the common code. */
    public FriendlyByteBuf read() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(body));
    }
}
