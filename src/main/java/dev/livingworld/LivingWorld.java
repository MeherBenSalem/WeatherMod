package dev.livingworld;

import dev.livingworld.config.ServerConfig;
import dev.livingworld.server.LWCommands;
import dev.livingworld.server.ServerClimateSynchronizer;
import dev.livingworld.server.TemperatureGameplay;
import dev.livingworld.surface.SurfaceMaterialResolver;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. Runs on both the client and the dedicated server, and therefore only touches the
 * simulation, networking, commands and optional survival rules — never rendering or audio.
 */
public final class LivingWorld implements ModInitializer {
    public static final String MOD_ID = "livingworld";
    public static final Logger LOGGER = LoggerFactory.getLogger("LivingWorld");

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ServerConfig.get();
        ServerClimateSynchronizer.init();
        TemperatureGameplay.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LWCommands.register(dispatcher));
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> SurfaceMaterialResolver.invalidate());
        LOGGER.info("Living World initialised (survival temperature rules: {})",
                ServerConfig.get().temperatureEffects ? "on" : "off");
    }
}
