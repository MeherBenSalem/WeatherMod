package dev.livingworld.climate;

import dev.livingworld.util.LWMath;
import dev.livingworld.util.Noise;

/**
 * The global wind field of one dimension.
 *
 * <p>Wind is a pure function of the (server-synchronised) world time, a per-dimension salt and the world
 * position, so every client in a multiplayer world computes the same direction, the same sustained speed
 * and the same travelling gust fronts with no packets at all. Weather, biome and altitude only scale it.
 *
 * <p>Units: speed is normalised, 0 = dead calm, 0.3 = breeze, 0.7 = strong wind, 1.0 = gale, up to 1.6.
 */
public final class WindField {
    /** Horizontal distance (blocks) an airborne light particle is carried per tick at speed 1.0. */
    public static final float BLOCKS_PER_TICK = 0.42f;

    private static final int SEED_DIR = 0x51ED27;
    private static final int SEED_CALM = 0x3C6EF3;
    private static final int SEED_GUST = 0x7A1B95;

    /** Unit vector the wind blows toward. */
    public float dirX = 1f;
    public float dirZ = 0f;
    /** Direction as an angle in radians (atan2(dirZ, dirX)). */
    public float angle;
    /** Sustained strength at the reference position. */
    public float base;
    /** Gust envelope 0..1 at the reference position. */
    public float gust;
    /** Instantaneous speed at the reference position (base modulated by the gust). */
    public float speed;

    private double timeSeconds;
    private int salt;

    /**
     * Recomputes the field for the given moment.
     *
     * @param timeTicks       world time in ticks (may include the partial tick)
     * @param dimensionSalt   any stable per-dimension integer
     * @param weatherBoost    additive strength from storms (0..~0.9)
     * @param windiness       biome multiplier (≈0.6 sheltered forest .. 1.35 exposed peaks)
     * @param altitudeBoost   additive strength from height (0..0.4)
     * @param override        fixed strength set by a test command, or a negative value for none
     */
    public void update(double timeTicks, int dimensionSalt, float weatherBoost, float windiness, float altitudeBoost,
                       float override, double refX, double refZ) {
        this.timeSeconds = timeTicks / 20.0;
        this.salt = dimensionSalt;

        // Direction wanders through more than a full turn over roughly twenty minutes, with a faster small wobble.
        float slow = Noise.fbm1(timeSeconds / 1300.0, 2, SEED_DIR ^ salt);
        float wobble = Noise.value1(timeSeconds / 23.0, SEED_DIR * 31 ^ salt) - 0.5f;
        this.angle = slow * LWMath.TAU * 1.6f + wobble * 0.35f;
        this.dirX = (float) Math.cos(angle);
        this.dirZ = (float) Math.sin(angle);

        if (override >= 0f) {
            this.base = override;
        } else {
            float calm = Noise.fbm1(timeSeconds / 260.0 + 100.0, 3, SEED_CALM ^ salt);
            float base0 = 0.07f + 0.6f * (float) Math.pow(calm, 1.7);
            this.base = Math.min(1.6f, (base0 + weatherBoost) * windiness + altitudeBoost);
        }
        this.gust = gustAt(refX, refZ);
        this.speed = base * (0.6f + 0.8f * gust);
    }

    /** Gust envelope (0..1) at a world position: slow rolling blobs a few dozen blocks across. */
    public float gustAt(double x, double z) {
        float g = Noise.fbm3(x / 46.0, z / 46.0, timeSeconds * 0.23, 2, SEED_GUST ^ salt);
        // Push the distribution apart so lulls and gusts are both distinct.
        return LWMath.smoothstep(0.22f, 0.78f, g);
    }

    /** Instantaneous speed at a world position. */
    public float speedAt(double x, double z) {
        return base * (0.6f + 0.8f * gustAt(x, z));
    }

    /** Stable per-dimension salt derived from a dimension id such as "minecraft:overworld". */
    public static int saltOf(String dimensionId) {
        return Noise.hash(dimensionId.hashCode());
    }
}
