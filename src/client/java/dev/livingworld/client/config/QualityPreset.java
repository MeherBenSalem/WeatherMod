package dev.livingworld.client.config;

/** Graphics presets. Each one is a set of hard budgets, so cost is bounded no matter what the weather does. */
public enum QualityPreset {
    //            particles drops  decals splash  post   wet    clouds shafts bloom  reflection steps
    PERFORMANCE(  1200,     1200,  256,   0.4f,   true,  false, false, false, false, 0),
    BALANCED(     3000,     3200,  768,   1.0f,   true,  true,  true,  false, true,  14),
    ULTRA(        6000,     6000,  1536,  1.6f,   true,  true,  true,  true,  true,  20),
    CINEMATIC(    12000,    10000, 3072,  2.4f,   true,  true,  true,  true,  true,  26);

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

    QualityPreset(int maxParticles, int maxDrops, int maxDecals, float spawnScale, boolean post, boolean wetness,
                  boolean clouds, boolean lightShafts, boolean bloom, int reflectionSteps) {
        this.reflectionSteps = reflectionSteps;
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
