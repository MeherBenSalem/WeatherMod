package dev.livingworld.client.footprint;

/**
 * Bounded storage and ageing for ground decals (footprints, impact marks, wet prints).
 *
 * <p>Decals are purely visual, client-side rows in primitive arrays — the world is never modified and
 * memory use is fixed. Each decal carries a {@code wear} value from 0 (fresh) to 1 (gone) that advances
 * with age and with the weather it is sensitive to, so wind sweeps sand prints away, snowfall fills snow
 * prints, and rain washes both out. When the budget is full the most worn decal is evicted, and
 * trampled spots are thinned so a herd pacing in one place cannot pile up unlimited overlapping quads.
 *
 * <p>No Minecraft types: unit-tested in isolation.
 */
public final class DecalStore {
    public static final int BOOT = 0, BOOT_SPRINT = 1, HOOF = 2, PAW = 3, IMPACT = 4, BIRD = 5, SCUFF = 6, SOLE = 7,
            SPLAT = 8, HEAVY = 9, DOTS = 10, HORSESHOE = 11;

    /** How many reasonably fresh decals may overlap one spot before the older ones are faded out. */
    private static final int LOCAL_LIMIT = 4;
    private static final float LOCAL_RADIUS_SQ = 0.3f * 0.3f;

    public final int capacity;
    public int count;

    public final double[] x;
    public final double[] y;
    public final double[] z;
    /** Unit forward direction (toe) in the XZ plane. */
    public final float[] dirX;
    public final float[] dirZ;
    public final float[] halfWidth;
    public final float[] halfLength;
    public final float[] strength;
    public final float[] wear;
    public final float[] r;
    public final float[] g;
    public final float[] b;
    public final byte[] sprite;
    public final boolean[] mirror;
    /** Flat darkening stain (wet print) instead of a lit impression. */
    public final boolean[] stain;
    /** Open to the sky, so weather erodes it. */
    public final boolean[] exposed;
    public final int[] light;
    private final float[] baseRate;
    private final float[] windSensitivity;
    private final float[] rainSensitivity;
    private final float[] snowSensitivity;

    /** Decals evicted to make room since creation (debug). */
    public long evicted;

    public DecalStore(int capacity) {
        this.capacity = capacity;
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        dirX = new float[capacity];
        dirZ = new float[capacity];
        halfWidth = new float[capacity];
        halfLength = new float[capacity];
        strength = new float[capacity];
        wear = new float[capacity];
        r = new float[capacity];
        g = new float[capacity];
        b = new float[capacity];
        sprite = new byte[capacity];
        mirror = new boolean[capacity];
        stain = new boolean[capacity];
        exposed = new boolean[capacity];
        light = new int[capacity];
        baseRate = new float[capacity];
        windSensitivity = new float[capacity];
        rainSensitivity = new float[capacity];
        snowSensitivity = new float[capacity];
    }

    /**
     * Adds a decal and returns its row.
     *
     * @param budget          current maximum number of live decals (≤ capacity)
     * @param lifetimeSeconds time to disappear in calm, dry conditions
     * @param windErosion     sensitivity to wind (0 immune .. 1 loose sand); likewise rain and snow
     */
    public int add(int budget, double px, double py, double pz, float forwardX, float forwardZ, float halfW, float halfL,
                   int spriteIndex, boolean mirrored, float decalStrength, float lifetimeSeconds, boolean isExposed,
                   boolean isStain, float red, float green, float blue, int packedLight,
                   float windErosion, float rainErosion, float snowErosion) {
        budget = Math.max(1, Math.min(budget, capacity));

        // Thin out the spot being stepped on.
        int overlapping = 0;
        for (int i = 0; i < count; i++) {
            double dx = x[i] - px;
            double dz = z[i] - pz;
            if (dx * dx + dz * dz < LOCAL_RADIUS_SQ && Math.abs(y[i] - py) < 0.3 && wear[i] < 0.75f) {
                if (++overlapping > LOCAL_LIMIT) {
                    wear[i] = Math.min(1f, wear[i] + 0.4f);
                } else {
                    wear[i] = Math.min(1f, wear[i] + 0.12f);
                }
            }
        }

        while (count >= budget) {
            int worst = 0;
            for (int i = 1; i < count; i++) {
                if (wear[i] > wear[worst]) {
                    worst = i;
                }
            }
            remove(worst);
            evicted++;
        }

        int i = count++;
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        float len = (float) Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (len < 1.0e-5f) {
            forwardX = 1f;
            forwardZ = 0f;
            len = 1f;
        }
        dirX[i] = forwardX / len;
        dirZ[i] = forwardZ / len;
        halfWidth[i] = halfW;
        halfLength[i] = halfL;
        sprite[i] = (byte) spriteIndex;
        mirror[i] = mirrored;
        strength[i] = decalStrength;
        wear[i] = 0f;
        stain[i] = isStain;
        exposed[i] = isExposed;
        r[i] = red;
        g[i] = green;
        b[i] = blue;
        light[i] = packedLight;
        baseRate[i] = 1f / Math.max(1f, lifetimeSeconds);
        windSensitivity[i] = windErosion;
        rainSensitivity[i] = rainErosion;
        snowSensitivity[i] = snowErosion;
        return i;
    }

    /**
     * Ages every decal.
     *
     * @param dtSeconds     elapsed time
     * @param lifetimeScale user multiplier on calm-weather lifetime
     * @param windRate      wear per second that wind adds to a fully wind-sensitive exposed decal
     * @param rainRate      likewise for rain
     * @param snowRate      likewise for snowfall
     */
    public void tick(float dtSeconds, float lifetimeScale, float windRate, float rainRate, float snowRate) {
        float inv = 1f / Math.max(0.05f, lifetimeScale);
        for (int i = count - 1; i >= 0; i--) {
            float rate = baseRate[i] * inv;
            if (exposed[i]) {
                rate += windSensitivity[i] * windRate + rainSensitivity[i] * rainRate + snowSensitivity[i] * snowRate;
            }
            float w = wear[i] + rate * dtSeconds;
            if (w >= 1f) {
                remove(i);
            } else {
                wear[i] = w;
            }
        }
    }

    public void remove(int i) {
        int last = --count;
        if (i == last) {
            return;
        }
        x[i] = x[last];
        y[i] = y[last];
        z[i] = z[last];
        dirX[i] = dirX[last];
        dirZ[i] = dirZ[last];
        halfWidth[i] = halfWidth[last];
        halfLength[i] = halfLength[last];
        strength[i] = strength[last];
        wear[i] = wear[last];
        r[i] = r[last];
        g[i] = g[last];
        b[i] = b[last];
        sprite[i] = sprite[last];
        mirror[i] = mirror[last];
        stain[i] = stain[last];
        exposed[i] = exposed[last];
        light[i] = light[last];
        baseRate[i] = baseRate[last];
        windSensitivity[i] = windSensitivity[last];
        rainSensitivity[i] = rainSensitivity[last];
        snowSensitivity[i] = snowSensitivity[last];
    }

    public void clear() {
        count = 0;
    }
}
