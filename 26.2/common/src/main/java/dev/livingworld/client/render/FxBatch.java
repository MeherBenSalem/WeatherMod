package dev.livingworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingworld.LivingWorld;
import java.util.Arrays;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * Everything that uses the particle sprite sheet (simulated particles and precipitation), collected on the CPU
 * during the frame and handed to the game's renderer as one piece of custom geometry with the stock translucent
 * entity render type. Positions are relative to the camera.
 *
 * <p>This version of the game draws through render states and submitted geometry; the mod's own soft-particle
 * program does not exist here, so sprites have the stock (harder) edges.
 */
public final class FxBatch {
    public static final Identifier SHEET = LivingWorld.id("textures/fx/particles.png");
    private static final int FLOATS = 5;

    private final Identifier texture;

    private float[] pos = new float[FLOATS * 4096];
    private int[] colour = new int[4096];
    private int[] lights = new int[4096];
    private int vertices;
    /** Quads drawn by the previous frame (debug/benchmark). */
    public int lastQuads;

    // Sprites are hazed here by their own distance (the full-screen atmosphere pass of other versions is not
    // available, so this only applies when a haze density is set).
    public float hazeR, hazeG, hazeB;
    private float hazeDensity, hazeMax;

    public FxBatch() {
        this(SHEET);
    }

    public FxBatch(Identifier texture) {
        this.texture = texture;
    }

    public void setHaze(float r, float g, float b, float density, float max) {
        hazeR = r;
        hazeG = g;
        hazeB = b;
        hazeDensity = density;
        hazeMax = max;
    }

    /** Fraction (0..1) by which something at this distance is replaced by the haze colour. */
    public float haze(float distance) {
        return hazeDensity <= 1.0e-5f ? 0f : (1f - (float) Math.exp(-hazeDensity * distance)) * hazeMax;
    }

    public void begin() {
        vertices = 0;
    }

    public void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a, int light) {
        if (vertices == colour.length) {
            int size = colour.length * 2;
            pos = Arrays.copyOf(pos, size * FLOATS);
            colour = Arrays.copyOf(colour, size);
            lights = Arrays.copyOf(lights, size);
        }
        int o = vertices * FLOATS;
        pos[o] = x;
        pos[o + 1] = y;
        pos[o + 2] = z;
        pos[o + 3] = u;
        pos[o + 4] = v;
        // Colours are stored as bytes: anything above 1 would wrap around rather than saturate.
        colour[vertices] = (byte255(a) << 24) | (byte255(r) << 16) | (byte255(g) << 8) | byte255(b);
        lights[vertices] = light;
        vertices++;
    }

    private static int byte255(float c) {
        return c >= 1f ? 255 : c <= 0f ? 0 : (int) (c * 255f + 0.5f);
    }

    /** Submits the collected quads for this frame. {@code poseStack} is camera-relative, without the camera rotation. */
    public void submit(PoseStack poseStack, SubmitNodeCollector collector) {
        lastQuads = vertices / 4;
        if (vertices < 4) {
            return;
        }
        int count = vertices - vertices % 4;
        float[] p = Arrays.copyOf(pos, count * FLOATS);
        int[] c = Arrays.copyOf(colour, count);
        int[] l = Arrays.copyOf(lights, count);
        RenderType type = RenderTypes.entityTranslucent(texture);
        collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> emit(consumer, pose.pose(), p, c, l, count));
    }

    private static void emit(VertexConsumer consumer, Matrix4f matrix, float[] p, int[] c, int[] l, int count) {
        for (int i = 0; i < count; i++) {
            int o = i * FLOATS;
            int argb = c[i];
            consumer.addVertex(matrix, p[o], p[o + 1], p[o + 2])
                    .setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255)
                    .setUv(p[o + 3], p[o + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(l[i])
                    .setNormal(0f, 1f, 0f);
        }
    }
}
