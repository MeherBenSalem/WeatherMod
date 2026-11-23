package dev.livingworld.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.CommandDispatcher;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.audio.EnvironmentAudio;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
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
import dev.livingworld.client.render.PrecipitationRenderer;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.net.LWNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;

/**
 * Client entry point: owns the per-tick update order and the per-frame render order of every effect
 * system. Nothing in here (or anywhere under the client packages) is reachable from the common code,
 * so a dedicated server never loads it.
 *
 * <p>This class knows nothing of the mod loader. The Fabric and NeoForge modules each call {@link #init()}
 * once at start-up and forward their own events to the {@code on...} methods below: the client tick,
 * the submit phase of the level render, the HUD, the two packets and the end of a connection.
 *
 * <p>On this version of the game the world is drawn from render states and submitted geometry, so the
 * mod's own shader programs (atmosphere pass, water, vegetation sway, lit decals, lens program) are not
 * part of it: precipitation, particles and footprints are submitted as custom geometry.
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
    public static KeyMapping configKey(KeyMapping.Category category) {
        if (configKey == null) {
            configKey = new KeyMapping("key.livingworld.config", InputConstants.KEY_F9, category);
        }
        return configKey;
    }

    /** The key binding category, by id; each loader registers it its own way. */
    public static Identifier keyCategoryId() {
        return LivingWorld.id("main");
    }

    /** Called once by the loader's client entry point. */
    public static void init() {
        ClientConfig.get();
        AmbientSpawner.INSTANCE.listener = EnvironmentAudio.INSTANCE;
        LivingWorld.LOGGER.info("Living World client ready ({} preset)", ClientConfig.get().preset);
    }

    /** {@code /lw ...}, when the loader builds its client command tree. */
    public static <S> void registerCommands(CommandDispatcher<S> dispatcher, BiConsumer<S, Component> feedback) {
        ClientCommands.register(dispatcher, feedback);
    }

    /**
     * One of the mod's packets has arrived. Called on the network thread: the packet is read here and
     * applied on the client thread.
     */
    public static void receive(Identifier channel, FriendlyByteBuf buf) {
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
    }

    private static void tick(Minecraft client) {
        while (configKey != null && configKey.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new LWConfigScreen(null));
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
    public static void onHudRender(GuiGraphicsExtractor graphics, float tickDelta) {
        DebugHud.render(graphics, tickDelta);
    }

    /**
     * The level render's submit phase. {@code poseStack} is relative to the camera, without its rotation.
     * Footprints first, then precipitation and particles in one batch.
     */
    public static void onSubmit(PoseStack poseStack, SubmitNodeCollector collector, Camera camera, float tickDelta) {
        QualityManager.frame();
        Benchmark.frame();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        ClientConfig config = ClientConfig.get();
        if (!config.enabled || !env.inWorld) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        long start = System.nanoTime();
        if (config.footprints) {
            DecalRenderer.render(poseStack, collector, camera, FootprintTracker.INSTANCE.decals, env);
        }
        long afterDecals = System.nanoTime();
        Perf.RENDER_DECALS.add(afterDecals - start);

        // No full-screen atmosphere pass on this version: sprites are not hazed.
        BATCH.setHaze(0f, 0f, 0f, 0f, 0f);
        BATCH.begin();
        PrecipitationRenderer.INSTANCE.render(BATCH, camera, env, client.isPaused());
        ParticleEngine.INSTANCE.render(BATCH, camera, client.isPaused() ? 1f : tickDelta);
        BATCH.submit(poseStack, collector);
        Perf.RENDER_AIR.add(System.nanoTime() - afterDecals);
    }
}
