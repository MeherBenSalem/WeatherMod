package dev.livingworld.climate;

import dev.livingworld.util.LWMath;

/**
 * Pure temperature model (degrees Celsius). No Minecraft types, so it is unit-testable and identical on
 * client and server.
 */
public final class ClimateMath {
    /** Minecraft day ticks at which the daily maximum is reached (14:00). */
    public static final int WARMEST_TICK = 8000;

    private ClimateMath() {
    }

    /** Daily mean air temperature at sea level for a vanilla biome temperature value. */
    public static float biomeMeanC(float vanillaTemperature) {
        return LWMath.table(vanillaTemperature,
                -0.7f, -16f,
                -0.5f, -12f,
                -0.2f, -10f,
                0.0f, -7f,
                0.2f, 3f,
                0.3f, 7f,
                0.5f, 13f,
                0.7f, 17f,
                0.8f, 19f,
                0.95f, 26f,
                1.2f, 28f,
                2.0f, 30f);
    }

    /** +1 at the warmest time of day (14:00), -1 at the coldest (02:00). */
    public static float diurnal(long timeOfDay) {
        double phase = (Math.floorMod(timeOfDay, 24000L) - WARMEST_TICK) / 24000.0 * Math.PI * 2.0;
        return (float) Math.cos(phase);
    }

    /** Half the day/night swing. Dry air swings wildly (deserts), humid air barely moves (jungles). */
    public static float diurnalAmplitude(float humidity) {
        return LWMath.lerp(15f, 3.5f, LWMath.clamp01(humidity));
    }

    /**
     * How much the sun manages to warm a climate, 0.35..1. Snow and ice reflect most of it, so frozen
     * biomes stay below freezing at midday instead of swinging like a desert.
     */
    public static float insolation(float meanC) {
        return LWMath.lerp(0.35f, 1f, LWMath.smoothstep(-6f, 14f, meanC));
    }

    /** Sine of the sun's elevation: 1 at noon, 0 at sunrise/sunset, negative at night. */
    public static float sunElevation(long timeOfDay) {
        return (float) Math.sin(Math.floorMod(timeOfDay, 24000L) / 24000.0 * Math.PI * 2.0);
    }

    /** Lapse rate: it gets colder with height above the lowlands. */
    public static float altitudeOffset(double y) {
        return -0.09f * (float) Math.max(0.0, y - 72.0);
    }

    /** Stable temperature of deep rock; slightly warmer toward the deepslate layers. */
    public static float caveTemperature(double y) {
        return 11f + LWMath.clamp((float) (8.0 - y) * 0.1f, 0f, 9f);
    }

    /**
     * Ambient air temperature.
     *
     * @param meanC        blended biome mean ({@link #biomeMeanC})
     * @param humidity     0 bone dry .. 1 saturated
     * @param timeOfDay    world time of day in ticks
     * @param y            height of the sample
     * @param rain         precipitation intensity 0..1
     * @param thunder      storm intensity 0..1
     * @param sun          direct sunlight reaching the sample 0..1 (sky exposure x clear sky)
     * @param cave         0 open air .. 1 deep underground
     * @param heatSources  summed contribution of nearby lava, fire, etc. in degrees
     * @param dayCycle     1 for dimensions with a sun and open sky, 0 for the Nether/End (no daily swing, no lapse)
     */
    public static float ambient(float meanC, float humidity, long timeOfDay, double y, float rain, float thunder,
                                float sun, float cave, float heatSources, float dayCycle) {
        float insolation = insolation(meanC);
        float swing = diurnal(timeOfDay) * diurnalAmplitude(humidity) * insolation * (1f - 0.5f * LWMath.clamp01(rain));
        float radiant = sun * Math.max(0f, sunElevation(timeOfDay)) * (2f + 3f * (1f - LWMath.clamp01(humidity))) * insolation;
        float surface = meanC + dayCycle * (swing + altitudeOffset(y) + radiant)
                - 5f * LWMath.clamp01(rain) - 2f * thunder;
        return LWMath.lerp(surface, caveTemperature(y), LWMath.clamp01(cave) * dayCycle) + heatSources;
    }

    /** What the air feels like on exposed skin: wind chill below 12 degrees, evaporative chill when soaked. */
    public static float feelsLike(float ambientC, float windSpeed, float windExposure, float wetness) {
        float t = ambientC;
        if (t < 12f) {
            t -= LWMath.clamp01(windSpeed) * windExposure * (0.35f + (12f - t) / 30f) * 9f;
        }
        if (t < 26f) {
            t -= wetness * (3f + 4f * LWMath.clamp01(windSpeed) * windExposure);
        }
        return t;
    }

    /** Strength (0..1) of heat shimmer for an air temperature and direct sun amount. */
    public static float heatHaze(float ambientC, float sun) {
        return LWMath.smoothstep(29f, 43f, ambientC) * (0.3f + 0.7f * LWMath.clamp01(sun));
    }

    /** How visible exhaled breath is (0..1). */
    public static float breathVisibility(float ambientC) {
        return LWMath.smoothstep(8f, -5f, ambientC);
    }

    /** How strongly frost creeps over the lens (0..1). */
    public static float frost(float feelsLikeC) {
        return LWMath.smoothstep(-8f, -26f, feelsLikeC);
    }

    /** Cold stress 0..1 on an unprotected body. */
    public static float coldStress(float feelsLikeC) {
        return LWMath.smoothstep(2f, -22f, feelsLikeC);
    }

    /** Heat stress 0..1 on an unprotected body. */
    public static float heatStress(float feelsLikeC) {
        return LWMath.smoothstep(33f, 47f, feelsLikeC);
    }
}
