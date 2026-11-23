package dev.livingworld.neoforge.platform;

import dev.livingworld.neoforge.NeoForgeNetwork;
import dev.livingworld.platform.services.IPlatformHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.function.Consumer;

/** The common code's view of NeoForge. Named in {@code META-INF/services} and found by {@code Services}. */
public final class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean sendToClient(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        return NeoForgeNetwork.sendToClient(player, channel, writer);
    }
}
