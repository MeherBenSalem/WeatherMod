package dev.livingworld.client.debug;

import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.footprint.FootprintTracker;
import dev.livingworld.client.particle.ParticleEngine;
import dev.livingworld.client.render.PrecipitationRenderer;

import static dev.livingworld.client.debug.AutoTest.cmd;
import static dev.livingworld.client.debug.AutoTest.expect;
import static dev.livingworld.client.debug.AutoTest.hud;
import static dev.livingworld.client.debug.AutoTest.look;
import static dev.livingworld.client.debug.AutoTest.note;
import static dev.livingworld.client.debug.AutoTest.selected;
import static dev.livingworld.client.debug.AutoTest.shot;
import static dev.livingworld.client.debug.AutoTest.tpBiome;
import static dev.livingworld.client.debug.AutoTest.turn;
import static dev.livingworld.client.debug.AutoTest.waitTicks;
import static dev.livingworld.client.debug.AutoTest.walk;

/**
 * The scenario list for {@link AutoTest}. Each scene covers one of the conditions the mod has to get
 * right. Run a subset with {@code -Dlivingworld.autotest.only=desert,snow}.
 */
final class Scenes {
    private static final ClientEnvironment ENV = ClientEnvironment.INSTANCE;

    private Scenes() {
    }

    /** Drops a spread of items (tools, food, blocks, stacks) from head height in front of the player. */
    static void dropItems() {
        String[] drops = {"diamond_sword", "apple", "stone", "oak_planks", "bow", "feather", "golden_pickaxe", "bread"};
        int[] counts = {1, 20, 64, 5, 1, 40, 1, 3};
        for (int i = 0; i < drops.length; i++) {
            cmd(String.format(java.util.Locale.ROOT,
                    "execute at @s run summon item ~%.1f ~3.2 ~%.1f {PickupDelay:32767,Motion:[%.2fd,0.25d,0.05d],Item:{id:\"minecraft:%s\",Count:%db}}",
                    (i % 4 - 1.5) * 0.9, 2.6 + (i / 4) * 1.1, (i % 2 == 0 ? 0.06 : -0.06), drops[i], counts[i]));
        }
    }

    /** A handful of different light sources in front of the player (to the south). */
    static void lights() {
        cmd("execute at @s run setblock ~-2 ~ ~4 minecraft:torch");
        cmd("execute at @s run setblock ~2 ~ ~4 minecraft:lantern");
        cmd("execute at @s run setblock ~ ~ ~6 minecraft:campfire");
        cmd("execute at @s run setblock ~4 ~ ~7 minecraft:glowstone");
        cmd("execute at @s run setblock ~-4 ~ ~7 minecraft:sea_lantern");
    }

