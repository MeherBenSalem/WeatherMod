package dev.livingworld.client.env;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.climate.BiomeProfile.Kind;
import dev.livingworld.climate.ClimateMath;
import dev.livingworld.util.LWMath;
import dev.livingworld.util.Noise;

/**
 * The unified atmosphere controller. From the shared {@link ClientEnvironment} it derives everything
 * that colours the air: distance haze, low-lying mist, the visibility cap during storms, the colour
 * grade, eye adaptation, heat shimmer, the overcast cloud deck and light shafts.
 *
 * <p>Fog is built from physically motivated components (humidity, dust, rain, snow, sand, cold) that are
 * summed as densities, so e.g. a humid jungle in heavy rain is hazier than either alone, and the haze
 * colour is the density-weighted mix of its causes.
 */
public final class Atmosphere {
    public static final Atmosphere INSTANCE = new Atmosphere();
    private static final float DT = 0.05f;

    // ---- distance haze -------------------------------------------------------------------------
    /** Extinction per block. */
    public float hazeDensity;
    public float hazeR = 0.8f, hazeG = 0.85f, hazeB = 0.9f;
    /** Upper bound on haze opacity so the world never disappears completely. */
    public float hazeMax = 0.9f;
    /** Density contributed by storms only (sand, blizzard); also tints the sky. */
    public float stormHaze;

    // ---- ground mist ---------------------------------------------------------------------------
    public float mistDensity;
    public float mistBase = 64f;
    public float mistFalloff = 0.35f;
    public float mistR = 0.9f, mistG = 0.92f, mistB = 0.95f;

    // ---- vanilla fog ---------------------------------------------------------------------------
    /** Cap on the vanilla fog end in blocks ({@link Float#MAX_VALUE} = no cap). */
    public float visibility = Float.MAX_VALUE;
    /** How far the vanilla fog/sky colours are pulled toward the haze colour. */
    public float fogColorMix;
    public float skyColorMix;

    // ---- grade ---------------------------------------------------------------------------------
    public float tintR = 1f, tintG = 1f, tintB = 1f;
    public float saturation = 1f;
    public float contrast = 1f;
    public float exposure = 1f;
    private float adaptedLuminance = 0.6f;

    // ---- other ---------------------------------------------------------------------------------
    public float heatShimmer;
    public float cloudDeck;
    public float cloudDarkness;
    public float lightShafts;
    /** Screen-space wet darkening, puddle level, snow cover (already scaled by config/quality). */
    public float wetSurfaces;
    public float puddles;
    public float snowCover;
    /** The part of {@link #snowCover} that winter keeps on cold ground between snowfalls, 0..1. */
    public float lyingSnow;

    // ---- sky ------------------------------------------------------------------------------------
    /** True while the mod draws its own cloud layer (and the vanilla one is switched off). */
    public boolean cloudsDrawn;
    /** How much of the sky the cloud layer covers, 0 clear .. 1 overcast. */
    public float cloudCoverage;
    /** World height of the cloud base and how tall the clouds stand above it, in blocks. */
    public float cloudBase = 192f;
    public float cloudThickness = 90f;
    /** Light the sun or moon puts on the clouds, and the light of the sky around them. */
    public float cloudLitR = 1f, cloudLitG = 1f, cloudLitB = 1f;
    public float cloudAmbientR = 0.5f, cloudAmbientG = 0.6f, cloudAmbientB = 0.75f;
    /** The clouds' overall colour seen from the ground: what water and puddles mirror. */
    public float cloudSeenR = 0.9f, cloudSeenG = 0.9f, cloudSeenB = 0.92f;
    /** How strongly clouds shade the ground under them, 0..1. */
    public float cloudShadow;
    /** Extra stars and the Milky Way, 0..1. */
    public float starlight;
    /** Northern lights, 0..1. */
    public float aurora;
    /** A rainbow opposite a low sun while the air is still wet, 0..1. */
    public float rainbow;
    /** How much the enhanced sky colours are applied, 0..1. */
    public float skyColours;
    /** Test hook: holds the aurora at this strength regardless of the night's own activity (negative = off). */
    public static float forceAurora = -1f;
    /** Test hook: holds the fair-weather cloud cover at this value (negative = let it wander). */
    public static float forceFairCover = -1f;

