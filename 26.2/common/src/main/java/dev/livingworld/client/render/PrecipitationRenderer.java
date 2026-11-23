package dev.livingworld.client.render;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.env.ColumnCache;
import dev.livingworld.client.particle.Fx;
import dev.livingworld.util.LWMath;
import dev.livingworld.util.Noise;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Wind-driven rain, snow and blowing sand.
 *
 * <p>Instead of simulating thousands of drops, every drop is a stateless function of its index and two
 * accumulated offsets (how far the rain has fallen, how far the wind has carried it), wrapped into a box
 * that follows the camera. That makes the cost one hash and one quad per visible drop, gives real depth
 * and per-drop speed variation, and lets the whole field lean and surge with the wind. Drops are hidden
 * below the surface that would have stopped them (looked up in the {@link ColumnCache}), so roofs, caves
 * and overhangs keep the rain out and tree canopies let only a few drips through.
 */
public final class PrecipitationRenderer {
    public static final PrecipitationRenderer INSTANCE = new PrecipitationRenderer();

    private static final float SHEET = 8f;
    private static final float INSET = 0.5f / 256f;
    private static final float HEIGHT = 22f;
    /** Half-width (in atlas UV) of the central band of a streak sprite that actually contains the streak. */
    private static final float STREAK_BAND = 4.5f / 256f;
    private static final int SKY_LIGHT = 0xF00000;

    private double fallRain, fallSnow, windX, windZ, sandX, sandZ;
    private long lastNanos;
    private final Vector3f left = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final float[] dust = new float[3];
    /** Streaks and flakes submitted last frame (debug/benchmark). */
    public int lastDrops;

    private PrecipitationRenderer() {
    }

