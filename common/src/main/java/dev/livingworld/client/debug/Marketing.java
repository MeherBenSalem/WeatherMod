package dev.livingworld.client.debug;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityPreset;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.gui.LWConfigScreen;
import dev.livingworld.config.ServerConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import static dev.livingworld.client.debug.AutoTest.clip;
import static dev.livingworld.client.debug.AutoTest.cmd;
import static dev.livingworld.client.debug.AutoTest.faceOpen;
import static dev.livingworld.client.debug.AutoTest.hud;
import static dev.livingworld.client.debug.AutoTest.look;
import static dev.livingworld.client.debug.AutoTest.note;
import static dev.livingworld.client.debug.AutoTest.perspective;
import static dev.livingworld.client.debug.AutoTest.run;
import static dev.livingworld.client.debug.AutoTest.shot;
import static dev.livingworld.client.debug.AutoTest.tpBiome;
import static dev.livingworld.client.debug.AutoTest.turn;
import static dev.livingworld.client.debug.AutoTest.waitTicks;
import static dev.livingworld.client.debug.AutoTest.walk;

/**
 * The pictures for the mod's listing pages: {@code gradlew runAutotest -PlwScenes=marketing}, or a few
 * groups with {@code -PlwScenes=mk_water,mk_sky}. Every screenshot is one unedited 1920x1080 frame of
 * the game on the Ultra preset, saved to {@code run/livingworld-autotest/screenshots/mk_*.png}; runs of
 * frames for the animations go to {@code run/livingworld-autotest/clips/mk_*}.
 * {@code marketing/source/collect.py} turns them into the files under {@code marketing/images}.
 *
 * <p>The time of day, the weather, the wind, the season and the amount of fair-weather cloud are set
 * with commands so each picture happens on cue. Everything the mod draws is what it draws in normal
 * play. See {@code marketing/README.md} for what each picture shows and how it was staged.
 */
final class Marketing {
    private Marketing() {
    }

    private static boolean on(String group) {
        return AutoTest.selected("marketing") || AutoTest.selected(group);
    }

    /** Time for the chunks around a new place to load and be meshed, and for the sky to settle. */
    private static void settle() {
        waitTicks(260);
    }

    /** Stands the player on a pillar of the given height (hidden under the feet in first person). */
    private static void pillar(int height) {
        cmd("execute at @s run fill ~ ~ ~ ~ ~" + (height - 1) + " ~ minecraft:stone");
        cmd("execute at @s run tp @s ~ ~" + height + " ~");
    }

    private static void cover(float fair) {
        run(() -> Atmosphere.forceFairCover = fair);
    }

    static void build() {
        cmd("gamerule doFireTick false");
        cmd("gamerule doMobSpawning false");
        run(() -> {
            ClientConfig c = ClientConfig.get();
            c.preset = QualityPreset.ULTRA;
            c.adaptiveQuality = false;
        });
        AutoTest.borderless(1920, 1080);
        hud(true);

        if (on("mk_water")) {
            water();
        }
        if (on("mk_sky")) {
            sky();
        }
        if (on("mk_storm")) {
            storm();
        }
        if (on("mk_seasons")) {
            seasons();
        }
        if (on("mk_calendar")) {
            calendar();
        }
        if (on("mk_winter")) {
            winter();
        }
        if (on("mk_weather")) {
            weather();
        }
        if (on("mk_world")) {
            world();
        }
        if (on("mk_puddles")) {
            puddles();
        }
        if (on("mk_ui")) {
            ui();
        }

        run(() -> {
            ClientConfig c = ClientConfig.get();
            c.preset = QualityPreset.BALANCED;
            c.adaptiveQuality = true;
            c.seasonHud = false;
            c.frostOverlay = true;
            Atmosphere.forceFairCover = -1f;
            Atmosphere.forceAurora = -1f;
        });
    }

