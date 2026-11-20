package dev.livingworld.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
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
    private static final Identifier NOISE = LivingWorld.id("textures/fx/noise.png");

    private static Framebuffer copy;
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Vector4f SUN = new Vector4f();
    /** Whether the pass ran last frame (debug/benchmark). */
    public static boolean lastRan;

    private PostProcessor() {
    }

    public static boolean available() {
        return LWShaders.post != null && Compat.postProcessingAllowed() && QualityManager.preset().post;
    }

    /** Copies the main framebuffer (optionally with depth) into the scratch target and returns it. */
    static Framebuffer copyMain(boolean withDepth) {
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        int w = main.textureWidth, h = main.textureHeight;
        if (copy == null) {
            copy = new SimpleFramebuffer(w, h, true, MinecraftClient.IS_SYSTEM_MAC);
            copy.setTexFilter(GL11.GL_LINEAR);
        } else if (copy.textureWidth != w || copy.textureHeight != h) {
            copy.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
            copy.setTexFilter(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h,
                withDepth ? GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT : GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.beginWrite(false);
        return copy;
    }

    /** Draws a clip-space quad covering the screen with the given program. */
    static void drawFullscreen(ShaderProgram program) {
        RenderSystem.setShader(() -> program);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(-1.0, -1.0, 0.0).texture(0f, 0f).next();
        buffer.vertex(1.0, -1.0, 0.0).texture(1f, 0f).next();
        buffer.vertex(1.0, 1.0, 0.0).texture(1f, 1f).next();
        buffer.vertex(-1.0, 1.0, 0.0).texture(0f, 1f).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static Framebuffer bloomA, bloomB;

    private static Framebuffer scratch(Framebuffer target, int w, int h) {
        if (target == null) {
            target = new SimpleFramebuffer(w, h, false, MinecraftClient.IS_SYSTEM_MAC);
            target.setTexFilter(GL11.GL_LINEAR);
        } else if (target.textureWidth != w || target.textureHeight != h) {
            target.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
            target.setTexFilter(GL11.GL_LINEAR);
        }
        return target;
    }

    /**
     * Bloom: keeps the brightest parts of the frame at quarter resolution, blurs them with two rounds of a
     * separable Gaussian and returns the texture for the main pass to add back. Five small draws in total.
     */
    private static int bloom(Framebuffer source, float threshold) {
        ShaderProgram program = LWShaders.bloom;
        int w = Math.max(16, source.textureWidth / 4), h = Math.max(16, source.textureHeight / 4);
        bloomA = scratch(bloomA, w, h);
        bloomB = scratch(bloomB, w, h);

        bloomA.beginWrite(true);
        program.addSampler("DiffuseSampler", source.getColorAttachment());
        LWShaders.set(program, "Step", 1f / source.textureWidth, 1f / source.textureHeight);
        LWShaders.set(program, "Mode", 0f, threshold);
        drawFullscreen(program);
        for (int round = 0; round < 2; round++) {
            float spread = 1f + round * 1.6f;
            bloomB.beginWrite(true);
            program.addSampler("DiffuseSampler", bloomA.getColorAttachment());
            LWShaders.set(program, "Step", spread / w, 0f);
            LWShaders.set(program, "Mode", 1f, threshold);
            drawFullscreen(program);
            bloomA.beginWrite(true);
            program.addSampler("DiffuseSampler", bloomB.getColorAttachment());
            LWShaders.set(program, "Step", 0f, spread / h);
            drawFullscreen(program);
        }
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        return bloomA.getColorAttachment();
    }

    public static void render(Camera camera, Matrix4f projection, Matrix4f view, float tickDelta) {
        lastRan = false;
        CameraSubmersionType submersion = camera.getSubmersionType();
        boolean under = submersion == CameraSubmersionType.WATER;
        if (!available() || (submersion != CameraSubmersionType.NONE && !under)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        Atmosphere atmosphere = Atmosphere.INSTANCE;
        ClientConfig config = ClientConfig.get();
        ShaderProgram program = LWShaders.post;

        Framebuffer source = copyMain(true);
        client.getTextureManager().getTexture(NOISE).setFilter(true, false);

        // How bright the surroundings are: full daylight under open sky is 1, night and caves are 0.
        // Bloom is strong and eager in the dark (a torch glows) and restrained in daylight (only the sun,
        // sunlit snow, clouds and glitter on water bloom), the way an eye or a camera adapts.
        float bright = LWMath.clamp01(env.daylight * env.skyLight);
        boolean blooming = config.bloom && config.bloomStrength > 0.01f && LWShaders.bloom != null && QualityManager.preset().bloom;
        int bloomTexture = blooming ? bloom(source, LWMath.lerp(0.74f, 0.86f, bright))
                : client.getTextureManager().getTexture(NOISE).getGlId();
        program.addSampler("BloomSampler", bloomTexture);
        LWShaders.set(program, "Look", blooming ? config.bloomStrength * LWMath.lerp(0.62f, 0.42f, bright) : 0f,
                config.filmLook, config.vignette, 0.22f);
        LWShaders.set(program, "Under", under ? 1f : 0f, under && config.caustics ? 1f : 0f,
                config.reduceDistortion ? 0f : 1f, under ? LWMath.clamp01(env.daylight * (0.25f + 0.75f * env.skyLight)) : 0f);

        program.addSampler("DiffuseSampler", source.getColorAttachment());
        program.addSampler("DepthSampler", source.getDepthAttachment());
        program.addSampler("NoiseSampler", client.getTextureManager().getTexture(NOISE).getGlId());
        program.addSampler("ColumnSampler", env.columns.textureId());

        INVERSE.set(projection).mul(view).invert();
        GlUniform inverse = program.getUniform("InvViewProj");
        if (inverse != null) {
            inverse.set(INVERSE);
        }

        Vec3d cam = camera.getPos();
        float camX = (float) (cam.x - Math.floor(cam.x / 4096.0) * 4096.0);
        float camZ = (float) (cam.z - Math.floor(cam.z / 4096.0) * 4096.0);
        LWShaders.set(program, "CamPos", camX, (float) cam.y, camZ);

        float seconds = (float) (((env.worldTime % 72000L) + tickDelta) / 20.0);
        LWShaders.set(program, "TimeWind", seconds, env.wind.dirX, env.wind.dirZ, env.wind.speed);
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
        LWShaders.set(program, "Heat", atmosphere.heatShimmer, env.flash * config.flashScale(), atmosphere.exposure,
                atmosphere.saturation);
        LWShaders.set(program, "Grade", atmosphere.tintR, atmosphere.tintG, atmosphere.tintB, atmosphere.contrast);
        LWShaders.set(program, "Wet", atmosphere.wetSurfaces * 0.8f, atmosphere.puddles, env.rain,
                atmosphere.snowCover);

        // Puddles mirror the sky: blue when clear, the grey of the cloud deck when overcast.
        Vec3d skyColor = client.world.getSkyColor(cam, tickDelta);
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

        // Where the sun sits on screen, for the light shafts.
        float shafts = atmosphere.lightShafts;
        float sunU = 0.5f, sunV = 0.5f, sunVisible = 0f;
        if (shafts > 0.003f) {
            float angle = client.world.getSkyAngleRadians(tickDelta);
            SUN.set(-(float) Math.sin(angle) * 100f, (float) Math.cos(angle) * 100f, 0f, 1f);
            SUN.mul(view).mul(projection);
            if (SUN.w > 0.01f) {
                sunU = SUN.x / SUN.w * 0.5f + 0.5f;
                sunV = SUN.y / SUN.w * 0.5f + 0.5f;
                sunVisible = sunU > -0.6f && sunU < 1.6f && sunV > -0.6f && sunV < 1.6f ? 1f : 0f;
            }
        }
        float warm = LWMath.smoothstep(0.5f, 0.05f, env.sunElevation);
        Framebuffer main = client.getFramebuffer();

        // Glow around the sun (warm and strong when it is low) or, at night, a faint cool one around the moon.
        float skyAngle = client.world.getSkyAngleRadians(tickDelta);
        float sx = -(float) Math.sin(skyAngle), sy = (float) Math.cos(skyAngle);
        boolean moon = sy < -0.1f;
        float clear = (1f - atmosphere.cloudDeck) * (1f - 0.7f * env.storm) * (env.natural ? 1f : 0f);
        float glow = config.sunGlow ? clear * (moon ? 0.16f : LWMath.lerp(0.42f, 0.95f, warm)) : 0f;
        // The sun's glow lingers a little after it has set, then the moon's takes over.
        glow *= moon ? LWMath.smoothstep(0.1f, 0.22f, -sy) : LWMath.smoothstep(-0.1f, 0f, sy);
        LWShaders.set(program, "SunDir", moon ? -sx : sx, moon ? -sy : sy, 0f);
        if (moon) {
            LWShaders.set(program, "Glow", 0.55f, 0.66f, 0.9f, glow);
        } else {
            LWShaders.set(program, "Glow", 1f, LWMath.lerp(0.93f, 0.66f, warm), LWMath.lerp(0.8f, 0.36f, warm), glow);
        }
        LWShaders.set(program, "Sun", sunU, sunV, shafts, sunVisible);
        LWShaders.set(program, "SunColor", 1f, LWMath.lerp(0.95f, 0.78f, warm), LWMath.lerp(0.82f, 0.5f, warm),
                (float) main.textureWidth / main.textureHeight);

        drawFullscreen(program);
        lastRan = true;
    }
}