    /**
     * Digs a 29x29 pond around the player, who is left standing on a one-block pier in the middle: a deep
     * half, a shallow shelf to the south, a few things on the bed, and a grassy bank with two trees on the
     * far side for the water to mirror.
     */
    static void pond() {
        cmd("execute at @s run fill ~-14 ~-5 ~-14 ~14 ~-5 ~14 minecraft:sand");
        cmd("execute at @s run fill ~-14 ~-4 ~-14 ~14 ~-1 ~14 minecraft:water");
        cmd("execute at @s run fill ~-18 ~ ~-18 ~18 ~9 ~18 minecraft:air");
        cmd("execute at @s run fill ~ ~-4 ~ ~ ~-1 ~ minecraft:oak_planks");
        // The shelf: one block of water over sand, stepping down toward the pier.
        cmd("execute at @s run fill ~-9 ~-4 ~7 ~9 ~-2 ~14 minecraft:sand");
        cmd("execute at @s run fill ~-7 ~-4 ~4 ~7 ~-3 ~6 minecraft:sand");
        cmd("execute at @s run setblock ~3 ~-4 ~2 minecraft:mossy_cobblestone");
        cmd("execute at @s run setblock ~-2 ~-2 ~5 minecraft:prismarine");
        cmd("execute at @s run setblock ~4 ~-1 ~9 minecraft:mossy_cobblestone");
        cmd("execute at @s run setblock ~-5 ~-1 ~8 minecraft:sea_lantern");
        // The far bank.
        cmd("execute at @s run fill ~-18 ~-1 ~15 ~18 ~-1 ~18 minecraft:grass_block");
        cmd("execute at @s run fill ~-7 ~ ~16 ~-7 ~4 ~16 minecraft:oak_log");
        cmd("execute at @s run fill ~-9 ~4 ~14 ~-5 ~6 ~18 minecraft:oak_leaves[persistent=true]");
        cmd("execute at @s run fill ~-8 ~7 ~15 ~-6 ~7 ~17 minecraft:oak_leaves[persistent=true]");
        cmd("execute at @s run fill ~6 ~ ~16 ~6 ~5 ~16 minecraft:birch_log");
        cmd("execute at @s run fill ~4 ~5 ~14 ~8 ~7 ~18 minecraft:birch_leaves[persistent=true]");
        cmd("execute at @s run fill ~5 ~8 ~15 ~7 ~8 ~17 minecraft:birch_leaves[persistent=true]");
        cmd("execute at @s run fill ~-3 ~ ~15 ~2 ~ ~15 minecraft:poppy");
        cmd("execute at @s run fill ~9 ~ ~15 ~13 ~ ~15 minecraft:dandelion");
        cmd("execute at @s run fill ~-13 ~ ~16 ~-10 ~ ~16 minecraft:grass");
        cmd("execute at @s run fill ~14 ~ ~15 ~14 ~2 ~15 minecraft:red_terracotta");
        // A rock with a spring on top, falling straight into the shallows.
        cmd("execute at @s run fill ~10 ~ ~15 ~13 ~5 ~17 minecraft:stone");
        cmd("execute at @s run fill ~10 ~4 ~15 ~13 ~5 ~15 minecraft:mossy_cobblestone");
        cmd("execute at @s run setblock ~11 ~5 ~14 minecraft:water");
    }

