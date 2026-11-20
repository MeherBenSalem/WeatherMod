package dev.livingworld.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.footprint.DecalStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws every live decal in one batched call as terrain-hugging quads.
 *
 * <p>The prints are pixel art at the block textures' own texel density, snapped to the texel grid.
 * Two paths: the lit path uses the mod's decal program, which reads a per-texel normal and height and
 * shades each texel against the actual sun or moon direction (so a print's bright and dark edge texels
 * swap sides through the day, and prints vanish texel by texel, shallowest first, as they wear). The
 * simple path draws a pre-shaded sheet with the stock particle program and is used when custom programs
 * are not safe to use.
 */
public final class DecalRenderer {
    private static final Identifier NORMAL_MAP = LivingWorld.id("textures/fx/decals_n.png");
    private static final Identifier SHADED = LivingWorld.id("textures/fx/decals.png");
    /** The sheets are a 4x4 grid of 16-texel cells; one cell covers exactly one block. */
    private static final float CELL = 0.25f;

    /** Decals actually submitted last frame (debug/benchmark). */
    public static int lastDrawn;

    private DecalRenderer() {
    }

    public static void render(MatrixStack matrices, Camera camera, DecalStore store, ClientEnvironment env) {
        lastDrawn = 0;
        int n = store.count;
        if (n == 0) {
            return;
        }
        ClientConfig config = ClientConfig.get();
        boolean lit = config.litFootprints && LWShaders.decal != null && Compat.customShadersAllowed();
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cam = camera.getPos();
        Vector3f forward = camera.getHorizontalPlane();
        float maxDist = QualityManager.effectDistance() * 1.5f;
        float maxDistSq = maxDist * maxDist;
        float fadeStartSq = maxDistSq * 0.55f;

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS,
                lit ? VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
        int drawn = 0;
        for (int i = 0; i < n; i++) {
            float dx = (float) (store.x[i] - cam.x);
            float dy = (float) (store.y[i] - cam.y);
            float dz = (float) (store.z[i] - cam.z);
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistSq) {
                continue;
            }
            if (dx * forward.x() + dy * forward.y() + dz * forward.z() < -1.5f) {
                continue;
            }
            float alpha = store.strength[i];
            if (distSq > fadeStartSq) {
                alpha *= 1f - (distSq - fadeStartSq) / (maxDistSq - fadeStartSq);
            }
            float wear = store.wear[i];
            if (!lit) {
                float keep = 1f - wear;
                alpha *= keep * (store.stain[i] ? 0.6f : (float) Math.sqrt(keep));
            }
            if (alpha <= 0.01f) {
                continue;
            }

            // Blocky prints: every decal is one block-sized cell of pixel art, snapped to the 1/16 texel grid
            // of the block textures and turned in 45-degree steps, so its texels line up with the ground's.
            int facing = Math.round((float) (Math.atan2(store.dirZ[i], store.dirX[i]) / (Math.PI / 4.0))) & 7;
            int cell = store.sprite[i];
            if ((facing & 1) == 1) {
                int diagonal = cell == DecalStore.BOOT ? 12 : cell == DecalStore.BOOT_SPRINT ? 13 : cell == DecalStore.SOLE ? 14 : -1;
                if (diagonal >= 0) {
                    cell = diagonal;
                }
                // The diagonal art points between "forward" and "right" of the axis below it.
                facing -= 1;
            }
            int quarter = (facing >> 1) & 3;
            float ax = quarter == 0 ? 1f : quarter == 2 ? -1f : 0f;
            float az = quarter == 1 ? 1f : quarter == 3 ? -1f : 0f;
            float rx = -az, rz = ax;

            float sx = (float) (Math.round(store.x[i] * 16.0) / 16.0 - cam.x);
            float sz = (float) (Math.round(store.z[i] * 16.0) / 16.0 - cam.z);
            // Stagger heights by a hair so overlapping prints never z-fight each other.
            float y = dy + 0.004f + (i & 7) * 0.0007f;

            float cu = (cell & 3) * CELL, cv = (cell >> 2) * CELL;
            float r = store.r[i], g = store.g[i], b = store.b[i];
            int light = store.light[i];
            float mode = store.stain[i] ? 1f : 0f;
            if (!lit && store.stain[i]) {
                r *= 0.6f;
                g *= 0.6f;
                b *= 0.6f;
            }
            for (int corner = 0; corner < 4; corner++) {
                float ox = corner < 2 ? -0.5f : 0.5f;
                float oz = corner == 1 || corner == 2 ? 0.5f : -0.5f;
                // Cell coordinates: u grows to the walker's right, v grows toward the heel.
                float u = cu + (0.5f + ox * rx + oz * rz) * CELL;
                float v = cv + (0.5f - (ox * ax + oz * az)) * CELL;
                if (lit) {
                    buffer.vertex(sx + ox, y, sz + oz).color(r, g, b, alpha).texture(u, v).light(light).normal(wear, mode, 0f).next();
                } else {
                    buffer.vertex(sx + ox, y, sz + oz).texture(u, v).color(r, g, b, alpha).light(light).next();
                }
            }
            drawn++;
        }
        lastDrawn = drawn;
        if (drawn == 0) {
            BufferBuilder.BuiltBuffer empty = buffer.endNullable();
            if (empty != null) {
                empty.release();
            }
            return;
        }

        Identifier texture = lit ? NORMAL_MAP : SHADED;
        client.getTextureManager().getTexture(texture).setFilter(false, false);
        LightmapTextureManager lightmap = client.gameRenderer.getLightmapTextureManager();

        MatrixStack modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.multiplyPositionMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
        RenderSystem.applyModelViewMatrix();

        if (lit) {
            RenderSystem.setShader(() -> LWShaders.decal);
            float strength = Math.max(0.16f, env.lightStrength);
            LWShaders.set(LWShaders.decal, "LightDir", env.lightX, env.lightY, env.lightZ, strength);
        } else {
            RenderSystem.setShader(GameRenderer::getParticleProgram);
        }
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.polygonOffset(-1f, -6f);
        RenderSystem.enablePolygonOffset();
        lightmap.enable();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        lightmap.disable();
        RenderSystem.polygonOffset(0f, 0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        modelView.pop();
        RenderSystem.applyModelViewMatrix();
    }
}
