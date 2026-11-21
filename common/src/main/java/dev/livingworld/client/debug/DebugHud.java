package dev.livingworld.client.debug;

import dev.livingworld.client.LivingWorldClient;
import dev.livingworld.client.audio.EnvironmentAudio;
import dev.livingworld.client.audio.synth.AmbienceParams;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.footprint.FootprintTracker;
import dev.livingworld.client.particle.ParticleEngine;
import dev.livingworld.client.player.PlayerExposure;
import dev.livingworld.client.render.DecalRenderer;
import dev.livingworld.client.render.LWShaders;
import dev.livingworld.client.render.OverlayRenderer;
import dev.livingworld.client.render.PostProcessor;
import dev.livingworld.client.render.PrecipitationRenderer;
import dev.livingworld.climate.BiomeProfile.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Developer overlays ({@code /livingworld debug ...}) and the optional player-facing thermometer. */
public final class DebugHud {
    public enum Mode {
        OFF, ATMOSPHERE, FOOTPRINTS, AUDIO
    }

    public static Mode mode = Mode.OFF;
    private static final List<String> LINES = new ArrayList<>();

    private DebugHud() {
    }

    public static void render(GuiGraphics context, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        ClientEnvironment e = ClientEnvironment.INSTANCE;
        ClientConfig config = ClientConfig.get();
        if (!e.inWorld || client.options.hideGui) {
            return;
        }
        OverlayRenderer.renderHudFallback(context);
        Font font = client.font;

        if (config.enabled && config.temperatureHud && mode == Mode.OFF && !client.options.renderDebug) {
            float felt = PlayerExposure.INSTANCE.feelsLikeC;
            String text = config.fahrenheit
                    ? String.format(Locale.ROOT, "%.0f°F", felt * 9f / 5f + 32f)
                    : String.format(Locale.ROOT, "%.0f°C", felt);
            int color = felt < 0f ? 0xFF9AD0FF : felt > 32f ? 0xFFFFB070 : 0xFFE8E8E8;
            context.drawString(font, text, 6, 6, color);
        }
        if (config.enabled && config.seasonHud && e.seasonsActive && mode == Mode.OFF && !client.options.renderDebug) {
            drawCalendar(context, font, e, 6, config.temperatureHud ? 19 : 6);
        }
        if (mode == Mode.OFF) {
            return;
        }

        LINES.clear();
        Atmosphere a = Atmosphere.INSTANCE;
        switch (mode) {
            case ATMOSPHERE -> {
                LINES.add("Living World — atmosphere");
                StringBuilder blend = new StringBuilder("biome blend:");
                for (Kind k : Kind.VALUES) {
                    if (e.kind(k) > 0.04f) {
                        blend.append(String.format(Locale.ROOT, " %s %.0f%%", k, e.kind(k) * 100f));
                    }
                }
                LINES.add(blend.toString());
                LINES.add(f("air %.1f°C  feels %.1f°C  heat sources %+.1f  humidity %.0f%%  dust %.2f  foliage %.2f",
                        e.ambientC, PlayerExposure.INSTANCE.feelsLikeC, e.heatSources, e.humidity * 100f, e.dustiness, e.foliage));
                LINES.add(f("wind dir %.0f°  base %.2f  gust %.2f  speed %.2f  felt %.2f (exposure %.2f)",
                        Math.toDegrees(e.wind.angle) % 360.0, e.wind.base, e.wind.gust, e.wind.speed, e.windLocal, e.windExposure));
                String forced = e.local.weather != null ? " [client override]" : e.server.hasWeather(e.worldTime) ? " [server override]" : "";
                int forecast = e.server.ticksUntilRain(e.worldTime);
                LINES.add(f("weather %s%s  precip %.2f (rain %.2f snow %.2f)  sand %.2f  storm %.2f  clouds %.2f%s",
                        e.weatherType.id(), forced, e.precip, e.rain, e.snow, e.sand, e.storm, e.cloudCover,
                        forecast >= 0 ? "  rain in " + forecast / 20 + "s" : ""));
                LINES.add(f("year: %s  phase %.3f  seasonality %.2f  offset %+.1f°C  autumn %.2f  winter %.2f  spring %.2f  lying snow %.2f",
                        dev.livingworld.client.ClientCommands.describeSeason(), e.yearPhase, e.seasonality, e.seasonOffsetC, e.autumn,
                        e.winter, e.spring, a.lyingSnow));
                LINES.add(f("sky: clouds %s  cover %.2f  thickness %.0f  shadow %.2f  stars %.2f  aurora %.2f  rainbow %.2f",
                        a.cloudsDrawn ? "drawn" : "vanilla", a.cloudCoverage, a.cloudThickness, a.cloudShadow, a.starlight, a.aurora,
                        a.rainbow));
                LINES.add(f("ground wet %.2f  fresh snow %.2f  player wet %.2f  cold %.2f  heat %.2f  frost %.2f  lens %.2f",
                        e.wetness, e.snowCover, PlayerExposure.INSTANCE.wet, PlayerExposure.INSTANCE.cold, PlayerExposure.INSTANCE.heat,
                        PlayerExposure.INSTANCE.frost, PlayerExposure.INSTANCE.lensDrops));
                LINES.add(f("shelter: open %.2f  roof %.2f  canopy %.2f  cave %.2f  sky light %.2f  rain exposure %.2f",
                        e.openness, e.roof, e.canopy, e.cave, e.skyLight, e.rainExposure));
                LINES.add(f("haze %.4f/block (%.2f %.2f %.2f)  mist %.4f  visibility %s  shimmer %.2f  exposure %.2f  saturation %.2f",
                        a.hazeDensity, a.hazeR, a.hazeG, a.hazeB, a.mistDensity,
                        a.visibility == Float.MAX_VALUE ? "∞" : f("%.0f", a.visibility), a.heatShimmer, a.exposure, a.saturation));
                LINES.add(f("render: %s  adaptive %.2f  frame %.2f ms  particles %d  drops %d  quads %d  decals %d  post %s",
                        QualityManager.preset(), QualityManager.adaptive(), QualityManager.smoothedFrameMs(), ParticleEngine.INSTANCE.count,
                        PrecipitationRenderer.INSTANCE.lastDrops, LivingWorldClient.batch().lastQuads, DecalRenderer.lastDrawn,
                        PostProcessor.lastRan ? "on" : "off"));
                LINES.add("shaders: " + LWShaders.status() + "  " + Compat.describe());
                for (Perf p : Perf.values()) {
                    LINES.add(f("  %-34s %6.0f µs  (peak %.0f)", p.label, p.micros(), p.peakMicros()));
                }
            }
            case FOOTPRINTS -> {
                FootprintTracker t = FootprintTracker.INSTANCE;
                LINES.add("Living World — footprints");
                LINES.add(f("decals %d / %d  drawn %d  evicted %d  placed %d", t.decals.count, QualityManager.maxDecals(),
                        DecalRenderer.lastDrawn, t.decals.evicted, t.placed));
                LINES.add(f("erosion: wind %.2f  rain %.2f  snow %.2f  sand %.2f  lifetime x%.2f", e.wind.speed, e.rain, e.snow, e.sand,
                        config.footprintLifetime));
                LINES.add(f("ground wetness %.2f (mud above 0.35, puddles above 0.5)", e.wetness));
                LINES.add("lit decals: " + (config.litFootprints && LWShaders.decal != null && Compat.customShadersAllowed()));
            }
            case AUDIO -> {
                LINES.add("Living World — audio");
                AmbienceParams p = EnvironmentAudio.INSTANCE.params();
                LINES.add("beds running: " + EnvironmentAudio.INSTANCE.running());
                if (p != null) {
                    LINES.add(f("wind %.2f  gust %.2f  foliage %.2f  altitude %.2f  sand %.2f  snow %.2f", p.windSpeed, p.windGust, p.foliage,
                            p.altitude, p.sandiness, p.snowfall));
                    LINES.add(f("rain %.2f  on leaves %.2f stone %.2f water %.2f wood %.2f soft %.2f  roofed %.2f", p.rain, p.rainOnLeaves,
                            p.rainOnStone, p.rainOnWater, p.rainOnWood, p.rainOnSoft, p.roofed));
                    LINES.add(f("openness %.2f  cave %.2f  drips %.2f  underwater %.2f", p.openness, p.cave, p.drips, p.underwater));
                    LINES.add(f("birds %.2f  insects %.2f  crickets %.2f  frogs %.2f  surf %.2f  nether %.2f  end %.2f", p.birds, p.insects,
                            p.crickets, p.frogs, p.surf, p.netherRumble, p.endDrone));
                }
            }
            default -> {
            }
        }
        int y = 4;
        for (String line : LINES) {
            int w = font.width(line);
            context.fill(2, y - 1, 6 + w, y + 9, 0x90000000);
            context.drawString(font, line, 4, y, 0xFFE6F2F5, false);
            y += 10;
        }
    }

