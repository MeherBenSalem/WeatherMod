package dev.livingworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.footprint.DecalStore;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws every live decal in one batch as terrain-hugging quads.
 *
 * <p>The prints are pixel art at the block textures' own texel density, snapped to the texel grid. This version
 * of the game has no place for the mod's lit decal program, so prints are always drawn from the pre-shaded sheet
 * with the stock translucent entity render type (the lit path of other versions is not available).
 */
public final class DecalRenderer {
    private static final Identifier SHADED = LivingWorld.id("textures/fx/decals.png");
    /** The sheets are a 4x4 grid of 16-texel cells; one cell covers exactly one block. */
    private static final float CELL = 0.25f;
    private static final FxBatch BATCH = new FxBatch(SHADED);

    /** Decals actually submitted last frame (debug/benchmark). */
    public static int lastDrawn;

    private DecalRenderer() {
    }

    public static void render(PoseStack poseStack, SubmitNodeCollector collector, Camera camera, DecalStore store, ClientEnvironment env) {
        lastDrawn = 0;
        int n = store.count;
        if (n == 0) {
            return;
        }
        Vec3 cam = camera.position();
        Vector3f forward = new Vector3f(0f, 0f, -1f).rotate(camera.rotation());
        float maxDist = QualityManager.effectDistance() * 1.5f;
        float maxDistSq = maxDist * maxDist;
        float fadeStartSq = maxDistSq * 0.55f;

        BATCH.begin();
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
            float keep = 1f - store.wear[i];
            alpha *= keep * (store.stain[i] ? 0.6f : (float) Math.sqrt(keep));
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
            if (store.stain[i]) {
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
                BATCH.vertex(sx + ox, y, sz + oz, u, v, r, g, b, alpha, light);
            }
            drawn++;
        }
        lastDrawn = drawn;
        BATCH.submit(poseStack, collector);
    }
}
