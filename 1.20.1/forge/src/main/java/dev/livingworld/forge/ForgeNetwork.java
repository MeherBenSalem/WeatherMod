package dev.livingworld.forge;

import dev.livingworld.LivingWorld;
import dev.livingworld.forge.client.LivingWorldForgeClient;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The mod's packets on Forge. All of them travel on one channel, each as the id it has in the common
 * code ({@code LWNetworking}) followed by its body, so adding a packet there needs nothing here.
 *
 * <p>The channel is optional in both directions: a client or a server without the mod is accepted, and
 * nothing is ever sent to a client that did not announce the channel.
 */
public final class ForgeNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(LivingWorld.id("main"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    private ForgeNetwork() {
    }

    /** One of the mod's packets: the id the common code knows it by, and its body as the common code wrote it. */
    private record Payload(ResourceLocation channel, FriendlyByteBuf body) {
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(channel);
            buf.writeBytes(body, body.readerIndex(), body.readableBytes());
        }

        static Payload decode(FriendlyByteBuf buf) {
            ResourceLocation channel = buf.readResourceLocation();
            byte[] body = new byte[buf.readableBytes()];
            buf.readBytes(body);
            return new Payload(channel, new FriendlyByteBuf(Unpooled.wrappedBuffer(body)));
        }
    }

    static void register() {
        CHANNEL.registerMessage(0, Payload.class, Payload::encode, Payload::decode, ForgeNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Runs on the network thread, like the receivers on Fabric: the common code reads the packet and hands off to the client thread. */
    private static void handle(Payload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LivingWorldForgeClient.receive(payload.channel(), payload.body()));
        context.get().setPacketHandled(true);
    }

    /** See {@code IPlatformHelper.sendToClient}. */
    public static boolean sendToClient(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        if (!CHANNEL.isRemotePresent(player.connection.connection)) {
            return false;
        }
        FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(body);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Payload(channel, body));
        return true;
    }
}