    /** Spring green, summer gold, autumn orange, winter ice. */
    private static final int[] SEASON_COLOURS = {0xFF7CC66F, 0xFFE9C546, 0xFFD97A35, 0xFFAED3EC};

    /**
     * The calendar: the season and the day within it, over a bar that is the whole year, a quarter for
     * each season, with a mark where today falls.
     */
    private static void drawCalendar(GuiGraphics context, Font font, ClientEnvironment e, int x, int y) {
        int season = e.season.ordinal();
        float days = e.seasonDays;
        String length = days == Math.rint(days) ? String.valueOf((int) days) : String.format(Locale.ROOT, "%.1f", days);
        String label = net.minecraft.network.chat.Component.translatable("livingworld.season." + e.season.id()).getString() + " \u00b7 "
                + net.minecraft.network.chat.Component.translatable("livingworld.season.day",
                dev.livingworld.climate.Seasons.dayOfSeason(e.yearPhase, days), length).getString();
        int width = Math.max(88, font.width(label) + 6);
        context.fill(x - 3, y - 3, x + width + 3, y + 19, 0x78000000);
        context.drawString(font, label, x, y, SEASON_COLOURS[season]);
        int barY = y + 12;
        for (int i = 0; i < 4; i++) {
            int x0 = x + width * i / 4, x1 = x + width * (i + 1) / 4 - 1;
            int colour = i == season ? SEASON_COLOURS[i] : (SEASON_COLOURS[i] & 0x00FFFFFF) | 0x70000000;
            context.fill(x0, barY, x1, barY + 4, colour);
        }
        int mark = x + Math.min(width - 1, Math.round(e.yearPhase * width));
        context.fill(mark - 1, barY - 1, mark + 1, barY + 5, 0xFFFFFFFF);
    }

    private static String f(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
