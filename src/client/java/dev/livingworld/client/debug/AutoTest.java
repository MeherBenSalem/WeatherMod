package dev.livingworld.client.debug;

import com.mojang.datafixers.util.Pair;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.LivingWorldClient;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.footprint.FootprintTracker;
import dev.livingworld.client.particle.ParticleEngine;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.render.DecalRenderer;
import dev.livingworld.client.render.LWShaders;
import dev.livingworld.client.render.PostProcessor;
import dev.livingworld.client.render.PrecipitationRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.storage.LevelStorage;
import org.lwjgl.opengl.GL11;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Scripted end-to-end smoke test, enabled with {@code -Dlivingworld.autotest=true} (see the
 * {@code runAutotest} Gradle task). It boots into a throw-away world, visits a list of biomes and
 * weather conditions, actually walks the player around, and writes screenshots plus a metrics report to
 * {@code run/livingworld-autotest}. It exists because visual effects cannot be asserted from a unit
 * test: a human (or a reviewer reading the screenshots) checks the pictures, the report catches
 * crashes, GL errors and systems that produced nothing.
 */
public final class AutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("livingworld.autotest");
    private static final String ONLY = System.getProperty("livingworld.autotest.only", "");
    /** The cinematic tour: only runs when asked for by name (-PlwScenes=showcase), never as part of a test run. */
    static final boolean SHOWCASE = ("," + ONLY + ",").contains(",showcase,");
    private static final String WORLD = "lw_autotest";
    private static final long SEED = 8765432123456L;

    private interface Step {
        /** Called every client tick until it returns true. */
        boolean run(MinecraftClient client, int ticksInStep);
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static Path outDir;
    private static PrintWriter report;
    private static int phase;
    private static int phaseTicks;
    private static int stepIndex;
    private static int stepTicks;
    private static long totalTicks;
    private static volatile boolean busy;
    private static volatile String serverMessage;
    private static int failures;

    private AutoTest() {
    }

    public static void init() {
        if (!ENABLED) {
            return;
        }
        try {
            outDir = FabricLoader.getInstance().getGameDir().resolve("livingworld-autotest");
            Files.createDirectories(outDir);
            report = new PrintWriter(Files.newBufferedWriter(outDir.resolve("report.txt")), true);
        } catch (Exception e) {
            LivingWorld.LOGGER.error("Autotest could not create its output directory", e);
            return;
        }
        Scenes.build();
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
        log("Living World autotest starting; " + STEPS.size() + " steps");
    }

    static void log(String line) {
        LivingWorld.LOGGER.info("[autotest] {}", line);
        if (report != null) {
            report.println(line);
        }
    }

    private static void tick(MinecraftClient client) {
        totalTicks++;
        try {
            if (totalTicks > 20L * 60L * (SHOWCASE ? 35L : 14L)) {
                log("FAIL: global timeout");
                failures++;
                finish(client);
                return;
            }
            switch (phase) {
                case 0 -> startWorld(client);
                case 1 -> {
                    if (client.world != null && client.player != null && client.currentScreen == null) {
                        if (++phaseTicks > 80) {
                            phase = 2;
                            log("World ready after " + totalTicks + " ticks; GL renderer: " + GL11.glGetString(GL11.GL_RENDERER));
                        }
                    }
                }
                case 2 -> {
                    // The test window can grab keyboard focus; do not let stray keystrokes open chat mid-run.
                    if (client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen
                            || client.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
                        client.setScreen(null);
                    }
                    if (SHOWCASE) {
                        // Advancement and recipe pop-ups have no place in the pictures.
                        client.getToastManager().clear();
                    }
                    if (stepIndex >= STEPS.size()) {
                        finish(client);
                        return;
                    }
                    if (STEPS.get(stepIndex).run(client, stepTicks++)) {
                        stepIndex++;
                        stepTicks = 0;
                    }
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            log("FAIL: exception in step " + stepIndex + ": " + t);
            LivingWorld.LOGGER.error("Autotest failure", t);
            failures++;
            finish(client);
        }
    }

    private static void startWorld(MinecraftClient client) throws Exception {
        if (client.getOverlay() != null || client.currentScreen == null || client.world != null) {
            return;
        }
        if (++phaseTicks < 40) {
            return;
        }
        client.options.pauseOnLostFocus = false;
        client.options.onboardAccessibility = false;
        client.options.tutorialStep = TutorialStep.NONE;
        client.getTutorialManager().setStep(TutorialStep.NONE);
        client.options.getViewDistance().setValue(SHOWCASE ? 10 : 8);
        client.options.getEnableVsync().setValue(false);
        client.options.getMaxFps().setValue(260);
        // The showcase is meant to be watched and heard; test runs stay nearly silent.
        client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(SHOWCASE ? 0.4 : 0.04);
        client.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue(0.0);

        LevelStorage storage = client.getLevelStorage();
        if (storage.levelExists(WORLD)) {
            try (LevelStorage.Session session = storage.createSession(WORLD)) {
                session.deleteSessionLock();
            }
        }
        GameRules rules = new GameRules();
        rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        LevelInfo info = new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, rules, DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart(WORLD, info, new GeneratorOptions(SEED, true, false),
                WorldPresets::createDemoOptions);
        phase = 1;
        phaseTicks = 0;
    }

    private static void finish(MinecraftClient client) {
        phase = 3;
        log(failures == 0 ? "RESULT: completed without failures" : "RESULT: " + failures + " failure(s)");
        if (report != null) {
            report.close();
            report = null;
        }
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.scheduleStop();
    }

    // ---- step library ----------------------------------------------------------------------------

    static boolean selected(String scene) {
        return ONLY.isEmpty() || ("," + ONLY + ",").contains("," + scene + ",");
    }

    static void add(Step step) {
        STEPS.add(step);
    }

    static void waitTicks(int ticks) {
        add((client, t) -> t >= ticks);
    }

    static void cmd(String command) {
        add((client, t) -> {
            client.player.networkHandler.sendChatCommand(command);
            return true;
        });
    }

    static void look(float yaw, float pitch) {
        add((client, t) -> {
            client.player.setYaw(yaw);
            client.player.setPitch(pitch);
            return true;
        });
    }

    /** Turns by a relative amount (used to look back at the trail just walked). */
    static void turn(float deltaYaw, float pitch) {
        add((client, t) -> {
            client.player.setYaw(client.player.getYaw() + deltaYaw);
            client.player.setPitch(pitch);
            return true;
        });
    }

    static void walk(int ticks, boolean sprint) {
        add((client, t) -> {
            boolean done = t >= ticks;
            client.options.forwardKey.setPressed(!done);
            client.options.sprintKey.setPressed(!done && sprint);
            if (!done && sprint) {
                client.player.setSprinting(true);
            }
            return done;
        });
    }

    static void hud(boolean hidden) {
        add((client, t) -> {
            client.options.hudHidden = hidden;
            return true;
        });
    }

    static void run(Runnable action) {
        add((client, t) -> {
            action.run();
            return true;
        });
    }

    static void shot(String name) {
        add((client, t) -> {
            ScreenshotRecorder.saveScreenshot(outDir.toFile(), name + ".png", client.getFramebuffer(), text -> {
            });
            return true;
        });
    }

    /** Records the state of every system and checks for GL errors. */
    static void note(String name) {
        add((client, t) -> {
            ClientEnvironment e = ClientEnvironment.INSTANCE;
            Atmosphere a = Atmosphere.INSTANCE;
            int glError = GL11.glGetError();
            log(String.format(Locale.ROOT,
                    "[%s] biome=%s weather=%s air=%.1fC feels=%.1fC wind=%.2f(gust %.2f) precip=%.2f rain=%.2f snow=%.2f sand=%.2f storm=%.2f "
                            + "clouds=%.2f wet=%.2f open=%.2f roof=%.2f cave=%.2f sky=%.2f | haze=%.4f mist=%.4f vis=%s shimmer=%.2f exposure=%.2f | "
                            + "decals=%d(drawn %d) particles=%d(dropped %d) drops=%d quads=%d | fps=%d frame=%.2fms adaptive=%.2f glError=%d",
                    name, e.dominant, e.weatherType.id(), e.ambientC, e.feelsLikeC, e.wind.speed, e.wind.gust, e.precip, e.rain, e.snow,
                    e.sand, e.storm, e.cloudCover, e.wetness, e.openness, e.roof, e.cave, e.skyLight, a.hazeDensity, a.mistDensity,
                    a.visibility == Float.MAX_VALUE ? "inf" : String.format(Locale.ROOT, "%.0f", a.visibility), a.heatShimmer, a.exposure,
                    FootprintTracker.INSTANCE.decals.count, DecalRenderer.lastDrawn, ParticleEngine.INSTANCE.count,
                    ParticleEngine.INSTANCE.dropped, PrecipitationRenderer.INSTANCE.lastDrops, LivingWorldClient.batch().lastQuads,
                    client.getCurrentFps(), QualityManager.smoothedFrameMs(), QualityManager.adaptive(), glError));
            StringBuilder perf = new StringBuilder("    perf(us):");
            for (Perf p : Perf.values()) {
                perf.append(' ').append(p.label).append('=').append(Math.round(p.micros()));
            }
            log(perf + " | shaders: " + LWShaders.status() + " postRan=" + PostProcessor.lastRan + " | " + Compat.describe());
            ParticleEngine.INSTANCE.dropped = 0;
            if (glError != 0) {
                failures++;
                log("FAIL: OpenGL error " + glError + " at " + name);
            }
            return true;
        });
    }

    // ---- showcase helpers -----------------------------------------------------------------------

    /** Smoothly turns the view from one direction to another over the given number of ticks. */
    static void pan(float yawFrom, float yawTo, float pitchFrom, float pitchTo, int ticks) {
        add((client, t) -> {
            float k = Math.min(1f, t / (float) ticks);
            float s = k * k * (3f - 2f * k);
            client.player.setYaw(yawFrom + (yawTo - yawFrom) * s);
            client.player.setPitch(pitchFrom + (pitchTo - pitchFrom) * s);
            return t >= ticks;
        });
    }

    /** Like {@link #pan} but starting from wherever the player is looking now. */
    static void panBy(float deltaYaw, float pitchFrom, float pitchTo, int ticks) {
        add(new Step() {
            private float from = Float.NaN;

            @Override
            public boolean run(MinecraftClient client, int t) {
                if (Float.isNaN(from)) {
                    from = client.player.getYaw();
                }
                float k = Math.min(1f, t / (float) ticks);
                float s = k * k * (3f - 2f * k);
                client.player.setYaw(from + deltaYaw * s);
                client.player.setPitch(pitchFrom + (pitchTo - pitchFrom) * s);
                return t >= ticks;
            }
        });
    }

    /**
     * Turns toward the most open direction within {@code halfRange} degrees of {@code centreYaw} (180 = all
     * round): the one where both the line of sight and the path at knee height run furthest before
     * hitting anything. Keeps the camera from staring at a tree trunk or a cliff.
     */
    static void faceOpen(float centreYaw, float halfRange, float pitch) {
        add((client, t) -> {
            net.minecraft.client.network.ClientPlayerEntity p = client.player;
            net.minecraft.util.math.Vec3d eye = p.getEyePos();
            float best = centreYaw;
            double bestFree = -1.0;
            for (float offset = -halfRange; offset <= halfRange; offset += 10f) {
                float yaw = centreYaw + offset;
                double rad = Math.toRadians(yaw);
                net.minecraft.util.math.Vec3d dir = new net.minecraft.util.math.Vec3d(-Math.sin(rad), 0.0, Math.cos(rad));
                double free = 0.0;
                for (int ray = 0; ray < 3; ray++) {
                    // Eye level, a little above the horizon, and knee height.
                    net.minecraft.util.math.Vec3d from = ray == 2 ? eye.add(0.0, -1.0, 0.0) : eye;
                    net.minecraft.util.math.Vec3d to = from.add(dir.multiply(48.0)).add(0.0, ray == 1 ? 10.0 : 0.0, 0.0);
                    net.minecraft.util.hit.HitResult hit = client.world.raycast(new net.minecraft.world.RaycastContext(from, to,
                            net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, p));
                    free += hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? 48.0 : hit.getPos().distanceTo(from);
                }
                // Prefer the middle of the range when directions are equally open.
                free -= Math.abs(offset) * 0.02;
                if (free > bestFree) {
                    bestFree = free;
                    best = yaw;
                }
            }
            p.setYaw(best);
            p.setPitch(pitch);
            log(String.format(Locale.ROOT, "facing open direction: yaw %.0f, %.0f blocks clear on average", best, bestFree / 3.0));
            return true;
        });
    }

    static void perspective(net.minecraft.client.option.Perspective perspective) {
        add((client, t) -> {
            client.options.setPerspective(perspective);
            return true;
        });
    }

    /** Names the current scene in the window title (for whoever is watching) and in the report. */
    static void caption(String title) {
        add((client, t) -> {
            client.getWindow().setTitle("Living World showcase  —  " + title);
            log("== " + title);
            return true;
        });
    }

    static void gamma(double value) {
        add((client, t) -> {
            client.options.getGamma().setValue(value);
            return true;
        });
    }

    /** Turns toward the largest body of water within about 24 blocks. */
    static void faceWater(float pitch) {
        add((client, t) -> {
            ClientEnvironment env = ClientEnvironment.INSTANCE;
            int cx = env.camBlock.getX(), cz = env.camBlock.getZ();
            double sx = 0.0, sz = 0.0;
            int n = 0;
            for (int dx = -24; dx <= 24; dx += 2) {
                for (int dz = -24; dz <= 24; dz += 2) {
                    if (env.columns.topMaterial(cx + dx, cz + dz) == dev.livingworld.surface.SurfaceMaterial.WATER) {
                        sx += dx;
                        sz += dz;
                        n++;
                    }
                }
            }
            if (n > 0) {
                client.player.setYaw((float) Math.toDegrees(Math.atan2(-sx, sz)));
            }
            client.player.setPitch(pitch);
            log("facing water: " + n + " water columns nearby");
            return true;
        });
    }

    /** Runs an action on the server thread against the player and waits for it; the returned text is logged. */
    static void onServer(java.util.function.Function<ServerPlayerEntity, String> action) {
        add(new Step() {
            private boolean started;

            @Override
            public boolean run(MinecraftClient client, int t) {
                if (!started) {
                    started = true;
                    busy = true;
                    MinecraftServer server = client.getServer();
                    UUID uuid = client.player.getUuid();
                    server.execute(() -> {
                        try {
                            serverMessage = action.apply(server.getPlayerManager().getPlayer(uuid));
                        } catch (Throwable e) {
                            serverMessage = "FAIL: server step: " + e;
                        } finally {
                            busy = false;
                        }
                    });
                }
                if (busy) {
                    return false;
                }
                String message = serverMessage;
                if (message != null) {
                    serverMessage = null;
                    log(message);
                    if (message.startsWith("FAIL")) {
                        failures++;
                    }
                }
                return true;
            }
        });
    }

    /** Fills the ground in front of the player (to the south) with grass, tall grass and flowers. */
    static void plantMeadow(int halfWidth, int from, int to) {
        onServer(player -> {
            ServerWorld world = player.getServerWorld();
            java.util.Random random = new java.util.Random(7L);
            net.minecraft.block.Block[] flowers = {net.minecraft.block.Blocks.POPPY, net.minecraft.block.Blocks.DANDELION,
                    net.minecraft.block.Blocks.CORNFLOWER, net.minecraft.block.Blocks.OXEYE_DAISY, net.minecraft.block.Blocks.AZURE_BLUET};
            BlockPos.Mutable p = new BlockPos.Mutable();
            int planted = 0;
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                for (int dz = from; dz <= to; dz++) {
                    int x = player.getBlockX() + dx, z = player.getBlockZ() + dz;
                    int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                    p.set(x, y - 1, z);
                    if (!world.getBlockState(p).isOf(net.minecraft.block.Blocks.GRASS_BLOCK)) {
                        continue;
                    }
                    p.set(x, y, z);
                    if (!world.getBlockState(p).isAir()) {
                        continue;
                    }
                    float r = random.nextFloat();
                    if (r < 0.14f) {
                        net.minecraft.block.TallPlantBlock.placeAt(world, net.minecraft.block.Blocks.TALL_GRASS.getDefaultState(), p, 2);
                    } else if (r < 0.24f) {
                        world.setBlockState(p, flowers[random.nextInt(flowers.length)].getDefaultState(), 2);
                    } else if (r < 0.94f) {
                        world.setBlockState(p, net.minecraft.block.Blocks.GRASS.getDefaultState(), 2);
                    }
                    planted++;
                }
            }
            return "planted a meadow of " + planted + " plants";
        });
    }

    /** Finds an underground biome (lush caves, dripstone caves) and puts the player on a cavern floor inside it. */
    static void tpUnderground(String biomeId, float yaw, float pitch) {
        add(new Step() {
            private boolean started;

            @Override
            public boolean run(MinecraftClient client, int t) {
                if (!started) {
                    started = true;
                    busy = true;
                    MinecraftServer server = client.getServer();
                    UUID uuid = client.player.getUuid();
                    server.execute(() -> {
                        try {
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                            ServerWorld world = server.getOverworld();
                            RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, new Identifier(biomeId));
                            BlockPos origin = new BlockPos(player.getBlockX(), 10, player.getBlockZ());
                            Pair<BlockPos, RegistryEntry<Biome>> found = world.locateBiome(e -> e.matchesKey(key), origin, 6400, 32, 8);
                            if (found == null) {
                                serverMessage = "FAIL: biome " + biomeId + " not found";
                                return;
                            }
                            BlockPos target = cavernFloor(world, found.getFirst(), key);
                            if (target == null) {
                                serverMessage = "FAIL: no open cavern found in " + biomeId + " near " + found.getFirst().toShortString();
                                return;
                            }
                            player.teleport(world, target.getX() + 0.5, target.getY() + 0.05, target.getZ() + 0.5, yaw, pitch);
                            serverMessage = "teleported into " + biomeId + " at " + target.toShortString();
                        } catch (Throwable e) {
                            serverMessage = "FAIL: teleport into " + biomeId + ": " + e;
                        } finally {
                            busy = false;
                        }
                    });
                }
                if (busy) {
                    return false;
                }
                String message = serverMessage;
                if (message != null) {
                    serverMessage = null;
                    log(message);
                    if (message.startsWith("FAIL")) {
                        failures++;
                    }
                }
                return true;
            }
        });
    }

    /** A standable spot well below the surface, in the given biome, with plenty of open cave around it. */
    private static BlockPos cavernFloor(ServerWorld world, BlockPos around, RegistryKey<Biome> biome) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        BlockPos best = null;
        int bestOpen = 0;
        for (int r = 0; r <= 24; r += 3) {
            for (int dx = -r; dx <= r; dx += 3) {
                for (int dz = -r; dz <= r; dz += 3) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = around.getX() + dx, z = around.getZ() + dz;
                    world.getChunk(x >> 4, z >> 4);
                    int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                    for (int y = around.getY() - 16; y <= around.getY() + 16; y++) {
                        if (y > surface - 12 || y <= world.getBottomY() + 2 || !standable(world, p, x, y, z)) {
                            continue;
                        }
                        p.set(x, y, z);
                        if (!world.getBiome(p).matchesKey(biome)) {
                            continue;
                        }
                        int open = 0;
                        for (int ox = -4; ox <= 4; ox += 2) {
                            for (int oz = -4; oz <= 4; oz += 2) {
                                for (int oy = 1; oy <= 5; oy += 2) {
                                    p.set(x + ox, y + oy, z + oz);
                                    if (world.getBlockState(p).getCollisionShape(world, p).isEmpty()) {
                                        open++;
                                    }
                                }
                            }
                        }
                        if (open > bestOpen) {
                            bestOpen = open;
                            best = new BlockPos(x, y, z);
                        }
                    }
                }
            }
            // 75 samples per spot: stop as soon as something properly roomy turns up.
            if (bestOpen >= 60) {
                return best;
            }
        }
        return bestOpen >= 35 ? best : null;
    }

    /** Resizes the game window (benchmarks are run at a realistic resolution). */
    static void resize(int width, int height) {
        add((client, t) -> {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), width, height);
            return true;
        });
        waitTicks(20);
    }

    /** Runs the in-game A/B benchmark and copies its report into the test report. */
    static void benchmark(String label, int seconds) {
        add((client, t) -> {
            Benchmark.lastReport = null;
            Benchmark.start(seconds);
            return true;
        });
        add((client, t) -> !Benchmark.running() && Benchmark.lastReport != null);
        add((client, t) -> {
            log("[benchmark: " + label + "]");
            for (String line : Benchmark.lastReport.split("\\R")) {
                log("    " + line);
            }
            return true;
        });
    }

    /** Fails the run unless the condition holds. */
    static void expect(String description, java.util.function.BooleanSupplier condition) {
        add((client, t) -> {
            boolean ok = condition.getAsBoolean();
            log((ok ? "ok:   " : "FAIL: ") + description);
            if (!ok) {
                failures++;
            }
            return true;
        });
    }

    /** Finds the nearest instance of a biome on the server and teleports the player onto open ground there. */
    static void tpBiome(String biomeId, float yaw, float pitch) {
        add(new Step() {
            private boolean started;

            @Override
            public boolean run(MinecraftClient client, int t) {
                if (!started) {
                    started = true;
                    busy = true;
                    MinecraftServer server = client.getServer();
                    UUID uuid = client.player.getUuid();
                    server.execute(() -> {
                        try {
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                            ServerWorld world = server.getOverworld();
                            RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, new Identifier(biomeId));
                            BlockPos origin = player.getWorld() == world ? player.getBlockPos() : BlockPos.ORIGIN;
                            Pair<BlockPos, RegistryEntry<Biome>> found = world.locateBiome(e -> e.matchesKey(key), origin, 6400, 32, 64);
                            if (found == null) {
                                serverMessage = "FAIL: biome " + biomeId + " not found within 6400 blocks";
                                return;
                            }
                            BlockPos target = openGround(world, found.getFirst(), key);
                            player.teleport(world, target.getX() + 0.5, target.getY() + 0.05, target.getZ() + 0.5, yaw, pitch);
                            serverMessage = "teleported to " + biomeId + " at " + target.toShortString();
                        } catch (Throwable e) {
                            serverMessage = "FAIL: teleport to " + biomeId + ": " + e;
                        } finally {
                            busy = false;
                        }
                    });
                }
                if (busy) {
                    return false;
                }
                String message = serverMessage;
                if (message != null) {
                    serverMessage = null;
                    log(message);
                    if (message.startsWith("FAIL")) {
                        failures++;
                    }
                }
                return true;
            }
        });
    }

    /** Teleports into another dimension, onto open ground near the given column. */
    static void tpDimension(String dimensionId, int nearX, int nearZ, float yaw, float pitch) {
        add(new Step() {
            private boolean started;

            @Override
            public boolean run(MinecraftClient client, int t) {
                if (!started) {
                    started = true;
                    busy = true;
                    MinecraftServer server = client.getServer();
                    UUID uuid = client.player.getUuid();
                    server.execute(() -> {
                        try {
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                            ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimensionId)));
                            BlockPos target = openSpot(world, nearX, nearZ);
                            if (target == null) {
                                serverMessage = "FAIL: no open ground found in " + dimensionId;
                                return;
                            }
                            player.teleport(world, target.getX() + 0.5, target.getY() + 0.05, target.getZ() + 0.5, yaw, pitch);
                            serverMessage = "teleported to " + dimensionId + " at " + target.toShortString();
                        } catch (Throwable e) {
                            serverMessage = "FAIL: teleport to " + dimensionId + ": " + e;
                        } finally {
                            busy = false;
                        }
                    });
                }
                if (busy) {
                    return false;
                }
                String message = serverMessage;
                if (message != null) {
                    serverMessage = null;
                    log(message);
                    if (message.startsWith("FAIL")) {
                        failures++;
                    }
                }
                return true;
            }
        });
    }

    /** A standable block with plenty of open air around it (a cavern floor in the Nether, an island top in the End). */
    private static BlockPos openSpot(ServerWorld world, int nearX, int nearZ) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        boolean ceiling = world.getDimension().hasCeiling();
        for (int r = 0; r <= 128; r += 8) {
            for (int dx = -r; dx <= r; dx += 8) {
                for (int dz = -r; dz <= r; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = nearX + dx, z = nearZ + dz;
                    world.getChunk(x >> 4, z >> 4);
                    int top = ceiling ? 110 : world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                    int bottom = ceiling ? 34 : top;
                    for (int y = top; y >= bottom; y--) {
                        if (y <= world.getBottomY() + 1 || !standable(world, p, x, y, z)) {
                            continue;
                        }
                        int open = 0, total = 0;
                        for (int ox = -5; ox <= 5; ox += 2) {
                            for (int oz = -5; oz <= 5; oz += 2) {
                                for (int oy = 1; oy <= 7; oy += 2) {
                                    p.set(x + ox, y + oy, z + oz);
                                    total++;
                                    if (world.getBlockState(p).isAir()) {
                                        open++;
                                    }
                                }
                            }
                        }
                        if (open >= total * 0.8) {
                            return new BlockPos(x, y, z);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Looks around the located biome position for a spot inside that biome with a straight, level,
     * unobstructed run of ground to the south, which is where the scripted walks go.
     */
    private static BlockPos openGround(ServerWorld world, BlockPos around, RegistryKey<Biome> biome) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        BlockPos fallback = null;
        BlockPos interiorFallback = null;
        BlockPos shortRun = null;
        for (int r = 0; r <= 192; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = around.getX() + dx, z = around.getZ() + dz;
                    world.getChunk(x >> 4, z >> 4);
                    int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                    p.set(x, y, z);
                    if (!world.getBiome(p).matchesKey(biome) || !standable(world, p, x, y, z)) {
                        continue;
                    }
                    if (fallback == null) {
                        fallback = new BlockPos(x, y, z);
                    }
                    boolean inside = interior(world, p, x, y, z, biome);
                    if (inside && interiorFallback == null) {
                        interiorFallback = new BlockPos(x, y, z);
                    }
                    int clear = 0;
                    for (int d = 1; d <= 22; d++) {
                        if (world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z + d) != y || !standable(world, p, x, y, z + d)) {
                            break;
                        }
                        clear++;
                    }
                    p.set(x, y, z + 22);
                    if (clear == 22 && inside && world.getBiome(p).matchesKey(biome)) {
                        return new BlockPos(x, y, z);
                    }
                    if (clear >= 9 && inside && shortRun == null) {
                        shortRun = new BlockPos(x, y, z);
                    }
                }
            }
        }
        // No long level run (hilly or densely wooded biome): a shorter one, or at least stand well inside it.
        if (shortRun != null) {
            return shortRun;
        }
        if (interiorFallback != null) {
            return interiorFallback;
        }
        if (fallback != null) {
            return fallback;
        }
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, around.getX(), around.getZ());
        return new BlockPos(around.getX(), y, around.getZ());
    }

    /**
     * Whether the spot is well inside the biome rather than on its edge. The mod blends climate over
     * about 26 blocks, so a scene meant to show one biome has to stand clear of its neighbours.
     */
    private static boolean interior(ServerWorld world, BlockPos.Mutable p, int x, int y, int z, RegistryKey<Biome> biome) {
        for (int d = -28; d <= 28; d += 28) {
            for (int e = -28; e <= 28; e += 28) {
                p.set(x + d, y, z + e);
                if (!world.getBiome(p).matchesKey(biome)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Solid, dry ground at y-1 with two blocks of free space above it. */
    private static boolean standable(ServerWorld world, BlockPos.Mutable p, int x, int y, int z) {
        p.set(x, y - 1, z);
        BlockState ground = world.getBlockState(p);
        if (!ground.isOpaque() || !ground.getFluidState().isEmpty() || ground.isIn(BlockTags.LOGS) || ground.isIn(BlockTags.LEAVES)) {
            return false;
        }
        for (int h = 0; h < 2; h++) {
            p.set(x, y + h, z);
            BlockState above = world.getBlockState(p);
            if (!above.getFluidState().isEmpty() || !above.getCollisionShape(world, p).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
