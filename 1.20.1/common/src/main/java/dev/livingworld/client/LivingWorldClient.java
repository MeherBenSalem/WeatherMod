package dev.livingworld.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.CommandDispatcher;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.audio.EnvironmentAudio;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.debug.AutoTest;
import dev.livingworld.client.debug.Benchmark;
import dev.livingworld.client.debug.DebugHud;
import dev.livingworld.client.debug.Perf;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.footprint.FootprintTracker;
import dev.livingworld.client.gui.LWConfigScreen;
import dev.livingworld.client.particle.AmbientSpawner;
import dev.livingworld.client.particle.ParticleEngine;
import dev.livingworld.client.player.PlayerExposure;
import dev.livingworld.client.render.DecalRenderer;
import dev.livingworld.client.render.FxBatch;
import dev.livingworld.client.render.LWShaders;
import dev.livingworld.client.render.PostProcessor;
import dev.livingworld.client.render.PrecipitationRenderer;
import dev.livingworld.client.render.VegetationWind;
import dev.livingworld.client.render.WaterShading;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.net.LWNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

/**
 * Client entry point: owns the per-tick update order and the per-frame render order of every effect
 * system. Nothing in here (or anywhere under the client packages) is reachable from the common code,
 * so a dedicated server never loads it.
 *
 * <p>This class knows nothing of the mod loader. The Fabric and Forge modules each call {@link #init()}
 * once at start-up and forward their own events to the {@code on...} methods below: the client tick,
 * four points in the drawing of a frame, the HUD, the two packets and the end of a connection.
 */
public final class LivingWorldClient {
    private static final FxBatch BATCH = new FxBatch();
    /** Opens the settings screen. Created by {@link #configKey()}, registered with the game by the loader module. */
    private static KeyMapping configKey;

    private LivingWorldClient() {
    }

    public static FxBatch batch() {
        return BATCH;
    }

    /** Whether the procedural audio beds are currently playing. */
    public static boolean audioRunning() {
        return EnvironmentAudio.INSTANCE.running();
    }

    /**
     * The key binding that opens the settings screen, for the loader module to register. Created on the
     * first call rather than with the class: the game keeps key bindings in a plain map, and Forge
     * constructs mods on worker threads, so it must be made where the loader registers it.
     */
    public static KeyMapping configKey() {
        if (configKey == null) {
            configKey = new KeyMapping("key.livingworld.config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9,
                    "key.categories.livingworld");
        }
        return configKey;
    }

    /** Called once by the loader's client entry point. */
    public static void init() {
        ClientConfig.get();
        AmbientSpawner.INSTANCE.listener = EnvironmentAudio.INSTANCE;
        VegetationWind.logMode();
        AutoTest.init();
        LivingWorld.LOGGER.info("Living World client ready ({} preset)", ClientConfig.get().preset);
    }

    /** The mod's shader programs, when the game (re)loads its shaders. */
    public static void registerShaders(LWShaders.Registrar registrar) {
        LWShaders.registerAll(registrar);
    }

    /** {@code /lw ...}, when the loader builds its client command tree. */
    public static <S> void registerCommands(CommandDispatcher<S> dispatcher, BiConsumer<S, Component> feedback) {
        ClientCommands.register(dispatcher, feedback);
    }

    /**
     * One of the mod's packets has arrived. Called on the network thread: the packet is read here and
     * applied on the client thread.
     */
    public static void receive(ResourceLocation channel, FriendlyByteBuf buf) {
        Minecraft client = Minecraft.getInstance();
        if (LWNetworking.STATE.equals(channel)) {
            SimulationOverrides received = new SimulationOverrides();
            received.read(buf);
            client.execute(() -> ClientEnvironment.INSTANCE.server.copyFrom(received));
        } else if (LWNetworking.CLIENT_COMMAND.equals(channel)) {
            String action = buf.readUtf(LWNetworking.MAX_ACTION_LENGTH);
            int argument = buf.readVarInt();
            client.execute(() -> ClientCommands.run(action, argument));
        }
    }

    /** The connection to a server (or the single-player world) has ended. */
    public static void onDisconnect() {
        Minecraft.getInstance().execute(LivingWorldClient::reset);
    }

    private static void reset() {
        ClientEnvironment.INSTANCE.server.reset();
        ClientEnvironment.INSTANCE.local.reset();
        ParticleEngine.INSTANCE.clear();
        FootprintTracker.INSTANCE.clear();
        PlayerExposure.INSTANCE.reset();
        EnvironmentAudio.INSTANCE.stop();
        QualityManager.reset();
    }