    private static final int SEED_FAIR = 0x61C8A7;
    private static final int SEED_AURORA = 0x2B9D43;

    private final float[] dust = new float[3];
    private float accR, accG, accB, accD;

    private Atmosphere() {
    }

    private void add(float density, float r, float g, float b) {
        if (density <= 0f) {
            return;
        }
        accD += density;
        accR += density * r;
        accG += density * g;
        accB += density * b;
    }

    public void tick(ClientEnvironment e) {
        if (!e.inWorld) {
            return;
        }
        ClientConfig c = ClientConfig.get();
        float fogScale = c.fogScale();
        float dry = 1f - e.submerged;
        // Outdoor light available to scatter: day, moonlight, or the dimension's own glow.
        float lit = e.natural ? Math.max(0.1f, e.daylight * (1f - 0.35f * e.cloudCover)) : 1f;
        float outdoors = 0.3f + 0.7f * e.openness;
        e.dustColor(dust);

        // ---- distance haze -------------------------------------------------------------------
        accD = accR = accG = accB = 0f;
        float storms = 0f;
        if (e.natural) {
            add((0.0005f + 0.0034f * e.humidity * e.humidity) * outdoors, 0.78f * lit, 0.85f * lit, 0.92f * lit);
            add(0.0022f * e.dustiness * (0.3f + e.wind.speed) * outdoors, dust[0] * lit, dust[1] * lit, dust[2] * lit);
            add(0.0065f * (float) Math.pow(e.rain, 1.3) * outdoors, 0.56f * lit, 0.61f * lit, 0.67f * lit);
            float blizzard = 0.008f * e.snow + 0.05f * LWMath.smoothstep(0.55f, 1.1f, e.snow) * (0.5f + 0.5f * LWMath.clamp01(e.wind.speed));
            float sandstorm = 0.01f * e.sand + 0.07f * e.sand * e.sand;
            add(blizzard * outdoors, 0.84f * lit, 0.88f * lit, 0.93f * lit);
            add(sandstorm * outdoors, dust[0] * lit, dust[1] * lit, dust[2] * lit);
            storms = (blizzard + sandstorm) * outdoors;
            // Freezing fog: very cold, some moisture, little wind.
            float freezing = LWMath.smoothstep(-8f, -20f, e.ambientC) * LWMath.smoothstep(0.3f, 0.5f, e.humidity)
                    * (1f - LWMath.smoothstep(0.3f, 0.7f, e.wind.speed));
            add(0.006f * freezing * outdoors, 0.86f * lit, 0.9f * lit, 0.95f * lit);
            // Damp, dark air underground.
            add(0.004f * e.cave * (0.4f + 0.6f * e.humidity), 0.035f, 0.04f, 0.045f);
        } else if (e.nether) {
            add(0.0025f * e.kind(Kind.NETHER_WASTES), 0.36f, 0.09f, 0.07f);
            add(0.0035f * e.kind(Kind.CRIMSON_FOREST), 0.42f, 0.06f, 0.06f);
            add(0.0035f * e.kind(Kind.WARPED_FOREST), 0.1f, 0.3f, 0.27f);
            add(0.005f * e.kind(Kind.SOUL_SAND_VALLEY), 0.1f, 0.24f, 0.28f);
            add(0.008f * e.kind(Kind.BASALT_DELTAS), 0.3f, 0.27f, 0.32f);
        } else if (e.end) {
            add(0.0014f, 0.13f, 0.09f, 0.17f);
        }
        float density = accD * fogScale * dry;
        float k = 1f - (float) Math.exp(-1.2f * DT);
        hazeDensity += (density - hazeDensity) * k;
        stormHaze += (storms * fogScale * dry - stormHaze) * k;
        if (accD > 1.0e-6f) {
            hazeR += (accR / accD - hazeR) * k;
            hazeG += (accG / accD - hazeG) * k;
            hazeB += (accB / accD - hazeB) * k;
        }
        hazeMax = c.reduceFog ? 0.6f : 0.93f;

        // ---- ground mist ---------------------------------------------------------------------
        float mist = 0f;
        float falloff = 0.35f;
        if (c.groundMist && e.natural) {
            long t = e.timeOfDay % 24000L;
            float morning = t > 12000L ? LWMath.smoothstep(21600f, 23300f, t) : 1f - LWMath.smoothstep(700f, 2700f, t);
            float night = 1f - e.daylight;
            float calm = 1f - LWMath.smoothstep(0.3f, 0.7f, e.wind.speed);
            float clear = 1f - LWMath.clamp01(e.precip * 2f);
            mist += morning * (0.2f + 0.8f * e.humidity) * calm * clear * 0.1f * (1f - e.arid());
            // Dawn mist hugs the ground more tightly than swamp or jungle fog.
            falloff = LWMath.lerp(0.35f, 0.55f, morning);
            mist += e.kind(Kind.SWAMP) * (0.022f + 0.028f * night) * calm;
            mist += e.kind(Kind.JUNGLE) * 0.016f * calm;
            mist += e.kind(Kind.RIVER) * morning * 0.02f;
            // Steam lifting off warm, wet ground once the rain has passed.
            mist += e.wetness * LWMath.smoothstep(16f, 26f, e.ambientC) * e.daylight * clear * 0.02f;
            // Cold air pooling over water and snow.
            mist += LWMath.smoothstep(2f, -10f, e.ambientC) * e.humidity * calm * 0.018f;
            mist *= 0.35f + 0.65f * e.openness;
        }
        if (c.groundMist && !e.end) {
            mist += e.kind(Kind.LUSH_CAVES) * 0.03f + e.cave * e.humidity * 0.012f;
            mist += e.kind(Kind.SOUL_SAND_VALLEY) * 0.02f;
        }
        mist *= fogScale * dry;
        mistDensity += (mist - mistDensity) * (1f - (float) Math.exp(-0.5f * DT));
        mistFalloff = falloff;
        mistBase = e.groundY;
        float warmth = e.natural ? LWMath.smoothstep(0.3f, 0f, Math.abs(e.sunElevation)) * e.daylight : 0f;
        float mr, mg, mb;
        if (e.nether) {
            mr = 0.14f;
            mg = 0.3f;
            mb = 0.34f;
        } else {
            float base = e.natural ? Math.max(0.16f, lit) : 0.25f;
            mr = (0.9f + 0.1f * warmth) * base;
            mg = (0.92f - 0.05f * warmth) * base;
            mb = (0.96f - 0.2f * warmth) * base;
        }
        mistR += (mr - mistR) * k;
        mistG += (mg - mistG) * k;
        mistB += (mb - mistB) * k;

        // ---- vanilla fog cap -----------------------------------------------------------------
        float vis = Float.MAX_VALUE;
        if (fogScale > 0f && e.natural && e.submerged < 0.5f) {
            float sandVis = LWMath.lerp(400f, 30f, e.sand * e.sand);
            float snowVis = LWMath.lerp(400f, 34f, LWMath.smoothstep(0.55f, 1.15f, e.snow));
            float rainVis = LWMath.lerp(400f, 110f, LWMath.smoothstep(0.5f, 1.2f, e.rain));
            float v = Math.min(sandVis, Math.min(snowVis, rainVis));
            if (v < 399f) {
                v = LWMath.lerp(400f, v, 0.3f + 0.7f * e.openness);
                vis = Math.max(c.reduceFog ? 72f : 20f, v / Math.max(0.4f, fogScale));
            }
        }
        visibility = vis;
        fogColorMix = LWMath.clamp01(1f - (float) Math.exp(-hazeDensity * 60f)) * 0.9f;
        skyColorMix = LWMath.clamp01(1f - (float) Math.exp(-stormHaze * 45f)) * 0.92f;

        // ---- colour grade --------------------------------------------------------------------
        float g = c.colorGrading ? c.gradingStrength : 0f;
        float warm = e.natural ? LWMath.smoothstep(26f, 42f, e.ambientC) * e.daylight : 0f;
        float cold = e.natural ? LWMath.smoothstep(4f, -18f, e.ambientC) : 0f;
        if (!c.temperatureTint) {
            warm = 0f;
            cold = 0f;
        }
        float tr = 1f + warm * 0.07f - cold * 0.055f + warmth * (1f - e.cloudCover) * 0.045f;
        float tg = 1f + warm * 0.02f + warmth * (1f - e.cloudCover) * 0.008f;
        float tb = 1f - warm * 0.08f + cold * 0.075f - warmth * (1f - e.cloudCover) * 0.04f;
        float sat = 1f + warm * 0.06f - cold * 0.1f - LWMath.clamp01(e.rain) * 0.12f - e.storm * 0.1f - e.sand * 0.18f
                - LWMath.clamp01(e.snow) * 0.1f + e.kind(Kind.JUNGLE) * 0.07f + e.kind(Kind.FOREST) * 0.03f;
        float con = 1f + warm * 0.05f + LWMath.clamp01(e.rain) * 0.03f;
        float dim = 1f - e.storm * 0.1f - e.sand * 0.05f - LWMath.clamp01(e.snow - 0.6f) * 0.06f;
        if (e.nether) {
            tr = 1.05f;
            tg = 0.975f;
            tb = 0.93f;
            sat = 1.05f;
        } else if (e.end) {
            tr = 0.965f;
            tg = 0.955f;
            tb = 1.045f;
            sat = 0.9f;
        }
        tintR += (LWMath.lerp(1f, tr * dim, g) - tintR) * k;
        tintG += (LWMath.lerp(1f, tg * dim, g) - tintG) * k;
        tintB += (LWMath.lerp(1f, tb * dim, g) - tintB) * k;
        saturation += (LWMath.lerp(1f, sat, g) - saturation) * k;
        contrast += (LWMath.lerp(1f, con, g) - contrast) * k;

        // ---- eye adaptation ------------------------------------------------------------------
        float outdoorLight = e.natural ? e.skyLight * (0.06f + 0.94f * e.daylight) : 0.3f;
        float luminance = Math.max(0.05f, Math.max(outdoorLight, e.blockLight * 0.55f));
        // Eyes adjust to brightness within a second or two, to darkness far more slowly.
        adaptedLuminance = LWMath.approach(adaptedLuminance, luminance, 1.1f, 0.4f, DT);
        float exp = c.exposureAdaptation
                ? LWMath.clamp((float) Math.pow(luminance / adaptedLuminance, 0.32), 0.74f, 1.38f)
                : 1f;
        exposure += (exp - exposure) * 0.35f;

        // ---- heat shimmer --------------------------------------------------------------------
        float shimmer;
        if (e.nether) {
            shimmer = 0.4f + Math.min(0.6f, e.heatSources / 24f);
        } else if (e.end) {
            // Not heat: a faint, unexplained waver in the distance.
            shimmer = 0.16f;
        } else {
            float sun = e.natural ? e.lightStrength * (0.4f + 0.6f * e.rainExposure) : 0f;
            shimmer = ClimateMath.heatHaze(e.ambientC, sun) + LWMath.clamp(e.heatSources / 30f, 0f, 0.5f) * 0.5f;
        }
        shimmer *= c.distortionScale() * dry * (1f - LWMath.clamp01(e.precip * 2f));
        heatShimmer += (shimmer - heatShimmer) * k;

        // ---- clouds, shafts, wetness ---------------------------------------------------------
        // The flat overcast deck is only for when the mod is not drawing a cloud layer of its own.
        boolean deck = c.stormClouds && QualityManager.preset().clouds && e.natural && !cloudsDrawn;
        cloudDeck += ((deck ? e.cloudCover : 0f) - cloudDeck) * k;
        cloudDarkness = 0.35f + 0.65f * e.storm;

        float shafts = 0f;
        if (c.lightShafts && QualityManager.preset().lightShafts && e.natural && e.sunElevation > 0.02f) {
            float low = 1f - LWMath.smoothstep(0.25f, 0.85f, e.sunElevation);
            shafts = (0.12f + mistDensity * 9f + e.humidity * 0.2f + e.foliage * 0.12f) * (0.35f + 0.65f * low)
                    * (1f - e.cloudCover) * e.daylight * dry;
        }
        lightShafts += (Math.min(shafts, 0.6f) - lightShafts) * k;

        boolean wet = QualityManager.preset().wetness;
        wetSurfaces = wet && c.wetSurfaces ? LWMath.clamp01(Math.max(e.wetness, e.rain * 1.5f)) : 0f;
        puddles = wet && c.puddles ? e.wetness : 0f;
        // In winter, cold ground keeps a covering of snow between snowfalls: a dusting where the days
        // climb back above freezing, a full blanket where they do not.
        float dailyMean = e.meanC + e.seasonOffsetC + (e.natural ? ClimateMath.altitudeOffset(e.camY) : 0f);
        float lying = e.seasonsActive ? e.winter * LWMath.smoothstep(7f, -1f, dailyMean) * 0.85f : 0f;
        lyingSnow += (lying - lyingSnow) * k;
        snowCover = wet && c.snowCover ? Math.max(e.snowCover, lyingSnow * (1f - e.cave)) : 0f;

        tickSky(e, c, k);
    }

