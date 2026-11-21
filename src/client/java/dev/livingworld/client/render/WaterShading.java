package dev.livingworld.client.render;

import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.compat.sodium.SodiumState;
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
 * loses its red with depth, it mirrors the world around it and the sky and clouds above it, the sun
 * glitters on it, and rain rings it. In still air it lies nearly smooth under slow ripples; wind raises
 * waves that run downwind, and surf runs in along the shore.
 *
 * <p>Like {@link VegetationWind} this has two halves. While chunks are meshed, water vertices are
 * tagged (vanilla mesher: a vertex alpha of 250/255, see {@code FluidRendererMixin}; Sodium's mesher: a
 * bit in a spare byte of the light coordinate, see {@code SodiumBridge}). Just before the translucent
 * terrain layer is drawn, the frame so far (colour and depth: everything that can lie behind or be
 * mirrored in the water) is copied and everything the shader needs is worked out once into
 * {@link #FRAME}. The vanilla translucent terrain shader, which the mod overrides, is fed from it here;
 * under Sodium the bridge feeds Sodium's patched terrain shader from the same values.
 *
 * <p>Under an Iris shader pack none of this applies: the pack shades water itself.
 */
public final class WaterShading {
    /** Vertex alpha that marks water in the vanilla translucent terrain layer. */
    public static final float TAG_ALPHA = 250f / 255f;

    /**
     * Share of the wind's travel that the ripples on the water are carried along by. The air moves about
     * eight blocks a second in a gale; ripples drift at a walking pace.
     */
    private static final double RIPPLE_DRIFT = 0.05;

    /** Everything the water shader is fed for one frame, whichever renderer draws the terrain. */
    public static final class Frame {
        /** False when water keeps its plain look this frame (option off, no world, shader pack). */
        public boolean active;
        public float clock, driftX, driftZ, height;
        public float windX, windZ, windSpeed, seconds;
        public float camX, camY, camZ;
        public float sunX, sunY = 1f, sunZ, sunStrength;
        public float sunR = 1f, sunG = 0.95f, sunB = 0.85f;
        public float skyR, skyG, skyB;
        public float cloudCover, cloudHeight, cloudOriginX, cloudOriginZ;
        public float cloudR, cloudG, cloudB;
        public boolean surf;
        public final Matrix4f viewProj = new Matrix4f();
        public final Matrix4f invViewProj = new Matrix4f();
        public final Matrix4f view = new Matrix4f();
        public float pixelX, pixelY;
        public float reflectionSteps, caustics, rain, refraction;
        public int sceneColor, sceneDepth, noise, columns;
    }

    public static final Frame FRAME = new Frame();

    private static float lastHeight;
    /** Whether the water shader was fed last frame (debug/benchmark). */
    public static boolean lastRan;

    private WaterShading() {
    }

    /** True if a terrain shader that knows the water uniforms is in use: the mod's override, or Sodium's patched one. */
    public static boolean shaderBound() {
        if (Compat.SODIUM) {
            return SodiumState.waterReady();
        }
        ShaderProgram program = GameRenderer.getRenderTypeTranslucentProgram();
        return program != null && program.getUniform("LWWater") != null;
    }

    public static String status() {
        return "shader bound=" + shaderBound() + " ran=" + lastRan + " wave height=" + lastHeight + " sodium=" + Compat.SODIUM
                + (Compat.SODIUM ? " (" + SodiumState.describe() + ")" : "");
    }

    /**
     * Copies the frame as it stands and works out this frame's wind, sun, sky and options for the water
     * shader. Called after opaque terrain, entities and footprints, right before translucent terrain.
     */
    public static void prepare(WorldRenderContext context) {
        lastRan = false;
        Frame f = FRAME;
        boolean sodium = Compat.SODIUM;
        ShaderProgram program = sodium ? null : GameRenderer.getRenderTypeTranslucentProgram();
        // Null when a resource pack replaced the shader with one that does not know these uniforms.
        GlUniform water = program == null ? null : program.getUniform("LWWater");
        GlUniform sky = program == null ? null : program.getUniform("LWSky");
        if (sodium ? !SodiumState.waterReady() : water == null || sky == null) {
            f.active = false;
            return;
        }
        ClientConfig config = ClientConfig.get();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        Atmosphere atmosphere = Atmosphere.INSTANCE;
        MinecraftClient client = MinecraftClient.getInstance();
        f.active = config.enabled && config.waterShading && env.inWorld && client.world != null && Compat.customShadersAllowed();
        if (!f.active) {
            if (!sodium) {
                // Alpha 0 switches the shader back to the vanilla look.
                water.set(0f, 1f, 0f, 0f);
                sky.set(0.5f, 0.65f, 0.9f, 0f);
            }
            lastHeight = 0f;
            return;
        }

        Framebuffer scene = PostProcessor.copyMain(true);
        f.sceneColor = scene.getColorAttachment();
        f.sceneDepth = scene.getDepthAttachment();
        f.noise = PostProcessor.noiseTexture();
        // The terrain column cache, for the surf: it knows how near land each column of water is.
        f.columns = env.columns.textureId();
        f.pixelX = 1f / scene.textureWidth;
        f.pixelY = 1f / scene.textureHeight;

        float tickDelta = context.tickDelta();
        // The wave clock and the ripple drift are running totals kept by the environment, so a change in
        // the wind changes how the water moves from here on and never where the pattern already is.
        f.height = config.waveHeight;
        lastHeight = f.height;
        f.clock = env.waveClock(tickDelta);
        f.driftX = env.windTravelX(tickDelta, RIPPLE_DRIFT, 4096.0);
        f.driftZ = env.windTravelZ(tickDelta, RIPPLE_DRIFT, 4096.0);
        f.windX = env.wind.dirX;
        f.windZ = env.wind.dirZ;
        f.windSpeed = env.wind.speed;
        f.seconds = (float) (((env.ticks % 72000L) + tickDelta) / 20.0);

        Vec3d cam = context.camera().getPos();
        f.camX = (float) (cam.x - Math.floor(cam.x / 4096.0) * 4096.0);
        f.camY = (float) cam.y;
        f.camZ = (float) (cam.z - Math.floor(cam.z / 4096.0) * 4096.0);

        f.sunX = env.lightX;
        f.sunY = env.lightY;
        f.sunZ = env.lightZ;
        f.sunStrength = env.lightStrength;
        if (env.sunElevation < -0.08f) {
            f.sunR = 0.6f;
            f.sunG = 0.7f;
            f.sunB = 0.95f;
        } else {
            float warm = LWMath.smoothstep(0.5f, 0.05f, env.sunElevation);
            f.sunR = 1f;
            f.sunG = LWMath.lerp(0.96f, 0.7f, warm);
            f.sunB = LWMath.lerp(0.86f, 0.4f, warm);
        }

        // The colour overhead: blue when clear, the grey of the flat cloud deck when that is what is drawn.
        Vec3d skyColor = client.world.getSkyColor(cam, tickDelta);
        float lit = Math.max(0.06f, env.daylight);
        float dark = atmosphere.cloudDarkness;
        float cover = atmosphere.cloudDeck;
        f.skyR = LWMath.lerp((float) skyColor.x * 1.1f + 0.04f, LWMath.lerp(0.76f, 0.3f, dark) * lit, cover);
        f.skyG = LWMath.lerp((float) skyColor.y * 1.1f + 0.04f, LWMath.lerp(0.78f, 0.32f, dark) * lit, cover);
        f.skyB = LWMath.lerp((float) skyColor.z * 1.1f + 0.04f, LWMath.lerp(0.82f, 0.36f, dark) * lit, cover);

        // The cloud layer, for its mirror image.
        f.cloudCover = atmosphere.cloudsDrawn ? atmosphere.cloudCoverage : 0f;
        f.cloudHeight = atmosphere.cloudBase + atmosphere.cloudThickness * 0.35f - (float) cam.y;
        f.cloudOriginX = PostProcessor.cloudOriginX(cam.x, tickDelta);
        f.cloudOriginZ = PostProcessor.cloudOriginZ(cam.z, tickDelta);
        f.cloudR = atmosphere.cloudSeenR;
        f.cloudG = atmosphere.cloudSeenG;
        f.cloudB = atmosphere.cloudSeenB;
        f.surf = config.shoreline;

        f.view.set(context.matrixStack().peek().getPositionMatrix());
        f.viewProj.set(context.projectionMatrix()).mul(f.view);
        f.invViewProj.set(f.viewProj).invert();

        f.reflectionSteps = config.waterReflections ? QualityManager.preset().reflectionSteps : 0f;
        f.caustics = config.caustics ? 1f : 0f;
        f.rain = env.rain;
        f.refraction = config.reduceDistortion ? 0.35f : 1f;

        if (!sodium) {
            feedVanilla(program, water, sky, f);
        }
        lastRan = true;
    }

    /** Hands the frame's values to the mod's override of the vanilla translucent terrain shader. */
    private static void feedVanilla(ShaderProgram program, GlUniform water, GlUniform sky, Frame f) {
        program.addSampler("LWScene", f.sceneColor);
        program.addSampler("LWDepth", f.sceneDepth);
        program.addSampler("LWNoise", f.noise);
        program.addSampler("LWColumns", f.columns);
        water.set(f.clock, f.driftX, f.driftZ, f.height);
        sky.set(f.skyR, f.skyG, f.skyB, 1f);
        LWShaders.set(program, "LWWind", f.windX, f.windZ, f.windSpeed, f.seconds);
        LWShaders.set(program, "LWCamera", f.camX, f.camY, f.camZ);
        LWShaders.set(program, "LWSun", f.sunX, f.sunY, f.sunZ, f.sunStrength);
        LWShaders.set(program, "LWSunColor", f.sunR, f.sunG, f.sunB);
        LWShaders.set(program, "LWCloud", f.cloudCover, f.cloudHeight, f.cloudOriginX, f.cloudOriginZ);
        LWShaders.set(program, "LWCloudColor", f.cloudR, f.cloudG, f.cloudB, f.surf ? 1f : 0f);
        GlUniform forward = program.getUniform("LWViewProj");
        if (forward != null) {
            forward.set(f.viewProj);
        }
        GlUniform inverse = program.getUniform("LWInvViewProj");
        if (inverse != null) {
            inverse.set(f.invViewProj);
        }
        LWShaders.set(program, "LWScreen", f.pixelX, f.pixelY);
        LWShaders.set(program, "LWOptions", f.reflectionSteps, f.caustics, f.rain, f.refraction);
    }
}
