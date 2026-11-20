package dev.livingworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.audio.EnvironmentAudio;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.debug.AutoTest;
import dev.livingworld.client.debug.Benchmark;
import dev.livingworld.client.debug.DebugHud;
import dev.livingworld.client.debug.Perf;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.gui.LWConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.footprint.FootprintTracker;
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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * Client entry point: owns the per-tick update order and the per-frame render order of every effect
 * system. Nothing in here (or anywhere under the client source set) is reachable from the common code,
 * so a dedicated server never loads it.
 */
public final class LivingWorldClient implements ClientModInitializer {
    private static final FxBatch BATCH = new FxBatch();
    private static KeyBinding configKey;

    public static FxBatch batch() {
        return BATCH;
    }

    /** Whether the procedural audio beds are currently playing. */
    public static boolean audioRunning() {
        return EnvironmentAudio.INSTANCE.running();
    }

    @Override
    public void onInitializeClient() {
        ClientConfig.get();
        LWShaders.init();
        FootprintTracker.INSTANCE.listener = EnvironmentAudio.INSTANCE;
        AmbientSpawner.INSTANCE.listener = EnvironmentAudio.INSTANCE;

        VegetationWind.init();

        ClientTickEvents.END_CLIENT_TICK.register(LivingWorldClient::tick);
        WorldRenderEvents.START.register(context -> VegetationWind.upload(context.camera(), context.tickDelta()));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(WaterShading::prepare);
        WorldRenderEvents.AFTER_ENTITIES.register(LivingWorldClient::renderGround);
        WorldRenderEvents.LAST.register(LivingWorldClient::renderAir);

        ClientPlayNetworking.registerGlobalReceiver(LWNetworking.STATE, (client, handler, buf, sender) -> {
            SimulationOverrides received = new SimulationOverrides();
            received.read(buf);
            client.execute(() -> ClientEnvironment.INSTANCE.server.copyFrom(received));
        });
        ClientPlayNetworking.registerGlobalReceiver(LWNetworking.CLIENT_COMMAND, (client, handler, buf, sender) -> {
            String action = buf.readString(64);
            int argument = buf.readVarInt();
            client.execute(() -> ClientCommands.run(action, argument));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(LivingWorldClient::reset));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> ClientCommands.register(dispatcher));
        HudRenderCallback.EVENT.register(DebugHud::render);
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.livingworld.config", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9, "key.categories.livingworld"));

        AutoTest.init();
        LivingWorld.LOGGER.info("Living World client ready ({} preset)", ClientConfig.get().preset);
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

    private static void tick(MinecraftClient client) {
        while (configKey != null && configKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new LWConfigScreen(null));
            }
        }
        if (client.isPaused()) {
            return;
        }
        long start = System.nanoTime();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        env.tick(client);
        if (!env.inWorld || client.world == null) {
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
        AmbientSpawner.INSTANCE.tick(client.world, env);
        ParticleEngine.INSTANCE.tick(client.world, env);
        long end = System.nanoTime();
        EnvironmentAudio.INSTANCE.tick(client, env);
        Perf.ENVIRONMENT.add(afterEnv - start);
        Perf.FOOTPRINTS.add(afterPrints - afterEnv);
        Perf.PARTICLES.add(end - afterPrints);
        Perf.AUDIO.add(System.nanoTime() - end);
    }

    /** Ground decals: after opaque terrain and entities, before anything translucent. */
    private static void renderGround(WorldRenderContext context) {
        ClientConfig config = ClientConfig.get();
        if (!config.enabled || !config.footprints || !ClientEnvironment.INSTANCE.inWorld) {
            return;
        }
        long start = System.nanoTime();
        DecalRenderer.render(context.matrixStack(), context.camera(), FootprintTracker.INSTANCE.decals, ClientEnvironment.INSTANCE);
        Perf.RENDER_DECALS.add(System.nanoTime() - start);
    }

    /** Airborne effects: precipitation and particles in one batch at the very end of the world pass. */
    private static void renderAir(WorldRenderContext context) {
        QualityManager.frame();
        Benchmark.frame();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        if (!ClientConfig.get().enabled || !env.inWorld) {
            return;
        }
        long start = System.nanoTime();
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Atmosphere pass over the finished world image.
        PostProcessor.render(context.camera(), context.projectionMatrix(), context.matrixStack().peek().getPositionMatrix(),
                context.tickDelta());
        long afterPost = System.nanoTime();
        if (PostProcessor.lastRan) {
            Perf.RENDER_POST.add(afterPost - start);
        }

        // 2. Precipitation and particles on top, hazed by their own distance.
        Atmosphere atmosphere = Atmosphere.INSTANCE;
        boolean hazed = PostProcessor.lastRan;
        BATCH.setHaze(atmosphere.hazeR, atmosphere.hazeG, atmosphere.hazeB, hazed ? atmosphere.hazeDensity : 0f, atmosphere.hazeMax);

        MatrixStack modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.multiplyPositionMatrix(new Matrix4f(context.matrixStack().peek().getPositionMatrix()));
        RenderSystem.applyModelViewMatrix();

        BATCH.begin();
        PrecipitationRenderer.INSTANCE.render(BATCH, context.camera(), env, client.isPaused());
        ParticleEngine.INSTANCE.render(BATCH, context.camera(), client.isPaused() ? 1f : context.tickDelta());
        BATCH.draw();

        modelView.pop();
        RenderSystem.applyModelViewMatrix();
        Perf.RENDER_AIR.add(System.nanoTime() - afterPost);
    }
}
