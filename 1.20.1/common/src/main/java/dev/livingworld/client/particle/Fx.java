package dev.livingworld.client.particle;

import java.util.concurrent.ThreadLocalRandom;

import static dev.livingworld.client.particle.ParticleEngine.ANIMATE;
import static dev.livingworld.client.particle.ParticleEngine.COLLIDE;
import static dev.livingworld.client.particle.ParticleEngine.EMISSIVE;
import static dev.livingworld.client.particle.ParticleEngine.FLAT;
import static dev.livingworld.client.particle.ParticleEngine.FLUTTER;
import static dev.livingworld.client.particle.ParticleEngine.NO_FADE_IN;
import static dev.livingworld.client.particle.ParticleEngine.REST;
import static dev.livingworld.client.particle.ParticleEngine.SPLASH;
import static dev.livingworld.client.particle.ParticleEngine.STRETCH;
import static dev.livingworld.client.particle.ParticleEngine.TWINKLE;

/**
 * The effect library: one small recipe per kind of particle, each mapping to sprites on the sheet
 * (see the layout in {@code AssetGen}). Spawners compose these; nothing else touches raw particle rows.
 */
public final class Fx {
    // Sprite indices on the 8x8 sheet.
    public static final int DUST = 0, SAND_GRAINS = 4, SNOWFLAKE = 8, SNOW_PUFF = 12, LEAF = 16, SPLASH_ANIM = 20,
            RIPPLE_ANIM = 24, MUD = 28, BREATH_ANIM = 32, EMBER = 36, ASH = 38, POLLEN = 40, SPORE = 41, MOTE = 42,
            FIREFLY = 43, RAIN_STREAK = 44, DRIZZLE_STREAK = 45, SNOW_STREAK = 46, SAND_STREAK = 47, MIST = 48,
            WISP = 50, ICE_SPARKLE = 52, DRIP = 54, DROPLET = 55, GRASS_BITS = 56, SEED_FLUFF = 58, END_MOTE = 59,
            SMOKE = 60, BUBBLE = 62, SPRAY = 63;

    private static final ParticleEngine P = ParticleEngine.INSTANCE;

