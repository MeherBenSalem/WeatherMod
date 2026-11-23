package dev.livingworld.fabric.platform;

import dev.livingworld.platform.services.IPlatformHelper;
import dev.livingworld.net.LWNetworking;
import dev.livingworld.net.LWPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.function.Consumer;

/** The common code's view of Fabric. Named in {@code META-INF/services} and found by {@code Services}. */
public final class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    /** Each of the mod's packets is a Fabric networking channel of its own, announced by clients that have the mod. */
    @Override
    public boolean sendToClient(ServerPlayer player, Identifier channel, Consumer<FriendlyByteBuf> writer) {
        CustomPacketPayload.Type<LWPayload> type = LWNetworking.typeOf(channel);
        if (!ServerPlayNetworking.canSend(player, type)) {
            return false;
        }
        ServerPlayNetworking.send(player, LWPayload.of(type, writer));
        return true;
    }
}
