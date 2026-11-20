package dev.livingworld.client.render;

import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Water that is shaded as water, without touching a single block: you see through it to a bed that
 * loses its red with depth, it mirrors the world around it and the sky above it, the sun glitters on
 * it, ripples run with the wind and rain rings it.
 *
 * <p>Like {@link VegetationWind} this has two halves. While chunks are meshed, water vertices are given
 * a vertex alpha of 250/255 instead of the 255 vanilla always writes (see {@code FluidRendererMixin}).
 * Just before the translucent terrain layer is drawn, the frame so far (colour and depth: everything
 * that can lie behind or be mirrored in the water) is copied, and the mod's override of the vanilla
 * translucent terrain shader uses that copy to refract, tint and reflect.
 *
 * <p>It rides on the vanilla terrain shaders, so it is active with the vanilla/Indigo renderer and
 * automatically inactive under Sodium (its own terrain shaders and fluid mesher) or an Iris shader pack
 * (which shades water itself); there water keeps its vanilla look.
 */
public final class WaterShading {
    /** Vertex alpha that marks water in the translucent terrain layer. */
    public static final float TAG_ALPHA = 250f / 255f;

    private static final Matrix4f INVERSE = new Matrix4f();
    private static float lastHeight;
    /** Whether the water shader was fed last frame (debug/benchmark). */
    public static boolean lastRan;

    private WaterShading() {
    }

    /** True if the active translucent terrain program is the mod's override (not replaced by a pack or another mod). */
    public static boolean shaderBound() {
        ShaderProgram program = GameRenderer.getRenderTypeTranslucentProgram();
        return program != null && program.getUniform("LWWater") != null;
    }

    public static String status() {
        return "shader bound=" + shaderBound() + " ran=" + lastRan + " wave height=" + lastHeight + " sodium=" + Compat.SODIUM;
    }

    /**
     * Copies the frame as it stands and feeds this frame's wind, sun, sky and options to the translucent
     * terrain shader. Called after opaque terrain, entities and footprints, right before translucent terrain.
     */
    public static void prepare(WorldRenderContext context) {
        lastRan = false;
        if (Compat.SODIUM) {
            // Sodium draws terrain and fluids with its own mesher and shaders: nothing here would be used.
            return;
        }
        ShaderProgram program = GameRenderer.getRenderTypeTranslucentProgram();
        if (program == null) {
            return;
        }
        // Null when a resource pack replaced the shader with one that does not know these uniforms.
        GlUniform water = program.getUniform("LWWater");
        GlUniform sky = program.getUniform("LWSky");
        if (water == null || sky == null) {
            return;
        }
        ClientConfig config = ClientConfig.get();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        Atmosphere atmosphere = Atmosphere.INSTANCE;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean active = config.enabled && config.waterShading && env.inWorld && client.world != null
                && Compat.customShadersAllowed();
        if (!active) {
            // Alpha 0 switches the shader back to the vanilla look.
            water.set(0f, 1f, 0f, 0f);
            sky.set(0.5f, 0.65f, 0.9f, 0f);
            lastHeight = 0f;
            return;
        }

        Framebuffer scene = PostProcessor.copyMain(true);
        program.addSampler("LWScene", scene.getColorAttachment());
        program.addSampler("LWDepth", scene.getDepthAttachment());

        float tickDelta = context.tickDelta();
        // Calm water still breathes; a storm roughly doubles the swell.
        float height = config.waveHeight * LWMath.clamp(0.45f + env.wind.speed * 0.55f, 0.45f, 1.6f);
        lastHeight = height;
        float seconds = (float) (((env.ticks % 72000L) + tickDelta) / 20.0);
        water.set(seconds, env.wind.dirX, env.wind.dirZ, height);

        Vec3d cam = context.camera().getPos();
        GlUniform cameraPos = program.getUniform("LWCamera");
        if (cameraPos != null) {
            cameraPos.set((float) (cam.x - Math.floor(cam.x / 4096.0) * 4096.0), (float) cam.y,
                    (float) (cam.z - Math.floor(cam.z / 4096.0) * 4096.0));
        }

        GlUniform sun = program.getUniform("LWSun");
        if (sun != null) {
            sun.set(env.lightX, env.lightY, env.lightZ, env.lightStrength);
        }
        GlUniform sunColor = program.getUniform("LWSunColor");
        if (sunColor != null) {
            float warm = LWMath.smoothstep(0.5f, 0.05f, env.sunElevation);
            if (env.sunElevation < -0.08f) {
                sunColor.set(0.6f, 0.7f, 0.95f);
            } else {
                sunColor.set(1f, LWMath.lerp(0.96f, 0.7f, warm), LWMath.lerp(0.86f, 0.4f, warm));
            }
        }

        // The colour overhead: blue when clear, the grey of the cloud deck when overcast.
        Vec3d skyColor = client.world.getSkyColor(cam, tickDelta);
        float lit = Math.max(0.06f, env.daylight);
        float dark = atmosphere.cloudDarkness;
        float cover = atmosphere.cloudDeck;
        sky.set(LWMath.lerp((float) skyColor.x * 1.1f + 0.04f, LWMath.lerp(0.76f, 0.3f, dark) * lit, cover),
                LWMath.lerp((float) skyColor.y * 1.1f + 0.04f, LWMath.lerp(0.78f, 0.32f, dark) * lit, cover),
                LWMath.lerp((float) skyColor.z * 1.1f + 0.04f, LWMath.lerp(0.82f, 0.36f, dark) * lit, cover), 1f);

        INVERSE.set(context.projectionMatrix()).mul(context.matrixStack().peek().getPositionMatrix());
        GlUniform forward = program.getUniform("LWViewProj");
        if (forward != null) {
            forward.set(INVERSE);
        }
        INVERSE.invert();
        GlUniform inverse = program.getUniform("LWInvViewProj");
        if (inverse != null) {
            inverse.set(INVERSE);
        }
        GlUniform screen = program.getUniform("LWScreen");
        if (screen != null) {
            screen.set(1f / scene.textureWidth, 1f / scene.textureHeight);
        }
        GlUniform options = program.getUniform("LWOptions");
        if (options != null) {
            options.set(config.waterReflections ? QualityManager.preset().reflectionSteps : 0f,
                    config.caustics ? 1f : 0f, env.rain, config.reduceDistortion ? 0.35f : 1f);
        }
        lastRan = true;
    }
}
