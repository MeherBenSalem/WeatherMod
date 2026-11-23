package dev.livingworld.platform.services;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * What the common code needs from the mod loader on both sides, client and dedicated server. Nothing in
 * here may mention a client-only class; the client's own needs are in
 * {@code dev.livingworld.client.platform.IClientPlatformHelper}.
 */
public interface IPlatformHelper {
    /** The name of the loader, for logs and the debug overlay: "Fabric" or "Forge". */
    String getPlatformName();

    boolean isModLoaded(String modId);

    /** True in a development environment (the dev client or server started from Gradle or an IDE). */
    boolean isDevelopmentEnvironment();

    /** The folder mod configuration files are kept in. */
    Path getConfigDir();

    /** The game folder (".minecraft", or the run folder in development). */
    Path getGameDir();

    /**
     * Sends one of the mod's packets to a player, if that player's client has the mod.
     *
     * @param channel one of the ids in {@code LWNetworking}
     * @param writer  writes the body of the packet
     * @return false, having sent nothing, when the client never announced the mod's channel: a vanilla
     *         client, or one without the mod
     */
    boolean sendToClient(ServerPlayer player, Identifier channel, Consumer<FriendlyByteBuf> writer);
}