    /** The sea from a beach that faces west: noon, a breeze, the surf, sunset over the water, rain. */
    private static void water() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("time set 6000");
        cmd("livingworld weather clear");
        cmd("livingworld season set summer");
        AutoTest.tpShore("minecraft:beach", 90f, 6f);
        cover(0.3f);
        cmd("livingworld wind set 0.3");
        settle();
        note("mk_water_noon");
        shot("mk_water_noon");
        look(90f, 32f);
        waitTicks(12);
        shot("mk_water_shallows");
        look(50f, 14f);
        waitTicks(12);
        shot("mk_water_noon_left");
        look(130f, 14f);
        waitTicks(12);
        shot("mk_water_noon_right");

        cmd("livingworld wind set 0.85");
        look(90f, 20f);
        waitTicks(180);
        note("mk_water_surf");
        shot("mk_water_surf");
        look(45f, 24f);
        waitTicks(12);
        shot("mk_water_surf_left");
        look(135f, 24f);
        waitTicks(12);
        shot("mk_water_surf_right");
        look(90f, 12f);
        waitTicks(12);
        clip("mk_water_wind", 50, 2);

        cmd("livingworld wind set 0.3");
        cmd("time set 11750");
        look(90f, 3f);
        waitTicks(200);
        note("mk_water_sunset");
        shot("mk_water_sunset_early");
        cmd("time set 12150");
        waitTicks(120);
        shot("mk_water_sunset");
        clip("mk_water_sunset", 50, 2);
        cmd("time set 12600");
        waitTicks(120);
        shot("mk_water_dusk");