    /** The drawn cloud layer, the light on it, and the things that only show in a clear sky. */
    private void tickSky(ClientEnvironment e, ClientConfig c, float k) {
        // The mod's own cloud layer is drawn by the screen-space atmosphere pass, which this version does not have:
        // the vanilla clouds stay, and the cloud state below fades to nothing.
        float height = Float.NaN;
        cloudsDrawn = false;
        if (!Float.isNaN(height)) {
            cloudBase = height;
        }

        // Fair-weather cloud comes and goes over a quarter of an hour and is thicker in humid air; real
        // weather then closes the sky over it.
        float wander = Noise.fbm1(e.worldTime / 20.0 / 420.0 + 31.0, 2, SEED_FAIR ^ e.salt);
        float fair = LWMath.clamp(0.1f + 0.28f * e.humidity + 0.42f * (wander - 0.42f), 0.03f, 0.62f);
        if (forceFairCover >= 0f) {
            fair = forceFairCover;
        }
        float weather = LWMath.clamp01(e.cloudCover);
        float coverage = fair + (1f - fair) * (float) Math.pow(weather, 0.75);
        cloudCoverage += ((cloudsDrawn ? coverage : 0f) - cloudCoverage) * k;
        cloudThickness += ((80f + 90f * weather) - cloudThickness) * k;

        float sy = e.sunElevation;
        float low = LWMath.smoothstep(0.42f, 0.03f, sy) * LWMath.smoothstep(-0.16f, 0.02f, sy);
        float sunPower = LWMath.smoothstep(-0.14f, 0.07f, sy);
        float gloom = e.storm * weather;
        // Sunlight: white at noon, gold and then rose as the sun goes down; after dusk only the moon is left.
        float sr = 1f, sg = LWMath.lerp(0.98f, 0.63f, low), sb = LWMath.lerp(0.94f, 0.38f, low);
        float moon = 0.32f * (1f - sunPower);
        float direct = (1f - 0.62f * gloom) * (1f - 0.5f * e.sand);
        float lr = (sr * sunPower + 0.3f * moon * 1.5f) * direct;
        float lg = (sg * sunPower + 0.38f * moon * 1.5f) * direct;
        float lb = (sb * sunPower + 0.58f * moon * 1.5f) * direct;
        // Light of the sky itself: blue by day, violet at dusk, almost nothing at night.
        float dayAmount = LWMath.smoothstep(0f, 0.32f, sy);
        float duskAmount = LWMath.smoothstep(-0.2f, 0f, sy) * (1f - dayAmount);
        float skyDim = (1f - 0.4f * gloom) * (1f - 0.25f * weather);
        float ar = (0.6f * dayAmount + 0.2f * duskAmount + 0.03f) * skyDim;
        float ag = (0.65f * dayAmount + 0.17f * duskAmount + 0.04f) * skyDim;
        float ab = (0.74f * dayAmount + 0.25f * duskAmount + 0.07f) * skyDim;
        cloudLitR += (lr - cloudLitR) * k;
        cloudLitG += (lg - cloudLitG) * k;
        cloudLitB += (lb - cloudLitB) * k;
        cloudAmbientR += (ar - cloudAmbientR) * k;
        cloudAmbientG += (ag - cloudAmbientG) * k;
        cloudAmbientB += (ab - cloudAmbientB) * k;
        // From underneath a cloud is mostly sky-lit, with less of the sun getting through as it thickens.
        float through = 0.42f * (1f - 0.7f * cloudCoverage);
        cloudSeenR = LWMath.clamp01(cloudAmbientR * 1.05f + cloudLitR * through);
        cloudSeenG = LWMath.clamp01(cloudAmbientG * 1.05f + cloudLitG * through);
        cloudSeenB = LWMath.clamp01(cloudAmbientB * 1.05f + cloudLitB * through);

        float shadow = cloudsDrawn && c.cloudShadows ? e.daylight * (1f - 0.75f * weather) * (1f - e.cave) : 0f;
        cloudShadow += (shadow - cloudShadow) * k;

        boolean sky = c.enabled && c.enhancedSky && e.natural;
        float clear = (1f - LWMath.clamp01(e.precip * 2f)) * (1f - skyColorMix);
        skyColours += ((sky ? 1f : 0f) - skyColours) * k;
        starlight += ((sky ? LWMath.smoothstep(-0.02f, -0.3f, sy) * clear : 0f) - starlight) * k;

        // Northern lights: clear, cold nights only, and not every night.
        float activity = LWMath.smoothstep(0.42f, 0.7f, Noise.fbm1(e.worldTime / 20.0 / 500.0 + 7.0, 2, SEED_AURORA ^ e.salt));
        float cold = LWMath.smoothstep(4f, -6f, e.meanC + e.seasonOffsetC);
        float lights = sky && c.aurora ? LWMath.smoothstep(-0.1f, -0.35f, sy) * clear * cold * activity * (1f - weather) : 0f;
        if (forceAurora >= 0f && sky) {
            lights = forceAurora;
        }
        aurora += (lights - aurora) * k;

        // A rainbow needs sun from low behind you and rain (or air still full of it) ahead.
        float sunAngle = LWMath.smoothstep(0.03f, 0.2f, sy) * (1f - LWMath.smoothstep(0.5f, 0.66f, sy));
        float moisture = LWMath.clamp01(Math.max(e.rain * 3f, e.wetness * 2.2f - 0.2f));
        float bow = sky ? sunAngle * moisture * (1f - LWMath.smoothstep(0.45f, 0.95f, weather)) * (1f - e.storm) * e.openness : 0f;
        rainbow += (bow - rainbow) * (1f - (float) Math.exp(-0.25f * DT));
    }
}
