package dev.livingworld.client.config;

/** Graphics presets. Each one is a set of hard budgets, so cost is bounded no matter what the weather does. */
public enum QualityPreset {
    //            particles drops  decals splash  post   wet    clouds shafts bloom  reflection steps  cloud slices  cloud scale
    PERFORMANCE(  1200,     1200,  256,   0.4f,   true,  false, false, false, false, 0,                8,            0.5f),
    BALANCED(     3000,     3200,  768,   1.0f,   true,  true,  true,  false, true,  14,               16,           0.5f),
    ULTRA(        6000,     6000,  1536,  1.6f,   true,  true,  true,  true,  true,  20,               22,           1f),
    CINEMATIC(    12000,    10000, 3072,  2.4f,   true,  true,  true,  true,  true,  26,               32,           1f);

    /** Hard cap on live simulated particles. */
    public final int maxParticles;
    /** Hard cap on precipitation streaks/flakes drawn per frame. */
    public final int maxDrops;
    /** Hard cap on live footprint decals. */
    public final int maxDecals;
    /** Multiplier on splash/ripple/ambient spawn rates. */
    public final float spawnScale;
    /** Full-screen atmosphere pass (haze, mist, grading, heat shimmer). */
    public final boolean post;
    /** Screen-space wet surfaces, puddles and snow cover. */
    public final boolean wetness;
    /** Procedural overcast cloud deck. */
    public final boolean clouds;
    /** Screen-space light shafts. */
    public final boolean lightShafts;
    /** Bloom (four extra quarter-resolution passes). */
    public final boolean bloom;
    /** Steps of the screen-space reflection ray on water; 0 = water mirrors the sky only. */
    public final int reflectionSteps;
    /**
     * Horizontal slices the mod's own cloud layer is cut into (one sample of each per pixel); 0 = the
     * layer is not drawn and the game keeps its own clouds.
     */
    public final int cloudSteps;
    /**
     * Resolution the cloud layer is drawn at for open sky, as a share of the screen's: at 0.5 it goes to
     * a buffer a quarter of the size first (a quarter of the cost), at 1 it is worked out per pixel.
     */
    public final float cloudScale;

    QualityPreset(int maxParticles, int maxDrops, int maxDecals, float spawnScale, boolean post, boolean wetness,
                  boolean clouds, boolean lightShafts, boolean bloom, int reflectionSteps, int cloudSteps,
                  float cloudScale) {
        this.reflectionSteps = reflectionSteps;
        this.cloudSteps = cloudSteps;
        this.cloudScale = cloudScale;
        this.maxParticles = maxParticles;
        this.maxDrops = maxDrops;
        this.maxDecals = maxDecals;
        this.spawnScale = spawnScale;
        this.post = post;
        this.wetness = wetness;
        this.clouds = clouds;
        this.lightShafts = lightShafts;
        this.bloom = bloom;
    }

    public static final int MAX_PARTICLES = 12000;
    public static final int MAX_DECALS = 3072;
}
