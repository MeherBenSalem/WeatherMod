package dev.livingworld.climate;

import dev.livingworld.util.LWMath;
import dev.livingworld.util.Noise;

/**
 * Turns Minecraft's two weather gradients into a richer, continuously varying intensity. Pure and
 * deterministic in world time so all clients agree.
 */
public final class WeatherModel {
    private static final int SEED_VARIATION = 0x2F6B11;

    private WeatherModel() {
    }

    /** Slow intensity wander in [0, 1]: showers swell and ease over a couple of minutes. */
    public static float variation(double timeTicks, int dimensionSalt) {
        return Noise.fbm1(timeTicks / 20.0 / 95.0, 3, SEED_VARIATION ^ dimensionSalt);
    }

    /**
     * Global precipitation intensity (0..1.3) before biome and shelter are considered.
     *
     * @param rainGradient    vanilla rain gradient 0..1
     * @param thunderGradient vanilla thunder gradient 0..1
     */
    public static float intensity(float rainGradient, float thunderGradient, double timeTicks, int dimensionSalt) {
        if (rainGradient <= 0f) {
            return 0f;
        }
        float v = variation(timeTicks, dimensionSalt);
        // Ordinary rain ranges from drizzle to heavy; a thunderstorm lifts both the floor and the ceiling.
        float calm = 0.12f + 0.78f * LWMath.smoothstep(0.2f, 0.85f, v);
        float stormy = 0.7f + 0.6f * v;
        return rainGradient * LWMath.lerp(calm, stormy, LWMath.clamp01(thunderGradient));
    }

    /** Extra sustained wind brought by the weather. */
    public static float windBoost(float intensity, float thunderGradient, float sand, float snowFraction) {
        float i = LWMath.clamp01(intensity);
        return 0.28f * i + 0.32f * thunderGradient + 0.5f * sand + 0.3f * snowFraction * i;
    }

    /**
     * Sandstorm strength for dry, dusty ground: needs sustained strong wind, and is much more likely
     * while a weather front is passing (vanilla "rain" over a desert).
     */
    public static float sandstorm(float dustiness, float dryFraction, float windBase, float rainGradient) {
        float threshold = LWMath.lerp(0.62f, 0.4f, LWMath.clamp01(rainGradient));
        return dustiness * dryFraction * LWMath.smoothstep(threshold, threshold + 0.3f, windBase);
    }

    /** Cloud cover 0..1 from the rain gradient and an optional forecast (ticks until rain, or negative). */
    public static float cloudCover(float rainGradient, float thunderGradient, int ticksUntilRain) {
        float cover = LWMath.clamp01(rainGradient * 0.85f + thunderGradient * 0.15f);
        if (ticksUntilRain >= 0) {
            // Clouds gather during the two minutes before the first drop.
            cover = Math.max(cover, 0.7f * LWMath.smoothstep(2400f, 100f, ticksUntilRain));
        }
        return cover;
    }
}
