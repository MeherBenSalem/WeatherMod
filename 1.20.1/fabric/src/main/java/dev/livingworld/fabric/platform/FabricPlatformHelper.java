package dev.livingworld.fabric.platform;

import dev.livingworld.platform.services.IPlatformHelper;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
    public boolean sendToClient(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        if (!ServerPlayNetworking.canSend(player, channel)) {
            return false;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        writer.accept(buf);
        ServerPlayNetworking.send(player, channel, buf);
        return true;
    }
}
