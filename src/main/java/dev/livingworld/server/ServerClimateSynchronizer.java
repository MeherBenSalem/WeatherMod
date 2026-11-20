package dev.livingworld.server;

import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.config.ServerConfig;
import dev.livingworld.net.LWNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.level.ServerWorldProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns the per-dimension {@link SimulationOverrides} and pushes them to clients when they change:
 * on join, on dimension change, when a test command edits them and when the rain forecast moves.
 */
public final class ServerClimateSynchronizer {
    private static final Map<RegistryKey<World>, SimulationOverrides> STATE = new HashMap<>();
    private static final int CHECK_INTERVAL = 40;

    private ServerClimateSynchronizer() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> sendTo(player));
        ServerTickEvents.END_WORLD_TICK.register(ServerClimateSynchronizer::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATE.clear();
            weatherSeen = false;
        });
    }

    public static SimulationOverrides get(ServerWorld world) {
        return STATE.computeIfAbsent(world.getRegistryKey(), k -> new SimulationOverrides());
    }

    public static void sendTo(ServerPlayerEntity player) {
        SimulationOverrides state = get(player.getServerWorld());
        state.gameplayEffects = ServerConfig.get().temperatureEffects;
        LWNetworking.sendState(player, state);
    }

    /** Pushes the current state of a dimension to everyone in it. */
    public static void broadcast(ServerWorld world) {
        SimulationOverrides state = get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            LWNetworking.sendState(player, state);
        }
    }

    private static boolean wasRaining;
    private static boolean wasThundering;
    private static boolean weatherSeen;

    /**
     * When a storm ends, vanilla draws a random dry spell before the next one. With a storm frequency
     * other than 1 that freshly drawn delay is rescaled once, at the moment the weather clears.
     */
    private static void applyStormFrequency(ServerWorldProperties properties) {
        boolean raining = properties.isRaining();
        boolean thundering = properties.isThundering();
        float frequency = ServerConfig.get().stormFrequency;
        if (weatherSeen && frequency != 1f) {
            if (wasRaining && !raining && properties.getRainTime() > 0) {
                properties.setRainTime(Math.max(200, (int) (properties.getRainTime() / frequency)));
            }
            if (wasThundering && !thundering && properties.getThunderTime() > 0) {
                properties.setThunderTime(Math.max(200, (int) (properties.getThunderTime() / frequency)));
            }
        }
        weatherSeen = true;
        wasRaining = raining;
        wasThundering = thundering;
    }

    private static void tick(ServerWorld world) {
        long time = world.getTime();
        if (time % CHECK_INTERVAL != 0L) {
            return;
        }
        SimulationOverrides state = get(world);
        boolean dirty = false;

        if (state.weather != null && time >= state.weatherUntil) {
            state.weather = null;
            dirty = true;
        }

        boolean gameplay = ServerConfig.get().temperatureEffects;
        if (gameplay != state.gameplayEffects) {
            state.gameplayEffects = gameplay;
            dirty = true;
        }

        long forecast = -1L;
        boolean overworld = world.getRegistryKey() == World.OVERWORLD;
        if (overworld) {
            applyStormFrequency(world.getServer().getSaveProperties().getMainWorldProperties());
        }
        if (ServerConfig.get().syncForecast && world.getDimension().hasSkyLight() && !world.getDimension().hasCeiling()) {
            ServerWorldProperties properties = world.getServer().getSaveProperties().getMainWorldProperties();
            if (!properties.isRaining() && properties.getClearWeatherTime() <= 0 && properties.getRainTime() > 0) {
                forecast = time + properties.getRainTime();
            }
        }
        // The countdown normally ticks in step with world time; only resend when the plan itself changes.
        if ((forecast < 0L) != (state.rainStartTime < 0L) || Math.abs(forecast - state.rainStartTime) > CHECK_INTERVAL * 2L) {
            state.rainStartTime = forecast;
            dirty = true;
        }

        if (dirty) {
            broadcast(world);
        }
    }
}
