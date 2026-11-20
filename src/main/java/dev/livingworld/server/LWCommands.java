package dev.livingworld.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.climate.WeatherType;
import dev.livingworld.config.ServerConfig;
import dev.livingworld.net.LWNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /livingworld developer and test commands.
 *
 * <p>{@code weather}, {@code temperature} and {@code wind} set <em>simulated</em> conditions: they change
 * what Living World renders and (if enabled) its survival rules for everyone in the dimension, but never
 * touch the actual Minecraft weather, crops, mobs or blocks. {@code realweather} is the only sub-command
 * that changes the real world weather, exactly like vanilla {@code /weather}.
 */
public final class LWCommands {
    private static final int DEFAULT_SECONDS = 600;

    private LWCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal("livingworld");

        // --- simulated weather ---------------------------------------------------------------
        LiteralArgumentBuilder<ServerCommandSource> weather = literal("weather").requires(LWCommands::isOperator);
        weather.then(literal("clear").executes(ctx -> clearWeather(ctx)));
        for (WeatherType type : WeatherType.values()) {
            String name = type == WeatherType.CLEAR ? "clear_sky" : type.id();
            weather.then(literal(name)
                    .executes(ctx -> setWeather(ctx, type, 1f, DEFAULT_SECONDS))
                    .then(argument("intensity", FloatArgumentType.floatArg(0.1f, 1.5f))
                            .executes(ctx -> setWeather(ctx, type, FloatArgumentType.getFloat(ctx, "intensity"), DEFAULT_SECONDS))
                            .then(argument("seconds", IntegerArgumentType.integer(1, 86400))
                                    .executes(ctx -> setWeather(ctx, type, FloatArgumentType.getFloat(ctx, "intensity"),
                                            IntegerArgumentType.getInteger(ctx, "seconds"))))));
        }
        root.then(weather);

        // --- real weather --------------------------------------------------------------------
        LiteralArgumentBuilder<ServerCommandSource> real = literal("realweather").requires(LWCommands::isOperator);
        for (String kind : new String[]{"clear", "rain", "thunder"}) {
            real.then(literal(kind)
                    .executes(ctx -> setRealWeather(ctx, kind, DEFAULT_SECONDS))
                    .then(argument("seconds", IntegerArgumentType.integer(1, 86400))
                            .executes(ctx -> setRealWeather(ctx, kind, IntegerArgumentType.getInteger(ctx, "seconds")))));
        }
        root.then(real);

        // --- simulated temperature / wind ------------------------------------------------------
        root.then(literal("temperature").requires(LWCommands::isOperator)
                .then(literal("set").then(argument("celsius", FloatArgumentType.floatArg(-60f, 80f))
                        .executes(ctx -> {
                            float c = FloatArgumentType.getFloat(ctx, "celsius");
                            SimulationOverrides o = overrides(ctx);
                            o.temperatureC = c;
                            changed(ctx, String.format(Locale.ROOT, "Simulated ambient temperature set to %.1f°C in this dimension.", c));
                            return 1;
                        })))
                .then(literal("clear").executes(ctx -> {
                    overrides(ctx).temperatureC = Float.NaN;
                    changed(ctx, "Simulated temperature cleared; the climate model is back in control.");
                    return 1;
                })));

        root.then(literal("wind").requires(LWCommands::isOperator)
                .then(literal("set").then(argument("strength", FloatArgumentType.floatArg(0f, 1.6f))
                        .executes(ctx -> {
                            float w = FloatArgumentType.getFloat(ctx, "strength");
                            overrides(ctx).wind = w;
                            changed(ctx, String.format(Locale.ROOT, "Simulated wind strength set to %.2f (0 calm, 1 gale) in this dimension.", w));
                            return 1;
                        })))
                .then(literal("clear").executes(ctx -> {
                    overrides(ctx).wind = -1f;
                    changed(ctx, "Simulated wind cleared; the wind field is back in control.");
                    return 1;
                })));

        // --- information -----------------------------------------------------------------------
        root.then(literal("status").executes(LWCommands::status));

