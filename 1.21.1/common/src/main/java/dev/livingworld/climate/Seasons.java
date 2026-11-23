package dev.livingworld.climate;

import dev.livingworld.climate.BiomeProfile.Kind;
import dev.livingworld.util.LWMath;

/**
 * The year. Pure functions of the world's day counter, so every client works out the same season from
 * the time the server already synchronises, with no packets and nothing stored in the world.
 *
 * <p>A year is four seasons of equal length. Its position is a phase in [0, 1): 0 is the first morning
 * of spring, 0.25 of summer, 0.5 of autumn, 0.75 of winter. Temperature lags the calendar the way it
 * does on Earth: the coldest days fall in the middle of winter, not at its start.
 */
public final class Seasons {
    /** Length of one season in Minecraft days unless a server says otherwise. */
    public static final float DEFAULT_DAYS_PER_SEASON = 8f;
    /**
     * Where in the year a new world begins: well into spring, so the first thing a player sees is new
     * growth and mild air, not the tail end of winter.
     */
    public static final float EPOCH = 0.1f;
    /**
     * How far midsummer rises above, and midwinter falls below, a biome's ordinary temperature where
     * seasons are strongest, in degrees. Lopsided on purpose: Minecraft's biomes are already as warm as a
     * fine day in early summer, so most of the year's swing has to go downward for winter to bring frost
     * without summer turning every meadow into a desert.
     */
    public static final float SUMMER_RISE_C = 7f;
    public static final float WINTER_DROP_C = 17f;

    public enum Season {
        SPRING, SUMMER, AUTUMN, WINTER;

        public static final Season[] VALUES = values();

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Phase at which this season begins. */
        public float start() {
            return ordinal() * 0.25f;
        }

        public static Season byId(String id) {
            for (Season s : VALUES) {
                if (s.id().equals(id)) {
                    return s;
                }
            }
            return null;
        }
    }

    private Seasons() {
    }

    /**
     * Position in the year, 0..1.
     *
     * @param timeOfDay     the world's day-time counter in ticks (the one that sleeping advances)
     * @param daysPerSeason length of a season in days
     * @param offsetDays    calendar shift in days (set by {@code /livingworld season set})
     */
    public static float yearPhase(long timeOfDay, float daysPerSeason, float offsetDays) {
        double year = Math.max(0.25, daysPerSeason) * 4.0;
        double p = (timeOfDay / 24000.0 + offsetDays) / year + EPOCH;
        return (float) (p - Math.floor(p));
    }

    public static Season of(float phase) {
        return Season.VALUES[Math.min(3, Math.max(0, (int) (wrap(phase) * 4f)))];
    }

    /** Day within the current season, starting at 1. */
    public static int dayOfSeason(float phase, float daysPerSeason) {
        float within = wrap(phase) * 4f;
        within -= (float) Math.floor(within);
        return (int) Math.floor(within * Math.max(0.25f, daysPerSeason)) + 1;
    }

    /** The calendar shift (days) that makes {@code timeOfDay} fall in the middle of a season. */
    public static float offsetFor(Season season, long timeOfDay, float daysPerSeason) {
        float days = Math.max(0.25f, daysPerSeason);
        double year = days * 4.0;
        double target = (season.start() + 0.125 - EPOCH) * year;
        double now = timeOfDay / 24000.0;
        double offset = (target - now) % year;
        return (float) (offset < 0.0 ? offset + year : offset);
    }

    /** -1 in the depth of winter .. +1 at the height of summer. */
    public static float warmth(float phase) {
        return (float) Math.sin((wrap(phase) - 0.125) * Math.PI * 2.0);
    }

    /** Shift of the daily mean temperature, in degrees, for a place with the given seasonality (0..1). */
    public static float temperatureOffsetC(float phase, float seasonality) {
        float w = warmth(phase);
        return w * (w >= 0f ? SUMMER_RISE_C : WINTER_DROP_C) * LWMath.clamp01(seasonality);
    }

    /** How far the leaves have turned, 0..1: builds through early autumn, holds, and is over by winter. */
    public static float autumnColour(float phase) {
        return pulse(phase, 0.48f, 0.6f, 0.74f, 0.82f);
    }

    /** How bare and dormant the land is, 0..1: sets in as autumn ends and lifts in the first days of spring. */
    public static float winterBareness(float phase) {
        return pulse(phase, 0.72f, 0.82f, 0.98f, 1.07f);
    }

    /** New growth, 0..1: the pale, bright green of spring. */
    public static float springFreshness(float phase) {
        return pulse(phase, 0.02f, 0.1f, 0.2f, 0.3f);
    }

    /**
     * How well crops grow at this point of the year where seasons are strongest, relative to vanilla:
     * a quarter faster through spring, as usual in summer, slowing through autumn to a fifth in winter,
     * and picking up again as winter ends.
     */
    public static float growth(float phase) {
        return LWMath.table(wrap(phase),
                0f, 0.9f,
                0.06f, 1.25f,
                0.2f, 1.25f,
                0.3f, 1f,
                0.5f, 1f,
                0.6f, 0.7f,
                0.72f, 0.6f,
                0.8f, 0.2f,
                0.95f, 0.2f,
                1f, 0.9f);
    }

    /** {@link #growth(float)} for a place with the given seasonality: 1 (no change) where there are no seasons. */
    public static float growth(float phase, float seasonality) {
        return LWMath.lerp(1f, growth(phase), LWMath.clamp01(seasonality));
    }

    /**
     * How strongly a biome family has seasons at all, 0..1. Temperate land has all four; the tropics and
     * the deserts barely notice the year; caves and the other dimensions do not have one.
     */
    public static float seasonality(Kind kind) {
        return switch (kind) {
            case PLAINS, FOREST, TAIGA, MOUNTAIN -> 1f;
            case RIVER -> 0.9f;
            case SNOWY -> 0.8f;
            case SWAMP, BEACH -> 0.7f;
            case OCEAN -> 0.55f;
            case MUSHROOM -> 0.5f;
            case DESERT, BADLANDS -> 0.4f;
            case SAVANNA -> 0.3f;
            case JUNGLE -> 0.12f;
            default -> 0f;
        };
    }

    private static float wrap(float phase) {
        return phase - (float) Math.floor(phase);
    }

    /** A plateau that rises between r0..r1 and falls between f0..f1 on the cyclic year (f1 may exceed 1). */
    private static float pulse(float phase, float r0, float r1, float f0, float f1) {
        float p = wrap(phase);
        float a = LWMath.smoothstep(r0, r1, p) * (1f - LWMath.smoothstep(f0, f1, p));
        float b = LWMath.smoothstep(r0, r1, p + 1f) * (1f - LWMath.smoothstep(f0, f1, p + 1f));
        return Math.max(a, b);
    }
}
