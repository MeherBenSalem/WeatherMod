package dev.livingworld.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Runs the full-screen atmosphere pass: copies the finished world image (colour and depth) and redraws
 * it through {@code post.fsh}. See the shader for what each term does.
 *
 * <p>The pass is skipped entirely when a shader pack is rendering, when the user disables
 * post-processing, when the program failed to load, or under water — in all of those cases the
 * vanilla-fog hooks in {@link SkyAndFog} still provide the essential atmosphere.
 */
public final class PostProcessor {
    private static final ResourceLocation NOISE = LivingWorld.id("textures/fx/noise.png");

    /** The cloud layer's own frame repeats every this many blocks; every scale in lw_sky.glsl divides it. */
    private static final double CLOUD_PERIOD = 6400.0;

    private static RenderTarget copy;
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Vector4f SUN = new Vector4f();
    /** Whether the pass ran last frame (debug/benchmark). */
    public static boolean lastRan;

    private PostProcessor() {
    }

    /** GL id of the tiling noise texture, set to smooth sampling. */
    public static int noiseTexture() {
        net.minecraft.client.renderer.texture.AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(NOISE);
        texture.setFilter(true, false);
        return texture.getId();
    }

    /**
     * Where the camera sits in the cloud layer's frame: its position plus how far the wind has carried
     * the clouds, wrapped. Shaders add camera-relative positions to this.
     */
    public static float cloudOriginX(double camX, float tickDelta) {
        double v = camX + ClientEnvironment.INSTANCE.cloudTravelX(tickDelta);
        return (float) (v - Math.floor(v / CLOUD_PERIOD) * CLOUD_PERIOD);
    }

    public static float cloudOriginZ(double camZ, float tickDelta) {
        double v = camZ + ClientEnvironment.INSTANCE.cloudTravelZ(tickDelta);
        return (float) (v - Math.floor(v / CLOUD_PERIOD) * CLOUD_PERIOD);
    }

    public static boolean available() {
        return LWShaders.post != null && Compat.postProcessingAllowed() && QualityManager.preset().post;
    }

