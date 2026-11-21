package dev.livingworld.net;

import dev.livingworld.LivingWorld;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Channel ids and server-side senders. Packets are only sent to clients that announced the channel, so
 * vanilla clients and clients without the mod are never affected.
 *
 * <p>How a packet travels is the loader's business (see {@code IPlatformHelper.sendToClient}); what is
 * in it is decided here and read back in {@code LivingWorldClient.receive}.
 */
public final class LWNetworking {
    /** S2C: {@link SimulationOverrides} for the player's current dimension. */
    public static final ResourceLocation STATE = LivingWorld.id("state");
    /** S2C: asks the receiving client to run a local action (debug overlay, benchmark, config screen). */
    public static final ResourceLocation CLIENT_COMMAND = LivingWorld.id("client_command");
    /** Longest action name a {@link #CLIENT_COMMAND} packet may carry. */
    public static final int MAX_ACTION_LENGTH = 64;

    private LWNetworking() {
    }

    public static boolean sendState(ServerPlayer player, SimulationOverrides state) {
        return Services.PLATFORM.sendToClient(player, STATE, state::write);
    }

    public static boolean sendClientCommand(ServerPlayer player, String action, int argument) {
        return Services.PLATFORM.sendToClient(player, CLIENT_COMMAND, buf -> {
            buf.writeUtf(action, MAX_ACTION_LENGTH);
            buf.writeVarInt(argument);
        });
    }
}