    /** At the end of every client tick. */
    public static void onClientTick(Minecraft client) {
        tick(client);
        AutoTest.tick(client);
    }

    private static void tick(Minecraft client) {
        while (configKey != null && configKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new LWConfigScreen(null));
            }
        }
        if (client.isPaused()) {
            return;
        }
        long start = System.nanoTime();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        env.tick(client);
        if (!env.inWorld || client.level == null) {
            if (ParticleEngine.INSTANCE.count > 0) {
                ParticleEngine.INSTANCE.clear();
            }
            FootprintTracker.INSTANCE.tick(client, env);
            EnvironmentAudio.INSTANCE.tick(client, env);
            return;
        }
        if (!ClientConfig.get().enabled) {
            ParticleEngine.INSTANCE.clear();
            FootprintTracker.INSTANCE.clear();
            EnvironmentAudio.INSTANCE.tick(client, env);
            return;
        }
        Atmosphere.INSTANCE.tick(env);
        long afterEnv = System.nanoTime();
        FootprintTracker.INSTANCE.tick(client, env);
        long afterPrints = System.nanoTime();
        PlayerExposure.INSTANCE.tick(client, env);
        AmbientSpawner.INSTANCE.tick(client.level, env);
        ParticleEngine.INSTANCE.tick(client.level, env);
        long end = System.nanoTime();
        EnvironmentAudio.INSTANCE.tick(client, env);
        Perf.ENVIRONMENT.add(afterEnv - start);
        Perf.FOOTPRINTS.add(afterPrints - afterEnv);
        Perf.PARTICLES.add(end - afterPrints);
        Perf.AUDIO.add(System.nanoTime() - end);
    }

    /** The debug overlay, drawn with the rest of the HUD. */
    public static void onHudRender(GuiGraphics graphics, float tickDelta) {
        DebugHud.render(graphics, tickDelta);
    }

    /** Frame, step 1: before any terrain is drawn. Hands the terrain shaders this frame's wind and season. */
    public static void onRenderStart(Camera camera, float tickDelta) {
        VegetationWind.upload(camera, tickDelta);
    }

    /** Frame, step 2: ground decals, after opaque terrain and entities and before anything translucent. */
    public static void onAfterEntities(PoseStack poseStack, Camera camera) {
        ClientConfig config = ClientConfig.get();
        if (!config.enabled || !config.footprints || !ClientEnvironment.INSTANCE.inWorld) {
            return;
        }
        long start = System.nanoTime();
        DecalRenderer.render(poseStack, camera, FootprintTracker.INSTANCE.decals, ClientEnvironment.INSTANCE);
        Perf.RENDER_DECALS.add(System.nanoTime() - start);
    }

    /**
     * Frame, step 3: everything opaque is drawn (terrain, entities, block entities, footprints) and
     * translucent terrain comes next. Copies the frame for the water to refract and mirror.
     */
    public static void onBeforeTranslucent(PoseStack poseStack, Matrix4f projection, Camera camera, float tickDelta) {
        WaterShading.prepare(poseStack, projection, camera, tickDelta);
    }

    /**
     * Frame, step 4: the world is finished. The atmosphere pass over the whole image, then precipitation
     * and particles in one batch on top.
     */
    public static void onRenderLast(PoseStack poseStack, Matrix4f projection, Camera camera, float tickDelta) {
        QualityManager.frame();
        Benchmark.frame();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        if (!ClientConfig.get().enabled || !env.inWorld) {
            return;
        }
        long start = System.nanoTime();
        Minecraft client = Minecraft.getInstance();

        // 1. Atmosphere pass over the finished world image.
        PostProcessor.render(camera, projection, poseStack.last().pose(), tickDelta);
        long afterPost = System.nanoTime();
        if (PostProcessor.lastRan) {
            Perf.RENDER_POST.add(afterPost - start);
        }

        // 2. Precipitation and particles on top, hazed by their own distance.
        Atmosphere atmosphere = Atmosphere.INSTANCE;
        boolean hazed = PostProcessor.lastRan;
        BATCH.setHaze(atmosphere.hazeR, atmosphere.hazeG, atmosphere.hazeB, hazed ? atmosphere.hazeDensity : 0f, atmosphere.hazeMax);

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPoseMatrix(new Matrix4f(poseStack.last().pose()));
        RenderSystem.applyModelViewMatrix();

        BATCH.begin();
        PrecipitationRenderer.INSTANCE.render(BATCH, camera, env, client.isPaused());
        ParticleEngine.INSTANCE.render(BATCH, camera, client.isPaused() ? 1f : tickDelta);
        BATCH.draw();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        Perf.RENDER_AIR.add(System.nanoTime() - afterPost);
    }
}
