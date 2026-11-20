package dev.livingworld.client.debug;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityPreset;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.gui.LWConfigScreen;
import dev.livingworld.client.particle.AmbientSpawner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

import static dev.livingworld.client.debug.AutoTest.caption;
import static dev.livingworld.client.debug.AutoTest.cmd;
import static dev.livingworld.client.debug.AutoTest.faceOpen;
import static dev.livingworld.client.debug.AutoTest.gamma;
import static dev.livingworld.client.debug.AutoTest.hud;
import static dev.livingworld.client.debug.AutoTest.look;
import static dev.livingworld.client.debug.AutoTest.note;
import static dev.livingworld.client.debug.AutoTest.panBy;
import static dev.livingworld.client.debug.AutoTest.perspective;
import static dev.livingworld.client.debug.AutoTest.run;
import static dev.livingworld.client.debug.AutoTest.shot;
import static dev.livingworld.client.debug.AutoTest.tpBiome;
import static dev.livingworld.client.debug.AutoTest.tpDimension;
import static dev.livingworld.client.debug.AutoTest.turn;
import static dev.livingworld.client.debug.AutoTest.waitTicks;
import static dev.livingworld.client.debug.AutoTest.walk;

/**
 * A cinematic tour of the mod for a person to watch: {@code gradlew runAutotest -PlwScenes=showcase}.
 * About ten minutes at 1080p on the Ultra preset with sound on. It follows a day through the world —
 * dawn, wind, a storm building, shelter, the clear-up, the desert and its water, the mountains, night —
 * and saves a screenshot of every moment to {@code run/livingworld-autotest/screenshots/sc_*.png}.
 *
 * <p>Weather, wind and time are set with commands so each moment happens on cue, and a few scenes are
 * dressed with blocks (a meadow, a pond, a handful of lamps, lights in a cave); everything the mod
 * draws is the same code path it takes in normal play.
 */
final class Showcase {
    private static final ClientEnvironment ENV = ClientEnvironment.INSTANCE;

    private Showcase() {
    }

