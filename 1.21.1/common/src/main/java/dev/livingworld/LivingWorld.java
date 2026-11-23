package dev.livingworld;

import com.mojang.brigadier.CommandDispatcher;
import dev.livingworld.config.ServerConfig;
import dev.livingworld.platform.Services;
import dev.livingworld.server.LWCommands;
import dev.livingworld.server.SeasonalWorld;
import dev.livingworld.server.ServerClimateSynchronizer;
import dev.livingworld.server.TemperatureGameplay;
import dev.livingworld.surface.SurfaceMaterialResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. Runs on both the client and the dedicated server, and therefore only touches the
 * simulation, networking, commands and optional survival rules — never rendering or audio.
 *
 * <p>This class knows nothing of the mod loader. The Fabric and Forge modules each call {@link #init()}
 * once at start-up and forward their own events to the {@code on...} methods below, so the order things
 * happen in is the same on both.
 */
public final class LivingWorld {
    public static final String MOD_ID = "livingworld";
    public static final Logger LOGGER = LoggerFactory.getLogger("LivingWorld");

    private LivingWorld() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Called once by the loader's mod entry point. */
    public static void init() {
        ServerConfig.get();
        LOGGER.info("Living World initialised on {} (survival temperature rules: {})", Services.PLATFORM.getPlatformName(),
                ServerConfig.get().temperatureEffects ? "on" : "off");
    }

    /** {@code /livingworld ...}, when the server builds its command tree. */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        LWCommands.register(dispatcher);
    }

    /** Tags were (re)loaded or synchronised: what a block counts as underfoot may have changed. */
    public static void onTagsLoaded() {
        SurfaceMaterialResolver.invalidate();
    }

    /** At the end of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        TemperatureGameplay.tick(server);
    }

    /** At the end of every tick of every loaded dimension. */
    public static void onLevelTick(ServerLevel level) {
        ServerClimateSynchronizer.tick(level);
        SeasonalWorld.tick(level);
    }

    /** A player has finished joining. */
    public static void onPlayerJoin(ServerPlayer player) {
        ServerClimateSynchronizer.sendTo(player);
    }

    /** A player has arrived in another dimension. */
    public static void onPlayerChangedDimension(ServerPlayer player) {
        ServerClimateSynchronizer.sendTo(player);
    }

    /** A player has left the server. */
    public static void onPlayerLeave(ServerPlayer player) {
        TemperatureGameplay.forget(player);
    }

    /** The server (dedicated, or the one inside a single-player game) has shut down. */
    public static void onServerStopped() {
        ServerClimateSynchronizer.reset();
    }
}
