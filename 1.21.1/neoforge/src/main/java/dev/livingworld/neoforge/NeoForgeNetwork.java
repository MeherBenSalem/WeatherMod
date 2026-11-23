package dev.livingworld.neoforge;

import dev.livingworld.net.LWNetworking;
import dev.livingworld.net.LWPayload;
import dev.livingworld.neoforge.client.LivingWorldNeoForgeClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/**
 * The mod's packets on NeoForge: one payload type per channel of {@code LWNetworking}, all from the server
 * to the client. The channels are optional, so a client or a server without the mod is accepted, and
 * nothing is ever sent to a client that did not announce the channel.
 */
public final class NeoForgeNetwork {
    private static final String PROTOCOL = "1";

    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL).optional();
        for (CustomPacketPayload.Type<LWPayload> type : LWNetworking.TYPES) {
            // Only ever handled on a client: the handler is not called on a dedicated server.
            registrar.playToClient(type, LWPayload.codec(type),
                    (payload, context) -> LivingWorldNeoForgeClient.receive(payload.type().id(), payload.read()));
        }
    }

    /** See {@code IPlatformHelper.sendToClient}. */
    public static boolean sendToClient(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        CustomPacketPayload.Type<LWPayload> type = LWNetworking.typeOf(channel);
        if (!player.connection.hasChannel(type)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, LWPayload.of(type, writer));
        return true;
    }
}
