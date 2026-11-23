package dev.livingworld.client.particle;

import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.config.QualityPreset;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.render.FxBatch;
import dev.livingworld.climate.WindField;
import dev.livingworld.util.Noise;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A pooled, structure-of-arrays particle system for all of the mod's environmental particles.
 *
 * <p>Why not vanilla particles: these need a hard global budget, wind coupling, material-aware landing
 * and one batched draw. Here every particle is a row in a set of primitive arrays (no per-particle
 * objects, no allocation while running), removal is a swap with the last row, and the whole system is
 * drawn with a single buffer upload from one sprite sheet.
 */
public final class ParticleEngine {
    public static final ParticleEngine INSTANCE = new ParticleEngine();
    private static final int CAP = QualityPreset.MAX_PARTICLES;

    /** Test the block the particle moves into; land on solids, react to water. */
    public static final int COLLIDE = 1;
    /** Draw as a streak aligned with the velocity instead of a camera-facing sprite. */
    public static final int STRETCH = 1 << 1;
    /** Draw flat on the ground (ripples). */
    public static final int FLAT = 1 << 2;
    /** Ignore world light. */
    public static final int EMISSIVE = 1 << 3;
    /** Leaf-like side-to-side flutter while falling. */
    public static final int FLUTTER = 1 << 4;
    /** Stay on the ground after landing instead of disappearing. */
    public static final int REST = 1 << 5;
    /** Step through {@code frames} consecutive sprites over the lifetime. */
    public static final int ANIMATE = 1 << 6;
    /** Leave a ripple when hitting water / a small splash when hitting ground. */
    public static final int SPLASH = 1 << 7;
    /** Start at full opacity (no fade-in). */
    public static final int NO_FADE_IN = 1 << 8;
    /** Pulse the alpha (fireflies, ice glints). */
    public static final int TWINKLE = 1 << 9;
    private static final int RESTING = 1 << 30;

    private static final float SHEET = 8f;
    private static final float INSET = 0.5f / 256f;

    public int count;
    public final double[] x = new double[CAP];
    public final double[] y = new double[CAP];
    public final double[] z = new double[CAP];
    public final float[] vx = new float[CAP];
    public final float[] vy = new float[CAP];
    public final float[] vz = new float[CAP];
    public final float[] age = new float[CAP];
    public final float[] life = new float[CAP];
    public final float[] size = new float[CAP];
    /** Size at end of life; equal to {@link #size} for no growth. */
    public final float[] sizeEnd = new float[CAP];
    public final float[] r = new float[CAP];
    public final float[] g = new float[CAP];
    public final float[] b = new float[CAP];
    public final float[] a = new float[CAP];
    public final float[] rot = new float[CAP];
    public final float[] rotVel = new float[CAP];
    /** Downward acceleration per tick. */
    public final float[] gravity = new float[CAP];
    /** Velocity retained per tick. */
    public final float[] drag = new float[CAP];
    /** How quickly the particle is swept up to wind speed (0 = ignores wind, 1 = instantly). */
    public final float[] windK = new float[CAP];
    /** Random jitter acceleration. */
    public final float[] turbulence = new float[CAP];
    public final int[] light = new int[CAP];
    public final int[] flags = new int[CAP];
    public final short[] sprite = new short[CAP];
    public final byte[] frames = new byte[CAP];

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final Vector3f left = new Vector3f();
    private final Vector3f up = new Vector3f();
    private long tickCounter;
    /** Particles refused because the budget was full, since the last debug read. */
    public int dropped;

    private ParticleEngine() {
    }

    /**
     * Adds a particle with neutral defaults and returns its row, or -1 if the budget is exhausted.
     * Callers customise the row through the public arrays.
     */
    public int spawn(double px, double py, double pz, float velX, float velY, float velZ, int spriteIndex,
                     float lifeTicks, float sizeBlocks) {
        int i = count;
        if (i >= QualityManager.maxParticles()) {
            dropped++;
            return -1;
        }
        count = i + 1;
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        vx[i] = velX;
        vy[i] = velY;
        vz[i] = velZ;
        age[i] = 0f;
        life[i] = lifeTicks;
        size[i] = sizeBlocks;
        sizeEnd[i] = sizeBlocks;
        r[i] = 1f;
        g[i] = 1f;
        b[i] = 1f;
        a[i] = 1f;
        rot[i] = 0f;
        rotVel[i] = 0f;
        gravity[i] = 0f;
        drag[i] = 0.98f;
        windK[i] = 0f;
        turbulence[i] = 0f;
        light[i] = 0xF000F0;
        flags[i] = 0;
        sprite[i] = (short) spriteIndex;
        frames[i] = 1;
        return i;
    }