        cmd("time set 6000");
        cmd("livingworld weather rain");
        look(90f, 20f);
        waitTicks(280);
        note("mk_water_rain");
        shot("mk_water_rain");
        clip("mk_water_rain", 40, 2);
        cmd("livingworld weather clear");
        cmd("livingworld wind clear");
        waitTicks(100);
    }

    /** The sky from a pillar over the plains: clouds and their shadows, a time-lapse of dusk, sunset, night, an aurora. */
    private static void sky() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("livingworld weather clear");
        cmd("livingworld season set summer");
        cmd("time set 6000");
        tpBiome("minecraft:plains", 0f, -16f);
        pillar(32);
        cover(0.36f);
        settle();
        note("mk_sky_noon");
        shot("mk_sky_noon");
        look(90f, -16f);
        waitTicks(12);
        shot("mk_sky_noon_west");
        look(0f, 26f);
        waitTicks(12);
        shot("mk_sky_cloud_shadows");
        look(180f, -8f);
        waitTicks(12);
        shot("mk_sky_noon_north");

        // Late afternoon to the first stars, looking west.
        look(90f, -9f);
        cmd("time set 11300");
        waitTicks(160);
        AutoTest.timelapse("mk_dusk", 60, 4, 11300, 13700);
        cmd("time set 12250");
        waitTicks(160);
        note("mk_sky_sunset");
        shot("mk_sky_sunset");
        look(-90f, -10f);
        waitTicks(12);
        shot("mk_sky_sunset_east");

        cmd("time set 18000");
        cover(0.14f);
        look(0f, -32f);
        waitTicks(200);
        note("mk_sky_night");
        shot("mk_sky_night");
        look(140f, -50f);
        waitTicks(12);
        shot("mk_sky_night_overhead");
        look(90f, -24f);
        waitTicks(12);
        shot("mk_sky_night_west");

        cmd("time set 6000");
        tpBiome("minecraft:snowy_plains", 0f, -28f);
        pillar(10);
        cmd("time set 18000");
        run(() -> {
            Atmosphere.forceAurora = 1f;
            Atmosphere.forceFairCover = 0.1f;
        });
        settle();
        note("mk_sky_aurora");
        shot("mk_sky_aurora");
        clip("mk_aurora", 50, 2);
        look(180f, -24f);
        waitTicks(12);
        shot("mk_sky_aurora_north");
        look(90f, -30f);
        waitTicks(12);
        shot("mk_sky_aurora_west");
        run(() -> {
            Atmosphere.forceAurora = -1f;
            Atmosphere.forceFairCover = -1f;
        });
        cmd("time set 6000");
    }

    /** A thunderstorm over the plains, and strokes of lightning at dusk on the land ahead. */
    private static void storm() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("livingworld weather clear");
        cmd("livingworld season set summer");
        cmd("time set 6000");
        tpBiome("minecraft:plains", 0f, -12f);
        pillar(32);
        cover(-1f);
        cmd("livingworld weather thunderstorm");
        settle();
        waitTicks(120);
        note("mk_storm");
        shot("mk_storm");
        cmd("time set 13300");
        look(0f, 6f);
        waitTicks(100);
        shot("mk_before_lightning");
        // Each stroke comes down on whatever the surface is at that spot, in view.
        String[] strokes = {"~4 ~ ~58", "~-22 ~ ~44", "~16 ~ ~88", "~-6 ~ ~36"};
        for (int i = 0; i < strokes.length; i++) {
            cmd("execute at @s positioned " + strokes[i] + " positioned over motion_blocking run summon minecraft:lightning_bolt ~ ~ ~");
            waitTicks(2);
            shot("mk_lightning_" + (i + 1) + "a");
            waitTicks(2);
            shot("mk_lightning_" + (i + 1) + "b");
            waitTicks(70);
        }
        cmd("livingworld weather clear");
        cmd("time set 6000");
        waitTicks(60);
    }

    /** One view of a forest through the year. */
    private static void seasons() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("livingworld weather clear");
        cmd("time set 5000");
        cover(0.3f);
        AutoTest.tpOverlook("minecraft:forest", 0f, 6f);
        pillar(4);
        settle();
        String[] names = {"spring", "summer", "autumn", "winter"};
        for (String name : names) {
            cmd("livingworld season set " + name);
            waitTicks(180);
            note("mk_season_" + name);
            shot("mk_season_" + name);
        }
        cmd("livingworld season set summer");
        waitTicks(60);
    }

    /** The calendar, as a player sees it: the same forest in autumn with the HUD on. */
    private static void calendar() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("livingworld weather clear");
        cmd("time set 5000");
        cover(0.3f);
        AutoTest.tpOverlook("minecraft:forest", 0f, 6f);
        pillar(4);
        cmd("livingworld season set autumn");
        run(() -> ClientConfig.get().seasonHud = true);
        settle();
        hud(false);
        waitTicks(20);
        shot("mk_calendar_hud");
        hud(true);
        run(() -> ClientConfig.get().seasonHud = false);
        cmd("livingworld season set summer");
        waitTicks(60);
    }

    /** The opt-in rule: winter lays real snow and freezes still water, and the thaw takes it back. */
    private static void winter() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("livingworld weather clear");
        cmd("time set 5000");
        cover(0.3f);
        cmd("livingworld season set summer");
        tpBiome("minecraft:taiga", 0f, 10f);
        AutoTest.tpShore("minecraft:river", 0f, 12f);
        // The frost that creeps over the edge of the view in this cold would frame every picture in white.
        run(() -> ClientConfig.get().frostOverlay = false);
        settle();
        shot("mk_winter_before");
        run(() -> ServerConfig.get().seasonalChangeRate = 256);
        cmd("livingworld season snow on");
        cmd("livingworld season set winter");
        cmd("livingworld realweather rain 900");
        waitTicks(800);
        note("mk_winter_snowing");
        shot("mk_winter_snowing");
        cmd("livingworld realweather clear");
        waitTicks(200);
        note("mk_winter_frozen");
        shot("mk_winter_frozen");
        look(0f, 30f);
        waitTicks(12);
        shot("mk_winter_frozen_close");
        look(-40f, 14f);
        waitTicks(12);
        shot("mk_winter_frozen_left");
        look(40f, 14f);
        waitTicks(12);
        shot("mk_winter_frozen_right");
        AutoTest.run(() -> AutoTest.log("blocks placed by winter: " + Scenes.seasonalBlocks()));
        cmd("livingworld season snow off");
        run(() -> ServerConfig.get().seasonalChangeRate = 16);
        run(() -> ClientConfig.get().frostOverlay = true);
        cmd("livingworld season set summer");
        waitTicks(60);
    }

    /** Rain in the forest and what it leaves, a sandstorm, a blizzard, desert heat, a misty dawn. */
    private static void weather() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("livingworld weather clear");
        cmd("livingworld season set summer");
        cover(-1f);

        cmd("time set 23600");
        tpBiome("minecraft:forest", -90f, -3f);
        waitTicks(60);
        faceOpen(-90f, 70f, -3f);
        settle();
        note("mk_dawn_mist");
        shot("mk_dawn_mist");

        cmd("time set 5000");
        faceOpen(0f, 180f, 6f);
        waitTicks(60);
        cmd("livingworld weather heavy_rain");
        waitTicks(320);
        note("mk_rain_forest");
        shot("mk_rain_forest");
        clip("mk_rain", 40, 2);
        cmd("livingworld weather clear");
        waitTicks(100);

        cmd("time set 6000");
        tpBiome("minecraft:desert", 0f, 1f);
        settle();
        note("mk_desert_heat");
        shot("mk_desert_heat");
        cmd("livingworld weather sandstorm 0.5");
        waitTicks(220);
        shot("mk_sandstorm_rising");
        cmd("livingworld weather sandstorm 1.0");
        waitTicks(220);
        note("mk_sandstorm");
        shot("mk_sandstorm");
        perspective(CameraType.THIRD_PERSON_BACK);
        look(0f, 8f);
        waitTicks(20);
        shot("mk_sandstorm_figure");
        perspective(CameraType.FIRST_PERSON);
        cmd("livingworld weather clear");

        cmd("time set 3500");
        tpBiome("minecraft:snowy_plains", 0f, 4f);
        waitTicks(60);
        faceOpen(0f, 180f, 4f);
        settle();
        shot("mk_snowy_plains");
        cmd("livingworld weather blizzard");
        waitTicks(320);
        note("mk_blizzard");
        shot("mk_blizzard");
        cmd("livingworld weather clear");
        waitTicks(60);
    }

    /**
     * Puddles need ground that holds water, and grass does not hold much. Two places that do: a stony
     * shore as the world made it, and a paved yard laid for the purpose among sunflowers (stone bricks,
     * cobblestone, andesite, two lantern posts and a low wall).
     */
    private static void puddles() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("livingworld weather clear");
        cmd("livingworld season set summer");
        cover(0.3f);

        cmd("time set 6000");
        tpBiome("minecraft:stony_shore", 0f, 16f);
        waitTicks(60);
        faceOpen(0f, 180f, 16f);
        settle();
        cmd("livingworld weather heavy_rain");
        waitTicks(700);
        note("mk_puddles_shore_rain");
        shot("mk_puddles_shore_rain");
        cmd("livingworld weather clear");
        waitTicks(170);
        note("mk_puddles_shore");
        shot("mk_puddles_shore");
        turn(0f, 30f);
        waitTicks(12);
        shot("mk_puddles_shore_down");

        cmd("time set 6000");
        tpBiome("minecraft:sunflower_plains", 0f, 16f);
        // The chunks around a place just arrived at are not all loaded yet, and a fill that reaches into
        // one that is not does nothing at all.
        waitTicks(80);
        cmd("execute at @s run fill ~-11 ~-1 ~2 ~11 ~-1 ~27 minecraft:stone_bricks");
        cmd("execute at @s run fill ~-11 ~ ~2 ~11 ~7 ~27 minecraft:air");
        cmd("execute at @s run fill ~-7 ~-1 ~6 ~-2 ~-1 ~11 minecraft:cobblestone");
        cmd("execute at @s run fill ~3 ~-1 ~13 ~8 ~-1 ~19 minecraft:polished_andesite");
        cmd("execute at @s run fill ~-9 ~-1 ~17 ~-4 ~-1 ~22 minecraft:mossy_stone_bricks");
        cmd("execute at @s run fill ~4 ~-1 ~4 ~9 ~-1 ~8 minecraft:cracked_stone_bricks");
        cmd("execute at @s run fill ~-11 ~ ~28 ~11 ~ ~28 minecraft:stone_brick_wall");
        cmd("execute at @s run fill ~-9 ~ ~14 ~-9 ~2 ~14 minecraft:oak_fence");
        cmd("execute at @s run setblock ~-9 ~3 ~14 minecraft:lantern");
        cmd("execute at @s run fill ~9 ~ ~22 ~9 ~2 ~22 minecraft:oak_fence");
        cmd("execute at @s run setblock ~9 ~3 ~22 minecraft:lantern");
        look(0f, 16f);
        settle();
        cmd("livingworld weather heavy_rain");
        waitTicks(700);
        note("mk_puddles_rain");
        shot("mk_puddles_rain");
        cmd("livingworld weather clear");
        waitTicks(170);
        note("mk_puddles_noon");
        shot("mk_puddles_noon");
        // The same yard again, wet, late in the day.
        cmd("livingworld weather heavy_rain");
        cmd("time set 11500");
        waitTicks(400);
        cmd("livingworld weather clear");
        waitTicks(170);
        note("mk_puddles_sunset");
        shot("mk_puddles_sunset");
        look(0f, 9f);
        waitTicks(12);
        shot("mk_puddles_sunset_glancing");
        cmd("time set 6000");
        waitTicks(60);
    }

    /** Things that answer the player: grass in the wind, tracks in sand and snow. */
    private static void world() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("livingworld weather clear");
        cmd("livingworld season set summer");
        cover(0.3f);

        cmd("time set 2500");
        tpBiome("minecraft:plains", 0f, 24f);
        AutoTest.plantMeadow(12, 3, 24);
        // From a little above head height, so the field is seen as a field and not as the nearest tuft.
        pillar(3);
        cmd("livingworld wind set 1.0");
        settle();
        note("mk_meadow_wind");
        shot("mk_meadow_wind");
        clip("mk_meadow", 50, 2);
        look(0f, 14f);
        waitTicks(12);
        shot("mk_meadow_wind_far");
        cmd("livingworld wind clear");

        cmd("time set 8500");
        tpBiome("minecraft:desert", 0f, -18f);
        settle();
        perspective(CameraType.THIRD_PERSON_FRONT);
        walk(70, false);
        shot("mk_footprints_walk");
        perspective(CameraType.FIRST_PERSON);
        turn(180f, 34f);
        waitTicks(12);
        note("mk_footprints_sand");
        shot("mk_footprints_sand");

        cmd("time set 3500");
        tpBiome("minecraft:snowy_plains", 0f, -18f);
        settle();
        perspective(CameraType.THIRD_PERSON_FRONT);
        walk(60, false);
        shot("mk_snow_walk");
        perspective(CameraType.FIRST_PERSON);
        turn(180f, 34f);
        waitTicks(12);
        note("mk_snow_trail");
        shot("mk_snow_trail");
        cmd("time set 6000");
    }

    /** The settings screen. */
    private static void ui() {
        AutoTest.tpDimension("minecraft:overworld", 0, 0, 0f, 0f);
        cmd("time set 6000");
        cmd("livingworld weather clear");
        AutoTest.tpShore("minecraft:beach", 90f, 6f);
        settle();
        // The command feedback above would show through the screen.
        run(() -> Minecraft.getInstance().gui.getChat().clearMessages(true));
        String[] pages = {"WATER", "FOG", "CLIMATE", "GRAPHICS"};
        for (String page : pages) {
            run(() -> Minecraft.getInstance().setScreen(
                    new LWConfigScreen(null).show(dev.livingworld.client.config.Opt.Category.valueOf(page))));
            waitTicks(12);
            shot("mk_settings_" + page.toLowerCase(java.util.Locale.ROOT));
            run(() -> Minecraft.getInstance().setScreen(null));
            waitTicks(4);
        }
    }
}
