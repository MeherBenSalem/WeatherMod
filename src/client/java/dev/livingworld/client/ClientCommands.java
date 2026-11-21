package dev.livingworld.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.livingworld.client.debug.Benchmark;
import dev.livingworld.client.debug.DebugHud;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.gui.LWConfigScreen;
import dev.livingworld.client.player.PlayerExposure;
import dev.livingworld.climate.Seasons;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.climate.WeatherType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * {@code /lw ...}: the client-only twin of {@code /livingworld}. It works on any server, including
 * vanilla ones, because it only changes what this client renders: weather, temperature and wind set
 * here are a private simulation that nobody else sees and that never touches the world.
 */
public final class ClientCommands {
    private ClientCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("lw");

        LiteralArgumentBuilder<FabricClientCommandSource> weather = literal("weather");
        weather.then(literal("clear").executes(ctx -> {
            local().weather = null;
            say(ctx.getSource(), "Local weather simulation cleared.");
            return 1;
        }));
        for (WeatherType type : WeatherType.values()) {
            String name = type == WeatherType.CLEAR ? "clear_sky" : type.id();
            weather.then(literal(name)
                    .executes(ctx -> localWeather(ctx.getSource(), type, 1f, 600))
                    .then(argument("intensity", FloatArgumentType.floatArg(0.1f, 1.5f))
                            .executes(ctx -> localWeather(ctx.getSource(), type, FloatArgumentType.getFloat(ctx, "intensity"), 600))
                            .then(argument("seconds", IntegerArgumentType.integer(1, 86400))
                                    .executes(ctx -> localWeather(ctx.getSource(), type, FloatArgumentType.getFloat(ctx, "intensity"),
                                            IntegerArgumentType.getInteger(ctx, "seconds"))))));
        }
        root.then(weather);

        root.then(literal("temperature")
                .then(literal("set").then(argument("celsius", FloatArgumentType.floatArg(-60f, 80f)).executes(ctx -> {
                    local().temperatureC = FloatArgumentType.getFloat(ctx, "celsius");
                    say(ctx.getSource(), String.format(Locale.ROOT, "Local temperature simulation: %.1f°C (only you see this).", local().temperatureC));
                    return 1;
                })))
                .then(literal("clear").executes(ctx -> {
                    local().temperatureC = Float.NaN;
                    say(ctx.getSource(), "Local temperature simulation cleared.");
                    return 1;
                })));

        root.then(literal("wind")
                .then(literal("set").then(argument("strength", FloatArgumentType.floatArg(0f, 1.6f)).executes(ctx -> {
                    local().wind = FloatArgumentType.getFloat(ctx, "strength");
                    say(ctx.getSource(), String.format(Locale.ROOT, "Local wind simulation: %.2f (only you see this).", local().wind));
                    return 1;
                })))
                .then(literal("clear").executes(ctx -> {
                    local().wind = -1f;
                    say(ctx.getSource(), "Local wind simulation cleared.");
                    return 1;
                })));

        LiteralArgumentBuilder<FabricClientCommandSource> season = literal("season").executes(ctx -> {
            say(ctx.getSource(), describeSeason());
            return 1;
        });
        LiteralArgumentBuilder<FabricClientCommandSource> seasonSet = literal("set");
        for (Seasons.Season s : Seasons.Season.VALUES) {
            seasonSet.then(literal(s.id()).executes(ctx -> {
                ClientEnvironment e = ClientEnvironment.INSTANCE;
                float days = e.seasonDays > 0f ? e.seasonDays : Seasons.DEFAULT_DAYS_PER_SEASON;
                e.localSeasonOffsetDays = Seasons.offsetFor(s, e.timeOfDay, days);
                say(ctx.getSource(), "Your own calendar now stands in the middle of " + s.id() + " (only you see this).");
                return 1;
            }));
        }
        season.then(seasonSet);
        season.then(literal("clear").executes(ctx -> {
            ClientEnvironment.INSTANCE.localSeasonOffsetDays = Float.NaN;
            say(ctx.getSource(), "Local calendar cleared; following the world's seasons again.");
            return 1;
        }));
        root.then(season);

        LiteralArgumentBuilder<FabricClientCommandSource> debug = literal("debug");
        for (String mode : new String[]{"atmosphere", "footprints", "audio", "off"}) {
            debug.then(literal(mode).executes(ctx -> {
                run("debug_" + mode, 0);
                return 1;
            }));
        }
        root.then(debug);
        root.then(literal("benchmark")
                .executes(ctx -> {
                    run("benchmark", 20);
                    return 1;
                })
                .then(argument("seconds", IntegerArgumentType.integer(6, 300)).executes(ctx -> {
                    run("benchmark", IntegerArgumentType.getInteger(ctx, "seconds"));
                    return 1;
                })));
        root.then(literal("config").executes(ctx -> {
            run("config", 0);
            return 1;
        }));
        root.then(literal("status").executes(ctx -> {
            ClientEnvironment e = ClientEnvironment.INSTANCE;
            say(ctx.getSource(), String.format(Locale.ROOT, "%s | air %.1f°C (feels %.1f°C) | wind %.2f | humidity %.0f%% | %s | %s",
                    e.dominant, e.ambientC, PlayerExposure.INSTANCE.feelsLikeC, e.windLocal, e.humidity * 100f, e.weatherType.id(),
                    describeSeason()));
            return 1;
        }));

        dispatcher.register(root);
    }

    /** "autumn, day 3 of 8", or why there is no season here. */
    public static String describeSeason() {
        ClientEnvironment e = ClientEnvironment.INSTANCE;
        if (!e.seasonsActive) {
            return "no seasons here";
        }
        float days = e.seasonDays;
        return String.format(Locale.ROOT, "%s, day %d of %s%s", e.season.id(), Seasons.dayOfSeason(e.yearPhase, days),
                days == Math.rint(days) ? String.valueOf((int) days) : String.format(Locale.ROOT, "%.2f", days),
                Float.isNaN(e.localSeasonOffsetDays) ? "" : " (your own calendar)");
    }

    private static SimulationOverrides local() {
        return ClientEnvironment.INSTANCE.local;
    }

    private static int localWeather(FabricClientCommandSource source, WeatherType type, float intensity, int seconds) {
        SimulationOverrides o = local();
        o.weather = type;
        o.weatherIntensity = intensity;
        o.weatherUntil = ClientEnvironment.INSTANCE.worldTime + seconds * 20L;
        say(source, String.format(Locale.ROOT, "Simulating %s locally for %d s. Only you see this; the real weather is unchanged.", type.id(), seconds));
        return 1;
    }

    private static void say(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal("[Living World] ").formatted(Formatting.AQUA).append(Text.literal(message).formatted(Formatting.WHITE)));
    }

    /** Runs a local action. Also the entry point for the server's {@code client_command} packet. */
    public static void run(String action, int argument) {
        MinecraftClient client = MinecraftClient.getInstance();
        switch (action) {
            case "debug_atmosphere" -> DebugHud.mode = DebugHud.Mode.ATMOSPHERE;
            case "debug_footprints" -> DebugHud.mode = DebugHud.Mode.FOOTPRINTS;
            case "debug_audio" -> DebugHud.mode = DebugHud.Mode.AUDIO;
            case "debug_off" -> DebugHud.mode = DebugHud.Mode.OFF;
            case "benchmark" -> Benchmark.start(argument);
            // Opening a screen from inside the chat screen's own command handling would be closed again immediately.
            case "config" -> client.send(() -> client.setScreen(new LWConfigScreen(null)));
            default -> {
            }
        }
    }
}
