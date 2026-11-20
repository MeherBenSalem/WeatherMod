package dev.livingworld.net;

import dev.livingworld.LivingWorld;
import dev.livingworld.climate.SimulationOverrides;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Channel ids and server-side senders. Packets are only sent to clients that announced the channel, so
 * vanilla clients and clients without the mod are never affected.
 */
public final class LWNetworking {
    /** S2C: {@link SimulationOverrides} for the player's current dimension. */
    public static final Identifier STATE = LivingWorld.id("state");
    /** S2C: asks the receiving client to run a local action (debug overlay, benchmark, config screen). */
    public static final Identifier CLIENT_COMMAND = LivingWorld.id("client_command");

    private LWNetworking() {
    }

    public static boolean sendState(ServerPlayerEntity player, SimulationOverrides state) {
        if (!ServerPlayNetworking.canSend(player, STATE)) {
            return false;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        state.write(buf);
        ServerPlayNetworking.send(player, STATE, buf);
        return true;
    }

    public static boolean sendClientCommand(ServerPlayerEntity player, String action, int argument) {
        if (!ServerPlayNetworking.canSend(player, CLIENT_COMMAND)) {
            return false;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(action, 64);
        buf.writeVarInt(argument);
        ServerPlayNetworking.send(player, CLIENT_COMMAND, buf);
        return true;
    }
}