    /** Copies the main framebuffer (optionally with depth) into the scratch target and returns it. */
    static RenderTarget copyMain(boolean withDepth) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width, h = main.height;
        if (copy == null) {
            copy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h,
                withDepth ? GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT : GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);
        return copy;
    }

    /** Draws a clip-space quad covering the screen with the given program. */
    static void drawFullscreen(ShaderInstance program) {
        RenderSystem.setShader(() -> program);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(-1.0, -1.0, 0.0).uv(0f, 0f).endVertex();
        buffer.vertex(1.0, -1.0, 0.0).uv(1f, 0f).endVertex();
        buffer.vertex(1.0, 1.0, 0.0).uv(1f, 1f).endVertex();
        buffer.vertex(-1.0, 1.0, 0.0).uv(0f, 1f).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static RenderTarget bloomA, bloomB;
    private static RenderTarget cloudBuffer;
    /** Whether the sky's clouds came from the reduced-resolution buffer last frame (debug). */
    public static boolean lastCloudBuffer;

    /** Everything the cloud layer needs, for whichever program is about to draw it. */
    private static void cloudUniforms(ShaderInstance program, Vec3 cam, float tickDelta, float[] horizon) {
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        Atmosphere atmosphere = Atmosphere.INSTANCE;
        LWShaders.set(program, "CloudShape", atmosphere.cloudsDrawn ? atmosphere.cloudCoverage : 0f,
                atmosphere.cloudBase - (float) cam.y, atmosphere.cloudThickness, QualityManager.preset().cloudSteps);
        LWShaders.set(program, "CloudOrigin", cloudOriginX(cam.x, tickDelta), cloudOriginZ(cam.z, tickDelta), env.storm,
                atmosphere.cloudShadow);
        LWShaders.set(program, "CloudLit", atmosphere.cloudLitR, atmosphere.cloudLitG, atmosphere.cloudLitB, 0f);
        LWShaders.set(program, "CloudAmbient", atmosphere.cloudAmbientR, atmosphere.cloudAmbientG, atmosphere.cloudAmbientB, 0f);
        LWShaders.set(program, "Horizon", horizon[0], horizon[1], horizon[2], atmosphere.skyColours);
        // Lightning, as a light at the place of the stroke.
        float power = env.boltPower * ClientConfig.get().flashScale();
        LWShaders.set(program, "Bolt", (float) (env.boltX - cam.x), (float) (env.boltY - cam.y), (float) (env.boltZ - cam.z), power);
    }

    /**
     * Draws the cloud layer into a buffer at a share of the screen's resolution and returns its texture.
     * The main pass then only has to look the clouds up for every pixel of open sky.
     */
    private static int cloudBuffer(RenderTarget source, Matrix4f inverseViewProj, Vec3 cam, float tickDelta, float[] horizon,
                                   float sunX, float sunY, float sunZ, int noise, float scale) {
        ShaderInstance program = LWShaders.clouds;
        int w = Math.max(16, Math.round(source.width * scale)), h = Math.max(16, Math.round(source.height * scale));
        if (cloudBuffer == null) {
            cloudBuffer = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            cloudBuffer.setFilterMode(GL11.GL_LINEAR);
        } else if (cloudBuffer.width != w || cloudBuffer.height != h) {
            cloudBuffer.resize(w, h, Minecraft.ON_OSX);
            cloudBuffer.setFilterMode(GL11.GL_LINEAR);
        }
        cloudBuffer.bindWrite(true);
        program.setSampler("NoiseSampler", noise);
        Uniform inverse = program.getUniform("InvViewProj");
        if (inverse != null) {
            inverse.set(inverseViewProj);
        }
        cloudUniforms(program, cam, tickDelta, horizon);
        LWShaders.set(program, "SunDir", sunX, sunY, sunZ);
        drawFullscreen(program);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        return cloudBuffer.getColorTextureId();
    }

    private static RenderTarget scratch(RenderTarget target, int w, int h) {
        if (target == null) {
            target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        } else if (target.width != w || target.height != h) {
            target.resize(w, h, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        }
        return target;
    }

    /**
     * Bloom: keeps the brightest parts of the frame at quarter resolution, blurs them with two rounds of a
     * separable Gaussian and returns the texture for the main pass to add back. Five small draws in total.
     */
    private static int bloom(RenderTarget source, float threshold, float skyBloom) {
        ShaderInstance program = LWShaders.bloom;
        int w = Math.max(16, source.width / 4), h = Math.max(16, source.height / 4);
        bloomA = scratch(bloomA, w, h);
        bloomB = scratch(bloomB, w, h);

        bloomA.bindWrite(true);
        program.setSampler("DiffuseSampler", source.getColorTextureId());
        program.setSampler("DepthSampler", source.getDepthTextureId());
        LWShaders.set(program, "SkyBloom", skyBloom);
        LWShaders.set(program, "Step", 1f / source.width, 1f / source.height);
        LWShaders.set(program, "Mode", 0f, threshold);
        drawFullscreen(program);
        for (int round = 0; round < 2; round++) {
            float spread = 1f + round * 1.6f;
            bloomB.bindWrite(true);
            program.setSampler("DiffuseSampler", bloomA.getColorTextureId());
            LWShaders.set(program, "Step", spread / w, 0f);
            LWShaders.set(program, "Mode", 1f, threshold);
            drawFullscreen(program);
            bloomA.bindWrite(true);
            program.setSampler("DiffuseSampler", bloomB.getColorTextureId());
            LWShaders.set(program, "Step", 0f, spread / h);
            drawFullscreen(program);
        }
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        return bloomA.getColorTextureId();
    }

    public static void render(Camera camera, Matrix4f projection, Matrix4f view, float tickDelta) {
        lastRan = false;
        FogType submersion = camera.getFluidInCamera();
        boolean under = submersion == FogType.WATER;
        if (!available() || (submersion != FogType.NONE && !under)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        Atmosphere atmosphere = Atmosphere.INSTANCE;
        ClientConfig config = ClientConfig.get();
        ShaderInstance program = LWShaders.post;

        RenderTarget source = copyMain(true);
        int noise = noiseTexture();

        // How bright the surroundings are: full daylight under open sky is 1, night and caves are 0.
        // Bloom is strong and eager in the dark (a torch glows) and restrained in daylight (only the sun,
        // sunlit snow, clouds and glitter on water bloom), the way an eye or a camera adapts.
        float bright = LWMath.clamp01(env.daylight * env.skyLight);
        boolean blooming = config.bloom && config.bloomStrength > 0.01f && LWShaders.bloom != null && QualityManager.preset().bloom;
        int bloomTexture = blooming ? bloom(source, LWMath.lerp(0.74f, 0.86f, bright), 1f - 0.88f * env.daylight)
                : noise;
        program.setSampler("BloomSampler", bloomTexture);
        LWShaders.set(program, "Look", blooming ? config.bloomStrength * LWMath.lerp(0.62f, 0.42f, bright) : 0f,
                config.filmLook, config.vignette, 0.22f);
        LWShaders.set(program, "Under", under ? 1f : 0f, under && config.caustics ? 1f : 0f,
                config.reduceDistortion ? 0f : 1f, under ? LWMath.clamp01(env.daylight * (0.25f + 0.75f * env.skyLight)) : 0f);

        program.setSampler("DiffuseSampler", source.getColorTextureId());
        program.setSampler("DepthSampler", source.getDepthTextureId());
        program.setSampler("NoiseSampler", noise);
        program.setSampler("ColumnSampler", env.columns.textureId());

        INVERSE.set(projection).mul(view);
        Uniform forward = program.getUniform("ViewProj");
        if (forward != null) {
            forward.set(INVERSE);
        }
        INVERSE.invert();
        Uniform inverse = program.getUniform("InvViewProj");
        if (inverse != null) {
            inverse.set(INVERSE);
        }

        Vec3 cam = camera.getPosition();
        float camX = (float) (cam.x - Math.floor(cam.x / 4096.0) * 4096.0);
        float camZ = (float) (cam.z - Math.floor(cam.z / 4096.0) * 4096.0);
        LWShaders.set(program, "CamPos", camX, (float) cam.y, camZ);

        float seconds = (float) (((env.worldTime % 72000L) + tickDelta) / 20.0);
        LWShaders.set(program, "TimeWind", seconds, env.wind.dirX, env.wind.dirZ, env.wind.speed);
        // The same wave clock and ripple drift the water shader uses, plus how far a puddle's mirror ray is followed.
        LWShaders.set(program, "Wave", env.waveClock(tickDelta), env.windTravelX(tickDelta, 0.05, 4096.0),
                env.windTravelZ(tickDelta, 0.05, 4096.0), config.waterReflections ? Math.min(12f, QualityManager.preset().reflectionSteps) : 0f);
        LWShaders.set(program, "Haze", atmosphere.hazeR, atmosphere.hazeG, atmosphere.hazeB, atmosphere.hazeDensity);
        // Mist scatters the light of the sky around it: lean on the horizon colour the game is drawing right now,
        // so mist glows at dawn and stays dark at night without a separate lighting model.
        float[] fog = RenderSystem.getShaderFogColor();
        float mistR = LWMath.lerp(atmosphere.mistR, Math.min(1f, fog[0] * 1.12f + 0.04f), 0.6f);
        float mistG = LWMath.lerp(atmosphere.mistG, Math.min(1f, fog[1] * 1.12f + 0.04f), 0.6f);
        float mistB = LWMath.lerp(atmosphere.mistB, Math.min(1f, fog[2] * 1.12f + 0.04f), 0.6f);
        LWShaders.set(program, "Mist", mistR, mistG, mistB, atmosphere.mistDensity);
        LWShaders.set(program, "MistShape", (float) cam.y - atmosphere.mistBase, atmosphere.mistFalloff,
                config.reduceFog ? 0.45f : 0.78f, atmosphere.hazeMax);
        // Most of a flash is now the light of the stroke where it stands (see Bolt); a little still
        // reaches everything, as it does through the air.
        LWShaders.set(program, "Heat", atmosphere.heatShimmer, env.flash * config.flashScale() * 0.4f, atmosphere.exposure,
                atmosphere.saturation);
        LWShaders.set(program, "Grade", atmosphere.tintR, atmosphere.tintG, atmosphere.tintB, atmosphere.contrast);
        LWShaders.set(program, "Wet", atmosphere.wetSurfaces * 0.8f, atmosphere.puddles, env.rain,
                atmosphere.snowCover);

        // Puddles mirror the sky: blue when clear, the grey of the cloud deck when overcast.
        Vec3 skyColor = client.level.getSkyColor(cam, tickDelta);
        float lit = Math.max(0.06f, env.daylight);
        float dark = atmosphere.cloudDarkness;
        float cloudR = LWMath.lerp(0.76f, 0.3f, dark) * lit;
        float cloudG = LWMath.lerp(0.78f, 0.32f, dark) * lit;
        float cloudB = LWMath.lerp(0.82f, 0.36f, dark) * lit;
        float cover = atmosphere.cloudDeck;
        LWShaders.set(program, "Sky",
                LWMath.lerp((float) skyColor.x * 1.15f + 0.05f, cloudR, cover),
                LWMath.lerp((float) skyColor.y * 1.15f + 0.05f, cloudG, cover),
                LWMath.lerp((float) skyColor.z * 1.15f + 0.05f, cloudB, cover), cover);
        LWShaders.set(program, "Cloud", cloudR, cloudG, cloudB, dark);

        LWShaders.set(program, "Shore", config.shoreline && QualityManager.preset().wetness && env.columns.hasWater() ? 1f : 0f,
                0f, 0f, 0f);

        // The real cloud layer, the light on it, and the clear-sky extras.
        cloudUniforms(program, cam, tickDelta, fog);
        LWShaders.set(program, "CloudSeen", atmosphere.cloudSeenR, atmosphere.cloudSeenG, atmosphere.cloudSeenB, 0f);
        LWShaders.set(program, "SkyFx", atmosphere.starlight, atmosphere.aurora, atmosphere.rainbow, 0f);

        // Where the sun sits on screen, for the light shafts.
        float shafts = atmosphere.lightShafts;
        float sunU = 0.5f, sunV = 0.5f, sunVisible = 0f;
        if (shafts > 0.003f) {
            float angle = client.level.getSunAngle(tickDelta);
            SUN.set(-(float) Math.sin(angle) * 100f, (float) Math.cos(angle) * 100f, 0f, 1f);
            SUN.mul(view).mul(projection);
            if (SUN.w > 0.01f) {
                sunU = SUN.x / SUN.w * 0.5f + 0.5f;
                sunV = SUN.y / SUN.w * 0.5f + 0.5f;
                sunVisible = sunU > -0.6f && sunU < 1.6f && sunV > -0.6f && sunV < 1.6f ? 1f : 0f;
            }
        }
        float warm = LWMath.smoothstep(0.5f, 0.05f, env.sunElevation);
        RenderTarget main = client.getMainRenderTarget();

        // Glow around the sun (warm and strong when it is low) or, at night, a faint cool one around the moon.
        float skyAngle = client.level.getSunAngle(tickDelta);
        float sx = -(float) Math.sin(skyAngle), sy = (float) Math.cos(skyAngle);
        boolean moon = sy < -0.1f;
        LWShaders.set(program, "Celestial", sx, sy, 0f, skyAngle);
        // With the cloud layer drawn, the shader dims the glow by the cloud actually in front of the sun.
        float overcast = atmosphere.cloudsDrawn ? LWMath.smoothstep(0.75f, 1f, atmosphere.cloudCoverage) * 0.6f : atmosphere.cloudDeck;
        float clear = (1f - overcast) * (1f - 0.7f * env.storm) * (env.natural ? 1f : 0f);
        float glow = config.sunGlow ? clear * (moon ? 0.16f : LWMath.lerp(0.42f, 0.95f, warm)) : 0f;
        // The sun's glow lingers a little after it has set, then the moon's takes over.
        glow *= moon ? LWMath.smoothstep(0.1f, 0.22f, -sy) : LWMath.smoothstep(-0.1f, 0f, sy);
        LWShaders.set(program, "SunDir", moon ? -sx : sx, moon ? -sy : sy, 0f);

        // The clouds of the open sky, at reduced resolution where the preset asks for it.
        float cloudScale = QualityManager.preset().cloudScale;
        lastCloudBuffer = atmosphere.cloudsDrawn && atmosphere.cloudCoverage > 0.001f && cloudScale < 0.99f
                && LWShaders.clouds != null && !under;
        if (lastCloudBuffer) {
            program.setSampler("CloudSampler", cloudBuffer(source, INVERSE, cam, tickDelta, fog, moon ? -sx : sx, moon ? -sy : sy, 0f,
                    noise, cloudScale));
        } else {
            program.setSampler("CloudSampler", noise);
        }
        LWShaders.set(program, "CloudMode", lastCloudBuffer ? 1f : 0f, 0f, 0f, 0f);
        if (moon) {
            LWShaders.set(program, "Glow", 0.55f, 0.66f, 0.9f, glow);
        } else {
            LWShaders.set(program, "Glow", 1f, LWMath.lerp(0.93f, 0.66f, warm), LWMath.lerp(0.8f, 0.36f, warm), glow);
        }
        LWShaders.set(program, "Sun", sunU, sunV, shafts, sunVisible);
        LWShaders.set(program, "SunColor", 1f, LWMath.lerp(0.95f, 0.78f, warm), LWMath.lerp(0.82f, 0.5f, warm),
                (float) main.width / main.height);

        drawFullscreen(program);
        lastRan = true;
    }
}
