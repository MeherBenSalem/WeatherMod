package dev.livingworld.climate;

import java.util.Locale;

/** Named weather states. Used for test overrides and for describing the continuous simulation in debug output. */
public enum WeatherType {
    CLEAR(0f, 0f, 0f, 0f),
    DRIZZLE(0.14f, 0f, 0f, 0.05f),
    RAIN(0.4f, 0f, 0f, 0.12f),
    HEAVY_RAIN(0.7f, 0f, 0f, 0.25f),
    TORRENTIAL(1.0f, 0.15f, 0f, 0.35f),
    THUNDERSTORM(0.85f, 1f, 0f, 0.5f),
    EXTREME_STORM(1.25f, 1f, 0f, 0.8f),
    SNOW(0.45f, 0f, 0f, 0.1f),
    BLIZZARD(1.1f, 0.1f, 0f, 0.85f),
    SANDSTORM(0f, 0f, 1f, 0.9f);

    /** Precipitation intensity this state forces. */
    public final float precipitation;
    public final float thunder;
    public final float sand;
    /** Additive wind strength. */
    public final float wind;

    WeatherType(float precipitation, float thunder, float sand, float wind) {
        this.precipitation = precipitation;
        this.thunder = thunder;
        this.sand = sand;
        this.wind = wind;
    }

    public boolean forcesSnow() {
        return this == SNOW || this == BLIZZARD;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WeatherType byId(String id) {
        for (WeatherType t : values()) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** Names a continuous state. */
    public static WeatherType classify(float rain, float snow, float thunder, float sand) {
        if (sand > 0.35f) {
            return SANDSTORM;
        }
        if (snow > rain) {
            return snow > 0.75f ? BLIZZARD : snow > 0.04f ? SNOW : CLEAR;
        }
        if (thunder > 0.5f && rain > 0.2f) {
            return rain > 1.05f ? EXTREME_STORM : THUNDERSTORM;
        }
        if (rain > 0.9f) {
            return TORRENTIAL;
        }
        if (rain > 0.55f) {
            return HEAVY_RAIN;
        }
        if (rain > 0.25f) {
            return RAIN;
        }
        return rain > 0.03f ? DRIZZLE : CLEAR;
    }
}
