package dev.livingworld.client.env;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Estimates how enclosed the camera is by casting a small fan of rays into the upper hemisphere a few
 * times per second. The result drives shelter from wind and rain, audio muffling and the cave ambience.
 */
public final class ShelterProbe {
    /** Unit directions: straight up, a steep ring and a shallow ring. */
    private static final float[] DIRS;
    private static final int STEEP_END = 5;

    static {
        float[] d = new float[13 * 3];
        int n = 0;
        d[n++] = 0f;
        d[n++] = 1f;
        d[n++] = 0f;
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 0.5 * i + 0.4;
            double e = Math.toRadians(58);
            d[n++] = (float) (Math.cos(a) * Math.cos(e));
            d[n++] = (float) Math.sin(e);
            d[n++] = (float) (Math.sin(a) * Math.cos(e));
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 0.25 * i;
            double e = Math.toRadians(20);
            d[n++] = (float) (Math.cos(a) * Math.cos(e));
            d[n++] = (float) Math.sin(e);
            d[n++] = (float) (Math.sin(a) * Math.cos(e));
        }
        DIRS = d;
    }

    /** Fraction of the surroundings open to the sky, 0..1 (foliage counts as half open). */
    public float openness = 1f;
    /** Fraction of the overhead rays stopped by something solid. */
    public float roof;
    /** Fraction of the overhead rays stopped by leaves. */
    public float canopy;

    public void probe(ClientWorld world, Entity viewer, Vec3d eye) {
        float open = 0f;
        float roofHits = 0f;
        float canopyHits = 0f;
        int count = DIRS.length / 3;
        for (int i = 0; i < count; i++) {
            boolean steep = i < STEEP_END;
            double reach = steep ? 24.0 : 14.0;
            Vec3d end = eye.add(DIRS[i * 3] * reach, DIRS[i * 3 + 1] * reach, DIRS[i * 3 + 2] * reach);
            BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, viewer));
            if (hit.getType() == HitResult.Type.MISS) {
                open += 1f;
            } else if (world.getBlockState(hit.getBlockPos()).isIn(BlockTags.LEAVES)) {
                open += 0.5f;
                if (steep) {
                    canopyHits += 1f;
                }
            } else if (steep) {
                roofHits += 1f;
            }
        }
        openness = open / count;
        roof = roofHits / STEEP_END;
        canopy = canopyHits / STEEP_END;
    }
}