    public void render(FxBatch batch, Camera camera, ClientEnvironment env, boolean paused) {
        lastDrops = 0;
        ClientConfig config = ClientConfig.get();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        if (paused) {
            dt = 0f;
        }
        float rain = env.rain;
        float snow = env.snow;
        float sand = config.sandstorms ? env.sand : 0f;
        if (!config.customPrecipitation || env.submerged > 0.5f || rain + snow + sand < 0.004f) {
            return;
        }

        // ---- advance the shared offsets -------------------------------------------------------
        float wind = env.wind.speed * (0.25f + 0.75f * env.windExposure);
        float rainFall = rain < 0.25f ? 11f : 16f + 7f * Math.min(rain, 1.3f);
        float snowFall = 1.1f + 1.3f * Math.min(snow, 1.2f);
        fallRain += rainFall * dt;
        fallSnow += snowFall * dt;
        float wvx = env.wind.dirX * wind;
        float wvz = env.wind.dirZ * wind;
        windX += wvx * dt;
        windZ += wvz * dt;
        float sandSpeed = 6f + 12f * wind;
        sandX += env.wind.dirX * sandSpeed * dt;
        sandZ += env.wind.dirZ * sandSpeed * dt;

        int budget = QualityManager.maxDrops();
        float radius = Math.min(QualityManager.effectDistance(), 26f);
        float size = radius * 2f;
        int nRain = (int) (budget * Math.min(1f, (float) Math.pow(rain, 0.75)) * config.rainDensity);
        int nSnow = (int) (budget * 0.85f * Math.min(1f, (float) Math.pow(snow, 0.8)) * config.snowDensity);
        int nSand = (int) (budget * 0.7f * sand * config.sandDensity);
        int total = nRain + nSnow + nSand;
        int cap = (int) (budget * 1.3f);
        if (total > cap) {
            float k = (float) cap / total;
            nRain = (int) (nRain * k);
            nSnow = (int) (nSnow * k);
            nSand = (int) (nSand * k);
        }

        double camX = camera.position().x, camY = camera.position().y, camZ = camera.position().z;
        ColumnCache columns = env.columns;
        float flash = 1f + env.flash * 0.6f;
        float farStart = radius * 0.62f;
        int drawn = 0;

        // ---- rain ---------------------------------------------------------------------------
        if (nRain > 0) {
            boolean drizzle = rain < 0.25f;
            int cell = drizzle ? Fx.DRIZZLE_STREAK : Fx.RAIN_STREAK;
            // The streak art is a thin line down the middle of its cell: sample only that central band.
            float uMid = ((cell & 7) + 0.5f) / SHEET + (drizzle ? 0.5f / 256f : 0f);
            float u0 = uMid - STREAK_BAND, u1 = uMid + STREAK_BAND;
            float v0 = (cell >> 3) / SHEET + INSET, v1 = v0 + 1f / SHEET - 2f * INSET;
            float baseAlpha = (drizzle ? 0.5f : 0.52f + 0.2f * Math.min(rain, 1.2f));
            float baseLen = drizzle ? 0.35f : 0.55f + 0.55f * Math.min(rain, 1.2f);
            float windCarry = 8.5f;
            // Two shells: a dense one close to the camera (where streaks read individually) and a
            // sparser one out to the effect radius that gives the rain its depth.
            float nearRadius = Math.min(11f, radius);
            float nearSize = nearRadius * 2f;
            float nearHeight = 15f;
            for (int i = 0; i < nRain; i++) {
                int h = Noise.hash(i, 0x51A1);
                float h1 = (h & 0xFFFF) / 65536f;
                float h2 = (h >>> 16) / 65536f;
                int hb = Noise.hash(i, 0x7B3D);
                float h3 = (hb & 0xFFFF) / 65536f;
                float speed = 0.8f + 0.4f * ((hb >>> 16) / 65536f);

                boolean near = i % 5 < 2;
                float shell = near ? nearRadius : radius;
                float shellSize = near ? nearSize : size;
                float shellHeight = near ? nearHeight : HEIGHT;
                float x = wrap(h1 * shellSize + windX * windCarry * speed - camX, shellSize) - shell;
                float z = wrap(h3 * shellSize + windZ * windCarry * speed - camZ, shellSize) - shell;
                float y = wrap(h2 * shellHeight - fallRain * speed - camY, shellHeight) - shellHeight * 0.38f;

                float horizontal = x * x + z * z;
                if (horizontal > shell * shell) {
                    continue;
                }
                int wx = Mth.floor(camX + x);
                int wz = Mth.floor(camZ + z);
                float worldY = (float) camY + y;
                if (worldY < columns.top(wx, wz)) {
                    // Under cover. A few drips make it through leaves.
                    if ((hb & 15) != 0 || !columns.hasCanopy(wx, wz) || worldY < columns.ground(wx, wz)) {
                        continue;
                    }
                }
                float dist = (float) Math.sqrt(horizontal + y * y);
                float alpha = Math.min(1f, baseAlpha * LWMath.smoothstep(0.35f, 1.6f, dist)
                        * (1f - LWMath.smoothstep(shell * 0.62f, shell, (float) Math.sqrt(horizontal))));
                if (alpha < 0.02f) {
                    continue;
                }
                // Streak axis: where the drop came from.
                float vx = wvx * windCarry * speed, vy = -rainFall * speed, vz = wvz * windCarry * speed;
                float inv = baseLen * speed / (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                float ax = vx * inv, ay = vy * inv, az = vz * inv;
                float sx = ay * z - az * y, sy = az * x - ax * z, sz = ax * y - ay * x;
                float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1.0e-5f) {
                    continue;
                }
                // Distant streaks are drawn a little wider so they do not vanish below a pixel.
                float w = (0.03f + 0.014f * h2) * (1f + dist * 0.06f) / sl;
                sx *= w;
                sy *= w;
                sz *= w;
                float fog = batch.haze(dist);
                // Rain is lit by the sky: grey-blue by day, nearly invisible at night.
                float c = (0.3f + 0.42f * env.daylight) * flash;
                float cr = c + (batch.hazeR - c) * fog;
                float cg = c * 1.06f + (batch.hazeG - c * 1.06f) * fog;
                float cb = c * 1.18f + (batch.hazeB - c * 1.18f) * fog;
                alpha *= 1f - 0.5f * fog;
                batch.vertex(x - sx - ax, y - sy - ay, z - sz - az, u0, v0, cr, cg, cb, alpha, SKY_LIGHT);
                batch.vertex(x - sx, y - sy, z - sz, u0, v1, cr, cg, cb, alpha, SKY_LIGHT);
                batch.vertex(x + sx, y + sy, z + sz, u1, v1, cr, cg, cb, alpha, SKY_LIGHT);
                batch.vertex(x + sx - ax, y + sy - ay, z + sz - az, u1, v0, cr, cg, cb, alpha, SKY_LIGHT);
                drawn++;
            }
        }

        // ---- snow ---------------------------------------------------------------------------
        if (nSnow > 0) {
            Quaternionf rotation = camera.rotation();
            left.set(1f, 0f, 0f).rotate(rotation);
            up.set(0f, 1f, 0f).rotate(rotation);
            float windCarry = 13f;
            // In a blizzard the flakes smear into streaks along the wind.
            float streakiness = LWMath.smoothstep(0.45f, 0.95f, wind) * LWMath.smoothstep(0.5f, 1f, snow);
            for (int i = 0; i < nSnow; i++) {
                int h = Noise.hash(i, 0x2C91);
                float h1 = (h & 0xFFFF) / 65536f;
                float h2 = (h >>> 16) / 65536f;
                int hb = Noise.hash(i, 0x66E5);
                float h3 = (hb & 0xFFFF) / 65536f;
                float speed = 0.65f + 0.7f * ((hb >>> 16) / 65536f);

                float swirl = (float) (fallSnow * (0.5f + h2)) + h1 * 40f;
                float x = wrap(h1 * size + windX * windCarry * speed - camX, size) - radius + Mth.sin(swirl) * 0.35f;
                float z = wrap(h3 * size + windZ * windCarry * speed - camZ, size) - radius + Mth.cos(swirl * 0.8f) * 0.35f;
                float y = wrap(h2 * HEIGHT - fallSnow * speed - camY, HEIGHT) - HEIGHT * 0.38f;

                float horizontal = x * x + z * z;
                if (horizontal > radius * radius) {
                    continue;
                }
                int wx = Mth.floor(camX + x);
                int wz = Mth.floor(camZ + z);
                if ((float) camY + y < columns.top(wx, wz)) {
                    continue;
                }
                float dist = (float) Math.sqrt(horizontal + y * y);
                float alpha = 0.92f * LWMath.smoothstep(0.3f, 1.2f, dist)
                        * (1f - LWMath.smoothstep(farStart, radius, (float) Math.sqrt(horizontal)));
                if (alpha < 0.02f) {
                    continue;
                }
                float flake = (0.035f + 0.05f * h3) * (1f + dist * 0.03f);
                // Flakes deep in the storm take on its grey and thin out; near ones stay crisp and white.
                float fog = batch.haze(dist);
                float c = Math.min(1.2f, flash) * (1f - 0.35f * fog);
                alpha *= 1f - 0.55f * fog;
                if (streakiness > 0.25f && (i & 1) == 0) {
                    int cell = Fx.SNOW_STREAK;
                    float uMid = ((cell & 7) + 0.5f) / SHEET;
                    float u0 = uMid - STREAK_BAND * 1.4f, u1 = uMid + STREAK_BAND * 1.4f;
                    float v0 = (cell >> 3) / SHEET + INSET, v1 = v0 + 1f / SHEET - 2f * INSET;
                    float vx = wvx * windCarry * speed, vy = -snowFall * speed, vz = wvz * windCarry * speed;
                    float len = (0.25f + 0.55f * streakiness) / (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float ax = vx * len, ay = vy * len, az = vz * len;
                    float sx = ay * z - az * y, sy = az * x - ax * z, sz = ax * y - ay * x;
                    float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                    if (sl < 1.0e-5f) {
                        continue;
                    }
                    float w = flake * 0.9f / sl;
                    sx *= w;
                    sy *= w;
                    sz *= w;
                    batch.vertex(x - sx - ax, y - sy - ay, z - sz - az, u0, v0, c, c, c, alpha, SKY_LIGHT);
                    batch.vertex(x - sx, y - sy, z - sz, u0, v1, c, c, c, alpha, SKY_LIGHT);
                    batch.vertex(x + sx, y + sy, z + sz, u1, v1, c, c, c, alpha, SKY_LIGHT);
                    batch.vertex(x + sx - ax, y + sy - ay, z + sz - az, u1, v0, c, c, c, alpha, SKY_LIGHT);
                } else {
                    int cell = Fx.SNOWFLAKE + (i & 3);
                    float u0 = (cell & 7) / SHEET + INSET, v0 = (cell >> 3) / SHEET + INSET;
                    float u1 = u0 + 1f / SHEET - 2f * INSET, v1 = v0 + 1f / SHEET - 2f * INSET;
                    float lx = left.x * flake, ly = left.y * flake, lz = left.z * flake;
                    float ux = up.x * flake, uy = up.y * flake, uz = up.z * flake;
                    batch.vertex(x - lx - ux, y - ly - uy, z - lz - uz, u1, v1, c, c, c, alpha, SKY_LIGHT);
                    batch.vertex(x - lx + ux, y - ly + uy, z - lz + uz, u1, v0, c, c, c, alpha, SKY_LIGHT);
                    batch.vertex(x + lx + ux, y + ly + uy, z + lz + uz, u0, v0, c, c, c, alpha, SKY_LIGHT);
                    batch.vertex(x + lx - ux, y + ly - uy, z + lz - uz, u0, v1, c, c, c, alpha, SKY_LIGHT);
                }
                drawn++;
            }
        }

        // ---- blowing sand -------------------------------------------------------------------
        if (nSand > 0) {
            env.dustColor(dust);
            int cell = Fx.SAND_STREAK;
            float uMid = ((cell & 7) + 0.5f) / SHEET;
            float u0 = uMid - STREAK_BAND * 1.6f, u1 = uMid + STREAK_BAND * 1.6f;
            float v0 = (cell >> 3) / SHEET + INSET, v1 = v0 + 1f / SHEET - 2f * INSET;
            float lit = Math.max(0.25f, env.daylight);
            for (int i = 0; i < nSand; i++) {
                int h = Noise.hash(i, 0x3D17);
                float h1 = (h & 0xFFFF) / 65536f;
                float h2 = (h >>> 16) / 65536f;
                int hb = Noise.hash(i, 0x49F3);
                float h3 = (hb & 0xFFFF) / 65536f;
                float speed = 0.7f + 0.6f * ((hb >>> 16) / 65536f);

                float x = wrap(h1 * size + sandX * speed - camX, size) - radius;
                float z = wrap(h3 * size + sandZ * speed - camZ, size) - radius;
                float horizontal = x * x + z * z;
                if (horizontal > radius * radius) {
                    continue;
                }
                int wx = Mth.floor(camX + x);
                int wz = Mth.floor(camZ + z);
                float ground = columns.ground(wx, wz);
                if (ground <= ColumnCache.NO_SURFACE + 1f) {
                    continue;
                }
                // Dense near the ground, thinning quickly with height; each grain bobs a little.
                float worldY = ground + 0.08f + h2 * h2 * h2 * 8f + Mth.sin((float) (sandX * 0.3) + h1 * 30f) * 0.25f * h2;
                float y = worldY - (float) camY;
                if (y < -12f || y > 12f || worldY < columns.top(wx, wz) - 0.05f) {
                    continue;
                }
                float dist = (float) Math.sqrt(horizontal + y * y);
                float alpha = 0.42f * Math.min(1f, sand * 1.4f) * LWMath.smoothstep(0.3f, 1.5f, dist)
                        * (1f - LWMath.smoothstep(farStart, radius, (float) Math.sqrt(horizontal)));
                if (alpha < 0.02f) {
                    continue;
                }
                float len = (0.3f + 0.5f * h3) * (0.5f + wind);
                float ax = env.wind.dirX * len, az = env.wind.dirZ * len;
                float ay = -0.04f * len;
                float sx = ay * z - az * y, sy = az * x - ax * z, sz = ax * y - ay * x;
                float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1.0e-5f) {
                    continue;
                }
                float w = (0.03f + 0.03f * h2) / sl;
                sx *= w;
                sy *= w;
                sz *= w;
                // Grains vary a little around the colour of the dust-laden air, some catching the light.
                float shade = (0.74f + 0.36f * h1) * lit;
                float r = dust[0] * shade, g = dust[1] * shade, b = dust[2] * shade;
                alpha *= 1f - 0.6f * batch.haze(dist);
                batch.vertex(x - sx - ax, y - sy - ay, z - sz - az, u0, v0, r, g, b, alpha, SKY_LIGHT);
                batch.vertex(x - sx, y - sy, z - sz, u0, v1, r, g, b, alpha, SKY_LIGHT);
                batch.vertex(x + sx, y + sy, z + sz, u1, v1, r, g, b, alpha, SKY_LIGHT);
                batch.vertex(x + sx - ax, y + sy - ay, z + sz - az, u1, v0, r, g, b, alpha, SKY_LIGHT);
                drawn++;
            }
        }
        lastDrops = drawn;
    }

    private static float wrap(double value, float size) {
        double m = value % size;
        return (float) (m < 0 ? m + size : m);
    }
}