    static void build() {
        cmd("time set 6000");
        cmd("weather clear");
        hud(true);

        if (selected("plains")) {
            // A field of tall grass in a strong wind: the sway should roll across it in waves.
            tpBiome("minecraft:plains", 0f, 22f);
            cmd("time set 6000");
            cmd("livingworld wind set 0.9");
            cmd("execute at @s run fill ~-3 ~ ~2 ~3 ~ ~6 minecraft:grass replace air");
            waitTicks(160);
            note("plains_wind");
            shot("20_plains_grass_wind");
            waitTicks(9);
            shot("20_plains_grass_wind_b");
            expect("plant models were tagged for sway", () -> dev.livingworld.client.render.VegetationWind.wrappedModels > 100);
            expect("terrain shader accepts the wind uniforms", dev.livingworld.client.render.VegetationWind::shaderBound);
            cmd("livingworld wind clear");
        }

        if (selected("visuals")) {
            // Dropped items: tumbling in the air, then lying flat / piled / on a face.
            tpBiome("minecraft:desert", 0f, 40f);
            cmd("time set 6000");
            waitTicks(100);
            dropItems();
            waitTicks(7);
            shot("34_items_tumbling");
            waitTicks(70);
            note("items_resting");
            shot("35_items_resting");
            cmd("kill @e[type=item,distance=..16]");

            // Bloom: light sources at night.
            cmd("time set 18000");
            lights();
            look(0f, 14f);
            waitTicks(120);
            note("night_bloom");
            shot("36_night_bloom");
            AutoTest.run(() -> dev.livingworld.client.config.ClientConfig.get().bloom = false);
            waitTicks(6);
            shot("36_night_bloom_off");
            AutoTest.run(() -> dev.livingworld.client.config.ClientConfig.get().bloom = true);

            // Water: a pond around a one-block pier, so there is open water in every direction.
            cmd("execute at @s run tp @s ~40 ~ ~");
            waitTicks(20);
            pond();
            cmd("time set 6000");
            look(0f, 14f);
            waitTicks(120);
            note("water_noon");
            shot("30_water_noon");
            expect("translucent terrain shader accepts the water uniforms", dev.livingworld.client.render.WaterShading::shaderBound);
            expect("the water shader was fed this frame (never under Sodium, which shades water itself)",
                    () -> dev.livingworld.client.render.WaterShading.lastRan != dev.livingworld.client.compat.Compat.SODIUM);
            expect("the terrain column cache follows terrain edits", () -> {
                net.minecraft.client.network.ClientPlayerEntity p = net.minecraft.client.MinecraftClient.getInstance().player;
                float top = ENV.columns.top(p.getBlockX() + 16, p.getBlockZ() - 6);
                AutoTest.log("column top on the cleared bank: " + top + ", feet at " + p.getY());
                return top <= p.getY() + 0.4;
            });
            look(0f, 50f);
            waitTicks(10);
            shot("30b_water_shallows");
            AutoTest.run(() -> dev.livingworld.client.config.ClientConfig.get().waterShading = false);
            look(0f, 14f);
            waitTicks(10);
            shot("30c_water_vanilla");
            AutoTest.run(() -> dev.livingworld.client.config.ClientConfig.get().waterShading = true);
            cmd("time set 11900");
            look(90f, 9f);
            waitTicks(80);
            note("water_sunset");
            shot("31_water_sunset_glitter");
            cmd("time set 14200");
            look(-90f, 9f);
            waitTicks(80);
            shot("32_water_moonrise");
            cmd("livingworld wind set 1.0");
            cmd("time set 9500");
            look(30f, 12f);
            waitTicks(80);
            shot("32b_water_windy_afternoon");
            cmd("livingworld wind clear");

            // Under water: caustics on the floor, the surface from below.
            cmd("time set 6000");
            cmd("execute at @s run tp @s ~2 ~-3.4 ~-3");
            look(20f, 24f);
            waitTicks(14);
            note("underwater");
            shot("33_underwater_caustics");
            expect("the atmosphere pass runs under water", () -> dev.livingworld.client.render.PostProcessor.lastRan);
            look(20f, -35f);
            waitTicks(6);
            shot("33b_underwater_looking_up");
            cmd("execute at @s run tp @s ~-2 ~6 ~3");
            waitTicks(20);
        }

        if (selected("gui")) {
            waitTicks(40);
            AutoTest.run(() -> net.minecraft.client.MinecraftClient.getInstance()
                    .setScreen(new dev.livingworld.client.gui.LWConfigScreen(null)));
            waitTicks(10);
            shot("00_settings_general");
            AutoTest.run(() -> {
                net.minecraft.client.gui.screen.Screen s = net.minecraft.client.MinecraftClient.getInstance().currentScreen;
                if (s != null) {
                    // Click the "Rain & Storms" category in the sidebar.
                    s.mouseClicked(40, 32 + Math.max(11, Math.min(20, (s.height - 78) / 14)) * 2 + 3, 0);
                }
            });
            waitTicks(6);
            shot("00_settings_rain");
            AutoTest.run(() -> net.minecraft.client.MinecraftClient.getInstance().setScreen(null));
            waitTicks(10);
        }

        if (selected("probe")) {
            // Rendering sanity check: unmistakable particles in the air and prints on the ground.
            tpBiome("minecraft:desert", 0f, 25f);
            waitTicks(120);
            AutoTest.run(() -> {
                net.minecraft.client.network.ClientPlayerEntity p = net.minecraft.client.MinecraftClient.getInstance().player;
                for (int i = 0; i < 12; i++) {
                    dev.livingworld.client.particle.Fx.dustPuff(p.getX() + (i - 6) * 0.45, p.getEyeY() - 0.4, p.getZ() + 4.0,
                            0f, 0f, 0f, 1f, 0.25f, 0.2f, 0.4f, 1f, 300f);
                }
                dev.livingworld.client.footprint.DecalStore decals = FootprintTracker.INSTANCE.decals;
                for (int i = 0; i < 12; i++) {
                    decals.add(1000, p.getX() + (i % 6 - 2.5) * 0.7, p.getY(), p.getZ() + 2.2 + (i / 6) * 1.1, 0f, 1f,
                            i < 6 ? 0.16f : 0.3f, 0.3f, i, i % 2 == 1, 1f, 200f, true, false, 0.85f, 0.76f, 0.55f, 0xF000F0, 0f, 0f, 0f);
                }
            });
            waitTicks(6);
            note("probe");
            shot("00_probe");
            AutoTest.run(() -> dev.livingworld.client.config.ClientConfig.get().litFootprints = false);
            waitTicks(4);
            note("probe_simple_decals");
            shot("00_probe_simple_decals");
            AutoTest.run(() -> dev.livingworld.client.config.ClientConfig.get().litFootprints = true);
            cmd("livingworld weather heavy_rain");
            waitTicks(120);
            note("probe_rain");
            shot("00_probe_rain");
            cmd("livingworld weather clear");
            waitTicks(40);
        }

        if (selected("desert")) {
            tpBiome("minecraft:desert", 0f, 15f);
            waitTicks(140);
            note("desert_arrival");
            shot("01_desert_noon");
            walk(45, false);
            walk(45, true);
            waitTicks(8);
            turn(180f, 38f);
            waitTicks(12);
            note("desert_footprints");
            shot("02_desert_footprints");
            expect("walking on sand left footprints", () -> FootprintTracker.INSTANCE.decals.count >= 6);
            expect("desert at noon is hot (>36C)", () -> ENV.ambientC > 36f);
            expect("heat shimmer active in the midday desert", () -> Atmosphere.INSTANCE.heatShimmer > 0.3f);
            look(0f, 5f);
            cmd("livingworld weather sandstorm");
            waitTicks(200);
            note("desert_sandstorm");
            shot("03_desert_sandstorm");
            expect("sandstorm reduces visibility", () -> Atmosphere.INSTANCE.visibility < 80f);
            expect("sandstorm draws blowing sand", () -> PrecipitationRenderer.INSTANCE.lastDrops > 50);
            // A stray animal may have added a fresh print or two; the 20-odd prints of the walk must be gone.
            expect("sandstorm swept the footprints away", () -> FootprintTracker.INSTANCE.decals.count <= 6);
            cmd("livingworld weather clear");
            cmd("time set 18000");
            waitTicks(160);
            note("desert_night");
            shot("04_desert_night");
            expect("desert night is much colder than noon (<20C)", () -> ENV.ambientC < 20f);
            cmd("time set 6000");
        }

        if (selected("snow")) {
            tpBiome("minecraft:snowy_plains", 0f, 15f);
            waitTicks(140);
            walk(50, false);
            walk(35, true);
            waitTicks(8);
            turn(180f, 38f);
            waitTicks(12);
            note("snow_footprints");
            shot("05_snow_footprints");
            expect("walking on snow left footprints", () -> FootprintTracker.INSTANCE.decals.count >= 6);
            expect("snowy plains are below freezing", () -> ENV.ambientC < 0f);
            // Creature tracks: let a few animals wander over the snow in front of the camera.
            look(0f, 30f);
            cmd("execute at @s run summon minecraft:cow ~1.5 ~ ~4");
            cmd("execute at @s run summon minecraft:wolf ~-1.5 ~ ~4");
            cmd("execute at @s run summon minecraft:chicken ~0 ~ ~3");
            cmd("execute at @s run summon minecraft:horse ~3 ~ ~5");
            AutoTest.run(() -> FootprintTracker.INSTANCE.placed = 0);
            waitTicks(400);
            note("snow_creature_tracks");
            shot("05b_snow_creature_tracks");
            expect("wandering animals left tracks", () -> FootprintTracker.INSTANCE.placed > 4);
            cmd("kill @e[type=!minecraft:player,distance=..40]");
            look(0f, 5f);
            cmd("livingworld weather blizzard");
            waitTicks(200);
            note("snow_blizzard");
            shot("06_blizzard");
            expect("blizzard draws wind-driven snow", () -> PrecipitationRenderer.INSTANCE.lastDrops > 200);
            expect("blizzard reduces visibility", () -> Atmosphere.INSTANCE.visibility < 90f);
            cmd("livingworld weather clear");
        }

        if (selected("forest")) {
            tpBiome("minecraft:forest", 0f, 10f);
            cmd("time set 400");
            waitTicks(200);
            note("forest_morning");
            shot("07_forest_morning_mist");
            expect("morning mist forms in the forest", () -> Atmosphere.INSTANCE.mistDensity > 0.005f);
            cmd("time set 6000");
            cmd("livingworld wind set 0.9");
            waitTicks(120);
            note("forest_wind");
            shot("08_forest_wind");
            waitTicks(7);
            shot("08_forest_wind_b");
            AutoTest.run(() -> AutoTest.log("vegetation: " + dev.livingworld.client.render.VegetationWind.status()));
            expect("plant models were tagged for sway", () -> dev.livingworld.client.render.VegetationWind.wrappedModels > 100);
            expect("terrain shader accepts the wind uniforms", dev.livingworld.client.render.VegetationWind::shaderBound);
            cmd("livingworld wind clear");
            cmd("livingworld weather heavy_rain");
            waitTicks(220);
            note("forest_heavy_rain");
            shot("09_forest_heavy_rain");
            expect("heavy rain draws streaks", () -> PrecipitationRenderer.INSTANCE.lastDrops > 200);
            expect("ground gets wet in the rain", () -> ENV.wetness > 0.1f);
            // Let the ground soak until it turns to mud, then run through it.
            waitTicks(360);
            expect("prolonged rain soaks the ground into mud", () -> ENV.wetness > 0.4f);
            walk(50, true);
            turn(180f, 40f);
            waitTicks(12);
            note("forest_mud");
            shot("10_forest_mud_prints");
            look(0f, -8f);
            cmd("livingworld weather thunderstorm");
            waitTicks(160);
            // A real bolt nearby exercises the delayed-thunder path and the lightning flash.
            cmd("execute at @s run summon minecraft:lightning_bolt ~40 ~ ~60");
            waitTicks(3);
            shot("11_thunderstorm_flash");
            expect("a lightning strike flashes", () -> ENV.flash > 0.05f);
            waitTicks(60);
            note("forest_thunderstorm");
            shot("11_thunderstorm");
            cmd("livingworld weather clear");
            waitTicks(100);
            note("forest_after_rain");
            shot("12_after_rain");
        }

        if (selected("jungle")) {
            tpBiome("minecraft:jungle", 0f, 5f);
            waitTicks(140);
            note("jungle_humid");
            shot("13_jungle_haze");
            expect("jungle air is humid", () -> ENV.humidity > 0.8f);
            cmd("livingworld weather torrential");
            waitTicks(200);
            note("jungle_downpour");
            shot("14_jungle_downpour");
            cmd("livingworld weather clear");
        }

        if (selected("swamp")) {
            tpBiome("minecraft:swamp", 0f, 8f);
            cmd("time set 18000");
            waitTicks(200);
            note("swamp_night");
            shot("15_swamp_night");
            cmd("time set 6000");
        }

        if (selected("cave")) {
            tpBiome("minecraft:plains", 0f, 0f);
            waitTicks(100);
            cmd("livingworld weather heavy_rain");
            waitTicks(100);
            note("plains_rain_outside");
            // Carve a small chamber well below the surface and stand in it.
            cmd("execute at @s run fill ~-4 ~-30 ~-4 ~4 ~-26 ~4 air");
            cmd("execute at @s run tp @s ~ ~-30 ~");
            waitTicks(140);
            note("cave");
            shot("16_cave");
            expect("underground counts as a cave", () -> ENV.cave > 0.6f);
            expect("no rain is drawn underground", () -> PrecipitationRenderer.INSTANCE.lastDrops == 0);
            cmd("livingworld weather clear");
        }

        if (selected("ultra")) {
            // Ultra preset at sunrise, looking toward the sun through the trees: light shafts and mist.
            AutoTest.run(() -> dev.livingworld.client.config.ClientConfig.get().preset = dev.livingworld.client.config.QualityPreset.ULTRA);
            tpBiome("minecraft:forest", -90f, -8f);
            cmd("time set 700");
            waitTicks(220);
            note("ultra_sunrise");
            shot("19_ultra_sunrise_shafts");
            expect("light shafts are active on Ultra at sunrise", () -> Atmosphere.INSTANCE.lightShafts > 0.05f);
            cmd("time set 6000");
            AutoTest.run(() -> dev.livingworld.client.config.ClientConfig.get().preset = dev.livingworld.client.config.QualityPreset.BALANCED);
        }

        if (selected("bench")) {
            // A/B frame-time measurements at 1920x1080 in the heaviest conditions.
            AutoTest.resize(1920, 1080);
            tpBiome("minecraft:forest", 0f, 5f);
            cmd("time set 6000");
            waitTicks(200);
            AutoTest.benchmark("forest, clear midday", 16);
            cmd("livingworld weather thunderstorm");
            waitTicks(300);
            AutoTest.benchmark("forest, thunderstorm", 16);
            cmd("livingworld weather clear");
            tpBiome("minecraft:snowy_plains", 0f, 5f);
            waitTicks(160);
            cmd("livingworld weather blizzard");
            waitTicks(240);
            AutoTest.benchmark("snowy plains, blizzard", 16);
            cmd("livingworld weather clear");
            tpBiome("minecraft:desert", 0f, 5f);
            waitTicks(160);
            cmd("livingworld weather sandstorm");
            waitTicks(240);
            AutoTest.benchmark("desert, sandstorm", 16);
            cmd("livingworld weather clear");
            AutoTest.resize(854, 480);
        }

        if (selected("bench") || selected("benchwater")) {
            // Water: a pond filling the lower half of the view, with a bank and trees to mirror.
            AutoTest.resize(1920, 1080);
            tpBiome("minecraft:desert", 0f, 5f);
            cmd("time set 6000");
            cmd("execute at @s run tp @s ~44 ~ ~");
            waitTicks(20);
            pond();
            look(0f, 14f);
            waitTicks(240);
            AutoTest.benchmark("desert pond, clear midday", 16);
            AutoTest.resize(854, 480);
        }

        if (selected("nether")) {
            AutoTest.tpDimension("minecraft:the_nether", 0, 0, 0f, 5f);
            waitTicks(200);
            note("nether");
            shot("17_nether");
            expect("the Nether is hot", () -> ENV.ambientC > 40f);
            expect("no precipitation in the Nether", () -> ENV.precip == 0f);
            expect("embers are rising", () -> ParticleEngine.INSTANCE.count > 5);
        }

        if (selected("end")) {
            AutoTest.tpDimension("minecraft:the_end", 48, 0, 90f, 0f);
            waitTicks(200);
            note("end");
            shot("18_end");
            expect("End motes are drifting", () -> ParticleEngine.INSTANCE.count > 5);
            expect("the End is cold", () -> ENV.ambientC < 0f);
        }

        if (AutoTest.SHOWCASE) {
            Showcase.build();
        }
    }
}