    static void build() {
        cmd("gamerule doFireTick false");
        cmd("gamerule doMobSpawning false");
        run(() -> ClientConfig.get().preset = QualityPreset.ULTRA);
        AutoTest.resize(1920, 1080);
        hud(true);

        // ---- morning ----------------------------------------------------------------------------
        caption("Dawn in the forest");
        cmd("time set 23700");
        tpBiome("minecraft:forest", -90f, -4f);
        waitTicks(60);
        // The sun comes up in the east (yaw -90): find the clearest view in that half.
        faceOpen(-90f, 70f, -5f);
        waitTicks(200);
        note("sc_dawn");
        shot("sc_01_forest_dawn");
        panBy(50f, -5f, 6f, 220);
        waitTicks(10);
        shot("sc_02_forest_dawn_mist");

        caption("Wind over the meadow");
        cmd("time set 2000");
        tpBiome("minecraft:plains", 0f, 16f);
        AutoTest.plantMeadow(9, 2, 16);
        cmd("livingworld wind set 1.0");
        waitTicks(180);
        note("sc_meadow");
        shot("sc_03_meadow_wind");
        panBy(70f, 16f, 8f, 200);
        shot("sc_04_meadow_wind_b");
        cmd("livingworld wind clear");

        // ---- the storm --------------------------------------------------------------------------
        caption("Rain arrives");
        cmd("time set 5000");
        tpBiome("minecraft:forest", 0f, 6f);
        waitTicks(60);
        faceOpen(0f, 180f, 6f);
        waitTicks(100);
        cmd("livingworld weather drizzle");
        waitTicks(160);
        note("sc_drizzle");
        shot("sc_05_drizzle");
        cmd("livingworld weather rain");
        waitTicks(140);
        shot("sc_06_rain");
        cmd("livingworld weather heavy_rain");
        waitTicks(160);
        note("sc_heavy_rain");
        shot("sc_07_heavy_rain");
        cmd("livingworld weather torrential");
        waitTicks(240);
        note("sc_torrential");
        shot("sc_08_torrential");
        turn(30f, 40f);
        waitTicks(20);
        shot("sc_09_puddles");

        caption("Running through the mud");
        faceOpen(0f, 180f, -20f);
        perspective(Perspective.THIRD_PERSON_FRONT);
        walk(34, true);
        shot("sc_10_mud_run");
        waitTicks(15);
        perspective(Perspective.FIRST_PERSON);
        turn(180f, 42f);
        waitTicks(12);
        note("sc_mud");
        shot("sc_11_mud_tracks");

        caption("Thunderstorm over the plains");
        tpBiome("minecraft:plains", 0f, -4f);
        cmd("livingworld weather thunderstorm");
        // A pool to watch the rain land on.
        cmd("execute at @s run fill ~-4 ~-3 ~5 ~4 ~-3 ~13 minecraft:stone");
        cmd("execute at @s run fill ~-4 ~-2 ~5 ~4 ~-1 ~13 minecraft:water");
        waitTicks(60);
        look(0f, 30f);
        waitTicks(140);
        note("sc_rain_on_water");
        shot("sc_12_rain_on_water");
        faceOpen(180f, 100f, -8f);
        waitTicks(60);
        cmd("execute at @s rotated ~ 0 run summon minecraft:lightning_bolt ^-9 ^ ^46");
        waitTicks(2);
        shot("sc_13_lightning");
        waitTicks(100);
        note("sc_storm");
        shot("sc_14_storm");
        cmd("execute at @s rotated ~ 0 run summon minecraft:lightning_bolt ^10 ^ ^24");
        waitTicks(2);
        shot("sc_15_lightning_close");
        waitTicks(80);

        caption("Shelter underground");
        cmd("execute at @s run fill ~-4 ~-30 ~-4 ~4 ~-26 ~4 air");
        cmd("execute at @s run setblock ~3 ~-30 ~-3 minecraft:lantern");
        cmd("execute at @s run setblock ~-3 ~-30 ~2 minecraft:campfire");
        cmd("execute at @s run tp @s ~ ~-30 ~");
        look(-135f, 14f);
        waitTicks(220);
        note("sc_shelter");
        shot("sc_16_shelter");
        cmd("livingworld weather clear");
        waitTicks(160);

        caption("After the storm");
        cmd("execute at @s run tp @s ~ ~30 ~");
        look(0f, 22f);
        waitTicks(4);
        shot("sc_17_eyes_adjusting");
        waitTicks(80);
        note("sc_after_storm");
        shot("sc_18_after_storm");
        panBy(70f, 22f, 8f, 160);
        shot("sc_19_after_storm_b");

        // ---- the desert -------------------------------------------------------------------------
        caption("Desert at noon");
        cmd("time set 6000");
        tpBiome("minecraft:desert", 0f, 1f);
        waitTicks(220);
        note("sc_desert");
        shot("sc_20_desert_heat");

        caption("Footprints in the sand");
        look(0f, -24f);
        perspective(Perspective.THIRD_PERSON_FRONT);
        walk(70, false);
        shot("sc_21_desert_walk");
        perspective(Perspective.FIRST_PERSON);
        turn(180f, 40f);
        waitTicks(10);
        note("sc_desert_trail");
        shot("sc_22_desert_trail");

        caption("Things you drop behave like things");
        turn(180f, 44f);
        Scenes.dropItems();
        waitTicks(6);
        shot("sc_23_items_tumbling");
        waitTicks(70);
        shot("sc_24_items_resting");
        cmd("kill @e[type=item,distance=..16]");

        caption("Dust devil");
        look(0f, 2f);
        run(() -> AmbientSpawner.INSTANCE.forceDustDevil(ENV, ENV.camX + 1.5, ENV.camZ + 9.0));
        waitTicks(70);
        shot("sc_25_dust_devil");

        caption("Clear water");
        cmd("execute at @s run tp @s ~44 ~ ~");
        waitTicks(20);
        Scenes.pond();
        look(0f, 14f);
        waitTicks(160);
        note("sc_water");
        shot("sc_26_water");
        panBy(0f, 14f, 50f, 100);
        shot("sc_27_water_shallows");
        panBy(-60f, 50f, 16f, 140);
        shot("sc_28_water_b");

        caption("Sun on the water");
        cmd("time set 11200");
        look(90f, 8f);
        waitTicks(120);
        note("sc_water_evening");
        shot("sc_29_water_evening");
        cmd("livingworld wind set 1.0");
        waitTicks(120);
        shot("sc_30_water_evening_wind");
        cmd("livingworld wind clear");

        caption("Under the surface");
        cmd("time set 6000");
        cmd("execute at @s run tp @s ~2 ~-3.4 ~-3");
        look(20f, 22f);
        waitTicks(16);
        note("sc_underwater");
        shot("sc_31_underwater");
        look(60f, -40f);
        waitTicks(8);
        shot("sc_32_underwater_up");
        cmd("execute at @s run tp @s ~-46 ~4.4 ~3");
        waitTicks(30);

        caption("Sandstorm");
        look(0f, 2f);
        cmd("livingworld weather sandstorm 0.45");
        waitTicks(200);
        note("sc_sandstorm_rising");
        shot("sc_33_sandstorm_rising");
        cmd("livingworld weather sandstorm 1.0");
        waitTicks(200);
        note("sc_sandstorm");
        shot("sc_34_sandstorm");
        perspective(Perspective.THIRD_PERSON_BACK);
        look(0f, 8f);
        waitTicks(20);
        shot("sc_35_sandstorm_figure");
        perspective(Perspective.FIRST_PERSON);

        caption("Lamps in the desert night");
        cmd("livingworld weather clear");
        cmd("time set 18000");
        Scenes.lights();
        look(0f, 12f);
        waitTicks(220);
        note("sc_desert_night");
        shot("sc_36_desert_night_lights");
        look(120f, -14f);
        waitTicks(40);
        shot("sc_37_desert_night_sky");

        // ---- snow -------------------------------------------------------------------------------
        caption("Snowy plains");
        cmd("time set 3500");
        tpBiome("minecraft:snowy_plains", 0f, -24f);
        waitTicks(200);
        perspective(Perspective.THIRD_PERSON_FRONT);
        walk(60, false);
        shot("sc_38_snow_walk");
        waitTicks(30);
        perspective(Perspective.FIRST_PERSON);
        turn(180f, 40f);
        waitTicks(10);
        note("sc_snow");
        shot("sc_39_snow_trail");

        caption("Frozen peaks");
        tpBiome("minecraft:frozen_peaks", 0f, 4f);
        waitTicks(60);
        faceOpen(0f, 180f, 6f);
        waitTicks(200);
        note("sc_peaks");
        shot("sc_40_peaks");

        caption("Blizzard");
        cmd("livingworld weather blizzard");
        waitTicks(300);
        note("sc_blizzard");
        shot("sc_41_blizzard");
        hud(false);
        run(() -> DebugHud.mode = DebugHud.Mode.ATMOSPHERE);
        waitTicks(10);
        shot("sc_42_debug_overlay");
        run(() -> DebugHud.mode = DebugHud.Mode.OFF);
        hud(true);
        cmd("livingworld weather clear");

        // ---- elsewhere --------------------------------------------------------------------------
        caption("Swamp at night");
        cmd("time set 14500");
        tpBiome("minecraft:swamp", 0f, 4f);
        gamma(1.0);
        waitTicks(60);
        AutoTest.faceWater(6f);
        waitTicks(200);
        note("sc_swamp");
        shot("sc_43_swamp_night");
        panBy(90f, 6f, 2f, 160);
        shot("sc_44_swamp_night_b");
        gamma(0.5);

        caption("Jungle downpour");
        cmd("time set 6000");
        tpBiome("minecraft:jungle", 0f, -6f);
        waitTicks(60);
        faceOpen(0f, 180f, -4f);
        waitTicks(120);
        note("sc_jungle");
        shot("sc_45_jungle");
        cmd("livingworld weather torrential");
        waitTicks(260);
        note("sc_jungle_downpour");
        shot("sc_46_jungle_downpour");
        cmd("livingworld weather clear");

        caption("The coast in the evening");
        cmd("time set 11400");
        tpBiome("minecraft:beach", 0f, 4f);
        waitTicks(100);
        AutoTest.faceWater(5f);
        cmd("livingworld wind set 1.2");
        waitTicks(160);
        note("sc_coast");
        shot("sc_47_coast");
        cmd("livingworld wind clear");

        caption("Lush caves");
        cmd("time set 6000");
        AutoTest.tpUnderground("minecraft:lush_caves", 0f, 0f);
        // The cave is pitch dark on its own; a few invisible light blocks stand in for torches.
        cmd("execute at @s run fill ~-7 ~1 ~-7 ~7 ~4 ~7 minecraft:light[level=11] replace #minecraft:air");
        gamma(1.0);
        waitTicks(60);
        faceOpen(0f, 180f, 2f);
        waitTicks(180);
        note("sc_lush_caves");
        shot("sc_48_lush_caves");
        panBy(60f, 2f, -6f, 160);
        shot("sc_49_lush_caves_b");
        gamma(0.5);

        caption("The Nether");
        tpDimension("minecraft:the_nether", 0, 0, 0f, 4f);
        waitTicks(60);
        faceOpen(0f, 180f, 4f);
        waitTicks(180);
        note("sc_nether");
        shot("sc_50_nether");
        panBy(140f, 4f, -4f, 220);
        shot("sc_51_nether_b");

        caption("The End");
        tpDimension("minecraft:the_end", 48, 0, 90f, 0f);
        waitTicks(220);
        note("sc_end");
        shot("sc_52_end");
        panBy(110f, 0f, -10f, 200);
        shot("sc_53_end_b");

        caption("Sunset");
        cmd("time set 12300");
        tpBiome("minecraft:plains", 90f, -3f);
        waitTicks(60);
        // The sun goes down in the west (yaw 90).
        faceOpen(90f, 50f, -3f);
        waitTicks(180);
        note("sc_sunset");
        shot("sc_54_sunset");

        caption("Settings");
        hud(false);
        run(() -> MinecraftClient.getInstance().setScreen(new LWConfigScreen(null)));
        waitTicks(10);
        shot("sc_55_settings");
        run(() -> MinecraftClient.getInstance().setScreen(null));
        run(() -> ClientConfig.get().preset = QualityPreset.BALANCED);
        caption("Finished");
    }
}
