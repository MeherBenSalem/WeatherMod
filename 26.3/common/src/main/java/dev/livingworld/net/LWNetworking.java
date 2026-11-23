package dev.livingworld.net;

import dev.livingworld.LivingWorld;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.platform.Services;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Channel ids and server-side senders. Packets are only sent to clients that announced the channel, so
 * vanilla clients and clients without the mod are never affected.
 *
 * <p>How a packet travels is the loader's business (see {@code IPlatformHelper.sendToClient}); what is
 * in it is decided here and read back in {@code LivingWorldClient.receive}.
 */
public final class LWNetworking {
    /** S2C: {@link SimulationOverrides} for the player's current dimension. */
    public static final Identifier STATE = LivingWorld.id("state");
    /** S2C: asks the receiving client to run a local action (debug overlay, benchmark, config screen). */
    public static final Identifier CLIENT_COMMAND = LivingWorld.id("client_command");
    /** Longest action name a {@link #CLIENT_COMMAND} packet may carry. */
    public static final int MAX_ACTION_LENGTH = 64;

    public static final CustomPacketPayload.Type<LWPayload> STATE_TYPE = new CustomPacketPayload.Type<>(STATE);
    public static final CustomPacketPayload.Type<LWPayload> CLIENT_COMMAND_TYPE = new CustomPacketPayload.Type<>(CLIENT_COMMAND);
    /** Every payload type, for the loader modules to register. All of them travel from the server to the client. */
    public static final List<CustomPacketPayload.Type<LWPayload>> TYPES = List.of(STATE_TYPE, CLIENT_COMMAND_TYPE);

    /** The payload type of one of the channels above. */
    public static CustomPacketPayload.Type<LWPayload> typeOf(Identifier channel) {
        for (CustomPacketPayload.Type<LWPayload> type : TYPES) {
            if (type.id().equals(channel)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Living World channel " + channel);
    }

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