        // --- client-side tools (run on the caller's own client) --------------------------------
        LiteralArgumentBuilder<ServerCommandSource> debug = literal("debug");
        for (String mode : new String[]{"atmosphere", "footprints", "audio", "off"}) {
            debug.then(literal(mode).executes(ctx -> clientCommand(ctx, "debug_" + mode, 0)));
        }
        root.then(debug);
        root.then(literal("benchmark")
                .executes(ctx -> clientCommand(ctx, "benchmark", 20))
                .then(argument("seconds", IntegerArgumentType.integer(6, 300))
                        .executes(ctx -> clientCommand(ctx, "benchmark", IntegerArgumentType.getInteger(ctx, "seconds")))));
        root.then(literal("config").executes(ctx -> clientCommand(ctx, "config", 0)));

        dispatcher.register(root);
    }

    private static boolean isOperator(ServerCommandSource source) {
        return source.hasPermissionLevel(ServerConfig.get().commandPermissionLevel);
    }

    private static SimulationOverrides overrides(CommandContext<ServerCommandSource> ctx) {
        return ServerClimateSynchronizer.get(ctx.getSource().getWorld());
    }

    private static void changed(CommandContext<ServerCommandSource> ctx, String message) {
        ServerClimateSynchronizer.broadcast(ctx.getSource().getWorld());
        ctx.getSource().sendFeedback(() -> Text.literal("[Living World] ").formatted(Formatting.AQUA)
                .append(Text.literal(message).formatted(Formatting.WHITE)), true);
    }

    private static int setWeather(CommandContext<ServerCommandSource> ctx, WeatherType type, float intensity, int seconds) {
        ServerWorld world = ctx.getSource().getWorld();
        SimulationOverrides o = ServerClimateSynchronizer.get(world);
        o.weather = type;
        o.weatherIntensity = intensity;
        o.weatherUntil = world.getTime() + seconds * 20L;
        changed(ctx, String.format(Locale.ROOT,
                "Simulating %s (intensity %.2f) for %d s. Visual simulation only — the real Minecraft weather is unchanged.",
                type.id(), intensity, seconds));
        return 1;
    }

    private static int clearWeather(CommandContext<ServerCommandSource> ctx) {
        SimulationOverrides o = overrides(ctx);
        o.weather = null;
        changed(ctx, "Simulated weather cleared; following the real world weather again.");
        return 1;
    }

    private static int setRealWeather(CommandContext<ServerCommandSource> ctx, String kind, int seconds) {
        ServerWorld world = ctx.getSource().getWorld();
        int ticks = seconds * 20;
        switch (kind) {
            case "rain" -> world.setWeather(0, ticks, true, false);
            case "thunder" -> world.setWeather(0, ticks, true, true);
            default -> world.setWeather(ticks, 0, false, false);
        }
        ctx.getSource().sendFeedback(() -> Text.literal("[Living World] ").formatted(Formatting.AQUA)
                .append(Text.literal("REAL world weather set to " + kind + " for " + seconds + " s (same effect as /weather).")
                        .formatted(Formatting.YELLOW)), true);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ServerClimate.Reading r = ServerClimate.read(player, new ServerClimate.Reading());
        SimulationOverrides o = ServerClimateSynchronizer.get(player.getServerWorld());
        long time = player.getServerWorld().getTime();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "%s | air %.1f°C (feels %.1f°C) | wind %.2f | humidity %.0f%% | %s%s",
                r.biome, r.ambientC, r.feelsLikeC, r.windSpeed, r.humidity * 100f, r.weather.id(),
                r.simulatedWeather ? " (simulated)" : ""));
        if (o.hasTemperature()) {
            sb.append(" | temperature override");
        }
        if (o.hasWind()) {
            sb.append(" | wind override");
        }
        int forecast = o.ticksUntilRain(time);
        if (forecast >= 0) {
            sb.append(String.format(Locale.ROOT, " | rain in %d s", forecast / 20));
        }
        sb.append(ServerConfig.get().temperatureEffects ? " | survival rules ON" : " | survival rules off");
        String text = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal("[Living World] ").formatted(Formatting.AQUA)
                .append(Text.literal(text).formatted(Formatting.WHITE)), false);
        return 1;
    }

    private static int clientCommand(CommandContext<ServerCommandSource> ctx, String action, int argument) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if (!LWNetworking.sendClientCommand(player, action, argument)) {
            ctx.getSource().sendError(Text.literal("Living World is not installed on your client."));
            return 0;
        }
        return 1;
    }
}