    private Fx() {
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    private static float rf() {
        return rnd().nextFloat();
    }

    /** Symmetric random in [-1, 1]. */
    private static float rs() {
        return rnd().nextFloat() * 2f - 1f;
    }

    private static void tint(int i, float r, float g, float b, float a) {
        P.r[i] = r;
        P.g[i] = g;
        P.b[i] = b;
        P.a[i] = a;
    }

    /** Soft, slowly expanding cloud of dust. */
    public static void dustPuff(double x, double y, double z, float vx, float vy, float vz, float r, float g, float b,
                                float size, float alpha, float life) {
        int i = P.spawn(x, y, z, vx, vy, vz, DUST + rnd().nextInt(4), life * (0.8f + 0.4f * rf()), size);
        if (i < 0) {
            return;
        }
        tint(i, r, g, b, alpha);
        P.sizeEnd[i] = size * 2.3f;
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.02f;
        P.drag[i] = 0.93f;
        P.windK[i] = 0.06f;
        P.gravity[i] = -0.0008f;
    }

    /** Crisp grains thrown up and falling back (sand, gravel, dry dirt). */
    public static void grains(double x, double y, double z, float vx, float vy, float vz, float r, float g, float b, float size) {
        int i = P.spawn(x, y, z, vx, vy, vz, SAND_GRAINS + rnd().nextInt(4), 14f + rf() * 14f, size);
        if (i < 0) {
            return;
        }
        float shade = 0.85f + 0.25f * rf();
        tint(i, r * shade, g * shade, b * shade, 0.95f);
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.25f;
        P.gravity[i] = 0.035f;
        P.drag[i] = 0.97f;
        P.windK[i] = 0.04f;
        P.flags[i] = COLLIDE | NO_FADE_IN;
    }

    /** Powder snow thrown into the air: bright, grainy, lingers and drifts. */
    public static void snowPuff(double x, double y, double z, float vx, float vy, float vz, float size, float alpha) {
        int i = P.spawn(x, y, z, vx, vy, vz, SNOW_PUFF + rnd().nextInt(4), 16f + rf() * 16f, size);
        if (i < 0) {
            return;
        }
        tint(i, 0.96f, 0.98f, 1f, alpha);
        P.sizeEnd[i] = size * 1.9f;
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.04f;
        P.gravity[i] = 0.004f;
        P.drag[i] = 0.92f;
        P.windK[i] = 0.09f;
    }

    /** A single flake knocked loose. */
    public static void snowFleck(double x, double y, double z, float vx, float vy, float vz) {
        int i = P.spawn(x, y, z, vx, vy, vz, SNOWFLAKE + rnd().nextInt(4), 20f + rf() * 20f, 0.035f + rf() * 0.03f);
        if (i < 0) {
            return;
        }
        P.gravity[i] = 0.012f;
        P.drag[i] = 0.95f;
        P.windK[i] = 0.1f;
        P.turbulence[i] = 0.004f;
        P.flags[i] = COLLIDE | NO_FADE_IN;
    }

    /** Wet clod of mud. */
    public static void mud(double x, double y, double z, float vx, float vy, float vz, float r, float g, float b) {
        int i = P.spawn(x, y, z, vx, vy, vz, MUD + rnd().nextInt(4), 16f + rf() * 10f, 0.045f + rf() * 0.04f);
        if (i < 0) {
            return;
        }
        tint(i, r, g, b, 1f);
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.3f;
        P.gravity[i] = 0.045f;
        P.drag[i] = 0.98f;
        P.flags[i] = COLLIDE | NO_FADE_IN;
    }

    /** Animated crown splash where a drop hits something hard. */
    public static void splash(double x, double y, double z, float size, float alpha) {
        // Frames 22..23: a burst of droplets thinning out (the crown frames read too graphic at this size).
        int i = P.spawn(x, y + size * 0.6, z, 0f, 0f, 0f, SPLASH_ANIM + 2, 4f + rf() * 2f, size * 0.8f);
        if (i < 0) {
            return;
        }
        tint(i, 0.86f, 0.92f, 1f, alpha);
        P.sizeEnd[i] = size * 1.5f;
        P.frames[i] = 2;
        P.flags[i] = ANIMATE | NO_FADE_IN;
        P.drag[i] = 1f;
    }

    /** The smallest splash: a few flecks. Used when particles themselves land. */
    public static void tinySplash(double x, double y, double z, float sourceSize) {
        int i = P.spawn(x, y + 0.03, z, 0f, 0.004f, 0f, SPLASH_ANIM + 2, 4f, 0.05f + sourceSize);
        if (i < 0) {
            return;
        }
        tint(i, 0.86f, 0.92f, 1f, 0.55f);
        P.frames[i] = 2;
        P.flags[i] = ANIMATE | NO_FADE_IN;
    }

    /** Expanding ring lying on a water (or puddle) surface. */
    public static void ripple(double x, double y, double z, float size, float alpha) {
        int i = P.spawn(x, y, z, 0f, 0f, 0f, RIPPLE_ANIM, 11f + rf() * 5f, size * 0.45f);
        if (i < 0) {
            return;
        }
        tint(i, 0.9f, 0.95f, 1f, alpha);
        P.sizeEnd[i] = size * 1.5f;
        P.frames[i] = 4;
        P.rot[i] = rf() * 6.28f;
        P.drag[i] = 1f;
        P.flags[i] = ANIMATE | FLAT | NO_FADE_IN;
    }

    /** A free-falling water drop that splashes where it lands. */
    public static void droplet(double x, double y, double z, float vx, float vy, float vz, float size) {
        int i = P.spawn(x, y, z, vx, vy, vz, DROPLET, 40f, size);
        if (i < 0) {
            return;
        }
        tint(i, 0.85f, 0.92f, 1f, 0.8f);
        P.gravity[i] = 0.04f;
        P.drag[i] = 0.99f;
        P.flags[i] = COLLIDE | SPLASH | NO_FADE_IN;
    }

    /** A slow teardrop hanging then falling (leaves after rain, cave ceilings, soaked clothes). */
    public static void drip(double x, double y, double z) {
        int i = P.spawn(x, y, z, 0f, -0.02f, 0f, DRIP, 60f, 0.05f);
        if (i < 0) {
            return;
        }
        tint(i, 0.85f, 0.92f, 1f, 0.85f);
        P.gravity[i] = 0.03f;
        P.drag[i] = 0.99f;
        P.flags[i] = COLLIDE | SPLASH;
    }

    /** A falling leaf, tinted with the tree's foliage colour. */
    public static void leaf(double x, double y, double z, float vx, float vz, float r, float g, float b) {
        int i = P.spawn(x, y, z, vx, -0.01f, vz, LEAF + rnd().nextInt(4), 150f + rf() * 90f, 0.07f + rf() * 0.045f);
        if (i < 0) {
            return;
        }
        float shade = 0.8f + 0.3f * rf();
        tint(i, r * shade, g * shade, b * shade, 1f);
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.12f;
        P.gravity[i] = 0.0022f;
        P.drag[i] = 0.96f;
        P.windK[i] = 0.035f;
        P.flags[i] = COLLIDE | FLUTTER | REST;
    }

    /** Exhaled breath condensing in cold air. */
    public static void breath(double x, double y, double z, float vx, float vy, float vz, float alpha) {
        int i = P.spawn(x, y, z, vx, vy, vz, BREATH_ANIM, 22f + rf() * 10f, 0.07f);
        if (i < 0) {
            return;
        }
        tint(i, 0.94f, 0.97f, 1f, alpha);
        P.sizeEnd[i] = 0.3f + rf() * 0.12f;
        P.frames[i] = 4;
        P.rot[i] = rf() * 6.28f;
        P.drag[i] = 0.9f;
        P.windK[i] = 0.05f;
        P.gravity[i] = -0.0012f;
        P.flags[i] = ANIMATE | NO_FADE_IN;
    }

    /** Glowing ember carried upward by hot air. */
    public static void ember(double x, double y, double z, float vx, float vy, float vz) {
        int i = P.spawn(x, y, z, vx, vy, vz, EMBER + rnd().nextInt(2), 40f + rf() * 60f, 0.03f + rf() * 0.035f);
        if (i < 0) {
            return;
        }
        P.gravity[i] = -0.0015f;
        P.drag[i] = 0.97f;
        P.turbulence[i] = 0.006f;
        P.windK[i] = 0.02f;
        P.flags[i] = EMISSIVE | TWINKLE;
    }

    public static void ash(double x, double y, double z, float vx, float vy, float vz, float brightness) {
        int i = P.spawn(x, y, z, vx, vy, vz, ASH + rnd().nextInt(2), 120f + rf() * 100f, 0.035f + rf() * 0.03f);
        if (i < 0) {
            return;
        }
        tint(i, brightness, brightness, brightness, 0.85f);
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.05f;
        P.gravity[i] = 0.0012f;
        P.drag[i] = 0.95f;
        P.turbulence[i] = 0.003f;
        P.windK[i] = 0.03f;
        P.flags[i] = FLUTTER;
    }

    /** Tiny airborne speck: pollen, spores, dust in a sunbeam. */
    public static void mote(double x, double y, double z, int spriteIndex, float r, float g, float b, float alpha, float size, float life) {
        int i = P.spawn(x, y, z, rs() * 0.006f, rs() * 0.004f, rs() * 0.006f, spriteIndex, life * (0.7f + 0.6f * rf()), size);
        if (i < 0) {
            return;
        }
        tint(i, r, g, b, alpha);
        P.drag[i] = 0.96f;
        P.windK[i] = 0.02f;
        P.turbulence[i] = 0.0016f;
    }

    public static void firefly(double x, double y, double z) {
        int i = P.spawn(x, y, z, rs() * 0.02f, rs() * 0.01f, rs() * 0.02f, FIREFLY, 90f + rf() * 120f, 0.06f + rf() * 0.03f);
        if (i < 0) {
            return;
        }
        P.drag[i] = 0.94f;
        P.turbulence[i] = 0.006f;
        P.flags[i] = EMISSIVE | TWINKLE;
    }

    /** Large, faint volume of mist. */
    public static void mist(double x, double y, double z, float vx, float vz, float r, float g, float b, float alpha, float size) {
        int i = P.spawn(x, y, z, vx, 0f, vz, MIST + rnd().nextInt(2), 120f + rf() * 80f, size);
        if (i < 0) {
            return;
        }
        tint(i, r, g, b, alpha);
        P.sizeEnd[i] = size * 1.4f;
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.003f;
        P.drag[i] = 0.99f;
        P.windK[i] = 0.02f;
    }

    /** Streak of wind-blown material skimming along (blowing sand, spindrift). */
    public static void streak(double x, double y, double z, float vx, float vy, float vz, int spriteIndex, float r, float g, float b,
                              float alpha, float width, float life) {
        int i = P.spawn(x, y, z, vx, vy, vz, spriteIndex, life, width);
        if (i < 0) {
            return;
        }
        tint(i, r, g, b, alpha);
        P.drag[i] = 0.995f;
        P.windK[i] = 0.25f;
        P.turbulence[i] = 0.01f;
        P.flags[i] = STRETCH;
    }

    public static void iceSparkle(double x, double y, double z) {
        int i = P.spawn(x, y, z, rs() * 0.004f, -0.004f, rs() * 0.004f, ICE_SPARKLE + rnd().nextInt(2), 50f + rf() * 60f, 0.03f + rf() * 0.03f);
        if (i < 0) {
            return;
        }
        tint(i, 0.9f, 0.96f, 1f, 0.9f);
        P.rot[i] = rf() * 6.28f;
        P.drag[i] = 0.97f;
        P.windK[i] = 0.03f;
        P.flags[i] = TWINKLE;
    }

    public static void grassBits(double x, double y, double z, float vx, float vy, float vz, float r, float g, float b) {
        int i = P.spawn(x, y, z, vx, vy, vz, GRASS_BITS + rnd().nextInt(2), 18f + rf() * 12f, 0.06f + rf() * 0.03f);
        if (i < 0) {
            return;
        }
        tint(i, r, g, b, 1f);
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.3f;
        P.gravity[i] = 0.02f;
        P.drag[i] = 0.95f;
        P.windK[i] = 0.05f;
        P.flags[i] = COLLIDE | NO_FADE_IN;
    }

    public static void seedFluff(double x, double y, double z) {
        int i = P.spawn(x, y, z, 0f, 0.004f, 0f, SEED_FLUFF, 200f + rf() * 120f, 0.05f + rf() * 0.025f);
        if (i < 0) {
            return;
        }
        tint(i, 1f, 1f, 0.97f, 0.9f);
        P.rot[i] = rs() * 0.4f;
        P.gravity[i] = 0.0005f;
        P.drag[i] = 0.97f;
        P.windK[i] = 0.06f;
        P.turbulence[i] = 0.002f;
    }

    public static void endMote(double x, double y, double z, float vx, float vy, float vz) {
        int i = P.spawn(x, y, z, vx, vy, vz, END_MOTE, 120f + rf() * 140f, 0.035f + rf() * 0.04f);
        if (i < 0) {
            return;
        }
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.02f;
        P.drag[i] = 0.995f;
        P.turbulence[i] = 0.0025f;
        P.flags[i] = EMISSIVE | TWINKLE;
    }

    public static void smoke(double x, double y, double z, float vx, float vy, float vz, float size, float alpha) {
        int i = P.spawn(x, y, z, vx, vy, vz, SMOKE + rnd().nextInt(2), 60f + rf() * 50f, size);
        if (i < 0) {
            return;
        }
        tint(i, 0.75f, 0.75f, 0.78f, alpha);
        P.sizeEnd[i] = size * 2.6f;
        P.rot[i] = rf() * 6.28f;
        P.rotVel[i] = rs() * 0.01f;
        P.gravity[i] = -0.001f;
        P.drag[i] = 0.96f;
        P.windK[i] = 0.03f;
    }

    /** Fine spray thrown up by waves and waterfalls. */
    public static void spray(double x, double y, double z, float vx, float vy, float vz, float size, float alpha) {
        int i = P.spawn(x, y, z, vx, vy, vz, SPRAY, 18f + rf() * 14f, size);
        if (i < 0) {
            return;
        }
        tint(i, 0.92f, 0.96f, 1f, alpha);
        P.sizeEnd[i] = size * 1.8f;
        P.rot[i] = rf() * 6.28f;
        P.gravity[i] = 0.01f;
        P.drag[i] = 0.94f;
        P.windK[i] = 0.08f;
    }
}