    private void remove(int i) {
        int last = --count;
        if (i != last) {
            x[i] = x[last];
            y[i] = y[last];
            z[i] = z[last];
            vx[i] = vx[last];
            vy[i] = vy[last];
            vz[i] = vz[last];
            age[i] = age[last];
            life[i] = life[last];
            size[i] = size[last];
            sizeEnd[i] = sizeEnd[last];
            r[i] = r[last];
            g[i] = g[last];
            b[i] = b[last];
            a[i] = a[last];
            rot[i] = rot[last];
            rotVel[i] = rotVel[last];
            gravity[i] = gravity[last];
            drag[i] = drag[last];
            windK[i] = windK[last];
            turbulence[i] = turbulence[last];
            light[i] = light[last];
            flags[i] = flags[last];
            sprite[i] = sprite[last];
            frames[i] = frames[last];
        }
    }

    public void clear() {
        count = 0;
    }

    public void tick(ClientLevel world, ClientEnvironment env) {
        tickCounter++;
        float windX = env.wind.dirX * env.windLocal * WindField.BLOCKS_PER_TICK;
        float windZ = env.wind.dirZ * env.windLocal * WindField.BLOCKS_PER_TICK;
        int phase = (int) (tickCounter & 15);
        int jitterSeed = (int) (tickCounter >> 1);
        double maxDistSq = 72.0 * 72.0;

        for (int i = count - 1; i >= 0; i--) {
            float ag = age[i] + 1f;
            age[i] = ag;
            if (ag >= life[i]) {
                remove(i);
                continue;
            }
            int f = flags[i];
            if ((f & RESTING) != 0) {
                continue;
            }
            float k = windK[i];
            if (k > 0f) {
                vx[i] += (windX - vx[i]) * k;
                vz[i] += (windZ - vz[i]) * k;
            }
            float tb = turbulence[i];
            if (tb > 0f) {
                int h = Noise.hash(i * 7919 + jitterSeed);
                vx[i] += ((h & 255) / 127.5f - 1f) * tb;
                vy[i] += (((h >> 8) & 255) / 127.5f - 1f) * tb * 0.5f;
                vz[i] += (((h >> 16) & 255) / 127.5f - 1f) * tb;
            }
            if ((f & FLUTTER) != 0) {
                float ph = ag * 0.19f + i * 1.7f;
                vx[i] += Mth.sin(ph) * 0.0045f;
                vz[i] += Mth.cos(ph * 0.83f) * 0.0045f;
                vy[i] += Mth.sin(ph * 2f) * 0.0015f;
            }
            vy[i] -= gravity[i];
            float d = drag[i];
            vx[i] *= d;
            vy[i] *= d;
            vz[i] *= d;
            double nx = x[i] + vx[i];
            double ny = y[i] + vy[i];
            double nz = z[i] + vz[i];

            if ((f & COLLIDE) != 0) {
                pos.set(Mth.floor(nx), Mth.floor(ny), Mth.floor(nz));
                BlockState state = world.getBlockState(pos);
                if (!state.isAir()) {
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        float surface = pos.getY() + fluid.getHeight(world, pos);
                        if (ny < surface) {
                            if ((f & SPLASH) != 0) {
                                Fx.ripple(nx, surface + 0.01, nz, 0.12f + size[i] * 2.5f, 0.55f);
                            }
                            remove(i);
                            continue;
                        }
                    } else {
                        VoxelShape shape = state.getCollisionShape(world, pos);
                        if (!shape.isEmpty()) {
                            double surface = pos.getY() + shape.max(Direction.Axis.Y);
                            if (ny < surface) {
                                if ((f & REST) != 0 && vy[i] <= 0f) {
                                    x[i] = nx;
                                    y[i] = surface + 0.012;
                                    z[i] = nz;
                                    vx[i] = vy[i] = vz[i] = 0f;
                                    rotVel[i] = 0f;
                                    flags[i] = (f | RESTING | FLAT) & ~FLUTTER;
                                    continue;
                                }
                                if ((f & SPLASH) != 0) {
                                    Fx.tinySplash(nx, surface + 0.02, nz, size[i]);
                                }
                                remove(i);
                                continue;
                            }
                        }
                    }
                }
            }
            x[i] = nx;
            y[i] = ny;
            z[i] = nz;
            rot[i] += rotVel[i];

            if ((i & 15) == phase) {
                double dx = nx - env.camX;
                double dz = nz - env.camZ;
                if (dx * dx + dz * dz > maxDistSq) {
                    remove(i);
                    continue;
                }
                if ((f & EMISSIVE) == 0) {
                    pos.set(Mth.floor(nx), Mth.floor(ny), Mth.floor(nz));
                    light[i] = LevelRenderer.getLightColor(world, pos);
                }
            }
        }
    }

    /** Appends every live particle to the batch. Positions are emitted relative to the camera. */
    public void render(FxBatch batch, Camera camera, float tickDelta) {
        int n = count;
        if (n == 0) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Quaternionf rotation = camera.rotation();
        left.set(1f, 0f, 0f).rotate(rotation);
        up.set(0f, 1f, 0f).rotate(rotation);
        float lx = left.x, ly = left.y, lz = left.z;
        float ux = up.x, uy = up.y, uz = up.z;
        float back = 1f - tickDelta;
        float time = tickCounter + tickDelta;
        boolean hazy = batch.haze(30f) > 0.02f;

        for (int i = 0; i < n; i++) {
            int f = flags[i];
            boolean resting = (f & RESTING) != 0;
            float t = age[i] / life[i];
            float alpha = a[i];
            if ((f & NO_FADE_IN) == 0 && t < 0.12f) {
                alpha *= t / 0.12f;
            }
            if (t > 0.7f) {
                alpha *= (1f - t) / 0.3f;
            }
            if ((f & TWINKLE) != 0) {
                float s = Mth.sin(time * 0.35f + i * 2.4f);
                alpha *= 0.25f + 0.75f * s * s;
            }
            if (alpha <= 0.004f) {
                continue;
            }
            float px = (float) (x[i] - cam.x) - (resting ? 0f : vx[i] * back);
            float py = (float) (y[i] - cam.y) - (resting ? 0f : vy[i] * back);
            float pz = (float) (z[i] - cam.z) - (resting ? 0f : vz[i] * back);
            float distSq = px * px + py * py + pz * pz;
            float s = size[i] + (sizeEnd[i] - size[i]) * t;
            // Large soft sprites dissolve as the camera moves through them instead of popping.
            if (s > 0.3f && distSq < s * s * 2.5f) {
                alpha *= distSq / (s * s * 2.5f);
            }

            int frame = sprite[i];
            if ((f & ANIMATE) != 0) {
                frame += Math.min(frames[i] - 1, (int) (t * frames[i]));
            }
            float u0 = (frame & 7) / SHEET + INSET;
            float v0 = (frame >> 3) / SHEET + INSET;
            float u1 = u0 + 1f / SHEET - 2f * INSET;
            float v1 = v0 + 1f / SHEET - 2f * INSET;
            int l = light[i];
            float cr = r[i], cg = g[i], cb = b[i];
            if (hazy && distSq > 9f) {
                float fog = batch.haze((float) Math.sqrt(distSq));
                cr += (batch.hazeR - cr) * fog;
                cg += (batch.hazeG - cg) * fog;
                cb += (batch.hazeB - cb) * fog;
                alpha *= 1f - 0.5f * fog;
            }

            if ((f & FLAT) != 0) {
                float c = Mth.cos(rot[i]) * s;
                float sn = Mth.sin(rot[i]) * s;
                batch.vertex(px - c + sn, py, pz - sn - c, u0, v0, cr, cg, cb, alpha, l);
                batch.vertex(px - c - sn, py, pz - sn + c, u0, v1, cr, cg, cb, alpha, l);
                batch.vertex(px + c - sn, py, pz + sn + c, u1, v1, cr, cg, cb, alpha, l);
                batch.vertex(px + c + sn, py, pz + sn - c, u1, v0, cr, cg, cb, alpha, l);
            } else if ((f & STRETCH) != 0) {
                float velX = vx[i], velY = vy[i], velZ = vz[i];
                float speed = (float) Math.sqrt(velX * velX + velY * velY + velZ * velZ);
                if (speed < 1.0e-4f) {
                    continue;
                }
                float lenScale = Math.max(s, speed * 1.6f) / speed;
                float ax = velX * lenScale, ay = velY * lenScale, az = velZ * lenScale;
                // Side vector perpendicular to both the streak and the view direction.
                float sx = ay * pz - az * py;
                float sy = az * px - ax * pz;
                float sz = ax * py - ay * px;
                float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1.0e-5f) {
                    continue;
                }
                float w = s * 0.5f / sl;
                sx *= w;
                sy *= w;
                sz *= w;
                // Streak sprites only occupy the middle of their cell.
                float uMid = (u0 + u1) * 0.5f;
                u0 = uMid - 7f / 256f;
                u1 = uMid + 7f / 256f;
                batch.vertex(px - sx - ax, py - sy - ay, pz - sz - az, u0, v0, cr, cg, cb, alpha, l);
                batch.vertex(px - sx, py - sy, pz - sz, u0, v1, cr, cg, cb, alpha, l);
                batch.vertex(px + sx, py + sy, pz + sz, u1, v1, cr, cg, cb, alpha, l);
                batch.vertex(px + sx - ax, py + sy - ay, pz + sz - az, u1, v0, cr, cg, cb, alpha, l);
            } else {
                float c = Mth.cos(rot[i]) * s;
                float sn = Mth.sin(rot[i]) * s;
                // Corner offsets (±1, ±1) rotated by the particle's own roll.
                float ax = -c + sn, ay = -sn - c;
                float bx = -c - sn, by = -sn + c;
                batch.vertex(px + lx * ax + ux * ay, py + ly * ax + uy * ay, pz + lz * ax + uz * ay, u1, v1, cr, cg, cb, alpha, l);
                batch.vertex(px + lx * bx + ux * by, py + ly * bx + uy * by, pz + lz * bx + uz * by, u1, v0, cr, cg, cb, alpha, l);
                batch.vertex(px - lx * ax - ux * ay, py - ly * ax - uy * ay, pz - lz * ax - uz * ay, u0, v0, cr, cg, cb, alpha, l);
                batch.vertex(px - lx * bx - ux * by, py - ly * bx - uy * by, pz - lz * bx - uz * by, u0, v1, cr, cg, cb, alpha, l);
            }
        }
    }
}
