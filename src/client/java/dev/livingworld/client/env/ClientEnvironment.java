package dev.livingworld.client.env;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.climate.BiomeProfile.Kind;
import dev.livingworld.climate.ClimateMath;
import dev.livingworld.climate.ClimateSampler;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.climate.WeatherModel;
import dev.livingworld.climate.WeatherType;
import dev.livingworld.climate.WindField;
import dev.livingworld.util.LWMath;
import dev.livingworld.util.Noise;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * The shared environmental state on the client. Once per tick it reads the world around the camera
 * (blended biomes, light, shelter, vanilla weather, server overrides) and produces one coherent,
 * smoothly varying description of the local conditions. Every effect system — precipitation, particles,
 * footprints, fog, post-processing, audio, camera feedback — reads from here and nowhere else, which is
 * what makes the effects react to each other instead of being isolated special cases.
 */
public final class ClientEnvironment {
    public static final ClientEnvironment INSTANCE = new ClientEnvironment();

    private static final float DT = 0.05f;
    private static final int SEED_LIGHTNING = 0x5F3A21;

    // ---- time ----------------------------------------------------------------------------------
    public long ticks;
    public long worldTime;
    public long timeOfDay;

    // ---- camera / dimension --------------------------------------------------------------------
    public boolean inWorld;
    public double camX, camY, camZ;
    public final BlockPos.Mutable camBlock = new BlockPos.Mutable();
    /** Overworld-like dimension: has a sky, sun and weather. */
    public boolean natural;
    public boolean nether;
    public boolean end;
    public int salt;

    // ---- blended biome (smoothed) --------------------------------------------------------------
    private final ClimateSampler.Sample target = new ClimateSampler.Sample();
    public final float[] kind = new float[Kind.VALUES.length];
    public Kind dominant = Kind.PLAINS;
    public float meanC = 15f;
    public float humidity = 0.5f;
    public float dustiness;
    public float foliage;
    public float windiness = 1f;
    public float rainFraction = 1f;
    public float snowFraction;
    public float dryFraction;

    // ---- shelter and light ---------------------------------------------------------------------
    public final ShelterProbe shelter = new ShelterProbe();
    public final ColumnCache columns = new ColumnCache();
    /** 1 under open sky .. 0 fully enclosed. */
    public float openness = 1f;
    /** Solid cover directly overhead, 0..1. */
    public float roof;
    /** Leaf cover directly overhead, 0..1. */
    public float canopy;
    /** How deep underground the camera feels, 0..1. */
    public float cave;
    public float skyLight = 1f;
    public float blockLight;
    /** 1 while the camera is under water. */
    public float submerged;
    /** Share of the falling precipitation that reaches the camera position, 0..1. */
    public float rainExposure = 1f;
    /** Height of the ground below the camera (smoothed), used as the base of ground mist. */
    public float groundY = 64f;

    // ---- climate -------------------------------------------------------------------------------
    public float ambientC = 15f;
    public float feelsLikeC = 15f;
    public float heatSources;

    // ---- wind ----------------------------------------------------------------------------------
    public final WindField wind = new WindField();
    /** How exposed the camera is to the wind, 0..1. */
    public float windExposure = 1f;
    /** Wind speed actually felt at the camera. */
    public float windLocal;

    // ---- weather -------------------------------------------------------------------------------
    public float rainGradient;
    public float thunderGradient;
    /** Total precipitation intensity at this location, 0..1.3 (already ramped). */
    public float precip;
    /** Rain and snow shares of {@link #precip}. */
    public float rain;
    public float snow;
    /** Sandstorm intensity 0..1. */
    public float sand;
    public float storm;
    public float cloudCover;
    /** Accumulated ground wetness 0..1; outlasts the rain and then evaporates. */
    public float wetness;
    /** Accumulated fresh snow 0..1 while it is snowing. */
    public float snowCover;
    public WeatherType weatherType = WeatherType.CLEAR;

    // ---- light ---------------------------------------------------------------------------------
    /** Sine of the sun's elevation. */
    public float sunElevation = 1f;
    /** 0 at night .. 1 in full daylight. */
    public float daylight = 1f;
    /** Unit direction toward the dominant sky light (sun by day, moon by night). */
    public float lightX, lightY = 1f, lightZ;
    /** Direct light actually arriving (clouds and night reduce it), 0..1. */
    public float lightStrength = 1f;
    /** Lightning flash, decays within a few ticks. */
    public float flash;
    /** Set for one tick when a distant (synthetic) lightning flash happens; consumed by the audio manager. */
    public float distantThunderDistance = -1f;
    public float distantThunderPan;

    // ---- overrides -----------------------------------------------------------------------------
    /** Latest state from the server (test commands, forecast, survival switch). */
    public final SimulationOverrides server = new SimulationOverrides();
    /** Client-only overrides from the /lw command, used when the server does not run the mod. */
    public final SimulationOverrides local = new SimulationOverrides();

    private ClientWorld lastWorld;
    private float precipTarget;
    private boolean wasForced;
    private int releaseTicks;

    private ClientEnvironment() {
    }

    public float kind(Kind k) {
        return kind[k.ordinal()];
    }

    /** Combined weight of the hot, dry biome families. */
    public float arid() {
        return kind(Kind.DESERT) + kind(Kind.BADLANDS) + 0.5f * kind(Kind.SAVANNA);
    }

    public float netherWeight() {
        return nether ? 1f : 0f;
    }

    private SimulationOverrides effective() {
        return local.weather != null || local.hasTemperature() || local.hasWind() ? local : server;
    }

    public void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        Entity viewer = client.getCameraEntity();
        if (world == null || viewer == null) {
            if (inWorld) {
                inWorld = false;
                lastWorld = null;
                columns.setWorld(null);
                server.reset();
                local.reset();
            }
            return;
        }
        boolean fresh = world != lastWorld;
        if (fresh) {
            lastWorld = world;
            columns.setWorld(world);
            natural = world.getDimension().hasSkyLight() && !world.getDimension().hasCeiling();
            nether = world.getDimension().ultrawarm();
            end = world.getRegistryKey() == net.minecraft.world.World.END;
            salt = WindField.saltOf(world.getRegistryKey().getValue().toString());
            wetness = 0f;
            snowCover = 0f;
            local.reset();
        }
        inWorld = true;
        ticks++;
        worldTime = world.getTime();
        timeOfDay = world.getTimeOfDay();

        Camera camera = client.gameRenderer.getCamera();
        Vec3d eye = camera.isReady() ? camera.getPos() : viewer.getEyePos();
        // Ground wetness and fresh snow describe the place you were in. After a long jump (teleport,
        // respawn) they no longer apply: start from what the weather here implies.
        double jumpX = eye.x - camX, jumpZ = eye.z - camZ;
        if (!fresh && jumpX * jumpX + jumpZ * jumpZ > 64.0 * 64.0) {
            wetness = natural && world.getRainGradient(1f) > 0.5f ? 0.6f : 0f;
            snowCover = 0f;
            fresh = true;
        }
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        camBlock.set(MathHelper.floor(camX), MathHelper.floor(camY), MathHelper.floor(camZ));
        int bx = camBlock.getX();
        int bz = camBlock.getZ();

        columns.tick(bx, bz);
        if (fresh || ticks % 20 == 7) {
            columns.computeSurfaceMix(bx, bz, 12);
        }

        // ---- biome blend ---------------------------------------------------------------------
        if (fresh || ticks % 10 == 0) {
            ClimateSampler.sampleBiomes(world, camBlock, target);
        }
        float kBiome = fresh ? 1f : 1f - (float) Math.exp(-0.9f * DT);
        for (int i = 0; i < kind.length; i++) {
            kind[i] += (target.kind[i] - kind[i]) * kBiome;
        }
        meanC += (target.meanC - meanC) * kBiome;
        humidity += (target.humidity - humidity) * kBiome;
        dustiness += (target.dustiness - dustiness) * kBiome;
        foliage += (target.foliage - foliage) * kBiome;
        windiness += (target.windiness - windiness) * kBiome;
        rainFraction += (target.rainFraction - rainFraction) * kBiome;
        snowFraction += (target.snowFraction - snowFraction) * kBiome;
        dryFraction += (target.dryFraction - dryFraction) * kBiome;
        dominant = target.dominant;

        // ---- shelter and light ---------------------------------------------------------------
        if (fresh || ticks % 5 == 2) {
            shelter.probe(world, viewer, eye);
        }
        float kShelter = fresh ? 1f : 1f - (float) Math.exp(-2.5f * DT);
        openness += (shelter.openness - openness) * kShelter;
        roof += (shelter.roof - roof) * kShelter;
        canopy += (shelter.canopy - canopy) * kShelter;
        float sky = world.getLightLevel(LightType.SKY, camBlock) / 15f;
        float block = world.getLightLevel(LightType.BLOCK, camBlock) / 15f;
        skyLight += (sky - skyLight) * kShelter;
        blockLight += (block - blockLight) * kShelter;
        float caveTarget = natural ? LWMath.smoothstep(0.55f, 0.05f, skyLight) * (1f - 0.6f * openness) : 0f;
        cave += (caveTarget - cave) * (fresh ? 1f : 1f - (float) Math.exp(-1.2f * DT));
        boolean underwater = camera.getSubmersionType() == CameraSubmersionType.WATER;
        submerged += ((underwater ? 1f : 0f) - submerged) * (fresh ? 1f : 0.3f);

        float top = columns.top(bx, bz);
        float exposureTarget = camY + 0.2 >= top ? 1f : 0f;
        if (exposureTarget == 0f && columns.hasCanopy(bx, bz) && camY + 0.2 >= columns.ground(bx, bz)) {
            exposureTarget = 0.3f;
        }
        rainExposure += (exposureTarget - rainExposure) * (fresh ? 1f : 1f - (float) Math.exp(-4f * DT));
        float ground = columns.ground(bx, bz);
        if (ground > ColumnCache.NO_SURFACE + 1f) {
            float base = cave > 0.5f ? (float) camY - 1.6f : Math.min(ground, (float) camY);
            groundY += (base - groundY) * (fresh ? 1f : 1f - (float) Math.exp(-1.5f * DT));
        }

        // ---- light direction -----------------------------------------------------------------
        float angle = world.getSkyAngleRadians(1f);
        float sx = -MathHelper.sin(angle);
        float sy = MathHelper.cos(angle);
        sunElevation = sy;
        daylight = natural ? LWMath.smoothstep(-0.12f, 0.25f, sy) : 0f;
        if (sy > -0.08f) {
            lightX = sx;
            lightY = Math.max(sy, 0.03f);
        } else {
            lightX = -sx;
            lightY = Math.max(-sy, 0.03f);
        }
        lightZ = 0.18f;
        float len = (float) Math.sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ);
        lightX /= len;
        lightY /= len;
        lightZ /= len;

        // ---- weather -------------------------------------------------------------------------
        ClientConfig config = ClientConfig.get();
        SimulationOverrides o = effective();
        rainGradient = natural ? world.getRainGradient(1f) : 0f;
        thunderGradient = natural ? world.getThunderGradient(1f) : 0f;
        float intensity = WeatherModel.intensity(rainGradient, thunderGradient, worldTime, salt) * (1f - dryFraction);
        float stormTarget = thunderGradient;
        float snowShare = rainFraction + snowFraction > 0.001f ? snowFraction / (rainFraction + snowFraction) : 0f;
        float forcedSand = -1f;
        float forcedWind = 0f;
        boolean forced = o.hasWeather(worldTime);
        if (forced) {
            WeatherType w = o.weather;
            intensity = w.precipitation * o.weatherIntensity;
            stormTarget = w.thunder;
            forcedSand = w.sand * o.weatherIntensity;
            forcedWind = w.wind * o.weatherIntensity;
            if (w.forcesSnow()) {
                snowShare = 1f;
            } else if (w.precipitation > 0f && snowShare < 0.5f) {
                snowShare = 0f;
            }
        }
        if (!config.blizzards) {
            intensity = Math.min(intensity, 0.9f - 0.3f * snowShare);
        }
        precipTarget = intensity;
        // A test override that has just been cleared lets go quickly instead of fading like real weather.
        if (wasForced && !forced) {
            releaseTicks = 240;
        }
        wasForced = forced;
        if (releaseTicks > 0) {
            releaseTicks--;
        }
        boolean quick = forced || releaseTicks > 0;
        // Showers build up over tens of seconds and die away a little faster than they came.
        precip = fresh ? precipTarget : LWMath.approach(precip, precipTarget, quick ? 0.35f : 0.055f, quick ? 0.4f : 0.11f, DT);
        if (precip < 0.002f && precipTarget == 0f) {
            precip = 0f;
        }
        snow = precip * snowShare;
        rain = precip - snow;
        storm = fresh ? stormTarget : LWMath.approach(storm, stormTarget, 0.3f, DT);

        float coverTarget = forced
                ? LWMath.clamp01(intensity * 1.3f + (forcedSand > 0f ? 0.25f * forcedSand : 0f))
                : WeatherModel.cloudCover(rainGradient, thunderGradient, config_forecast(o));
        cloudCover = fresh ? coverTarget : LWMath.approach(cloudCover, coverTarget, 0.18f, DT);

        // ---- wind ----------------------------------------------------------------------------
        float altitudeBoost = natural ? LWMath.clamp((float) (camY - 90.0) / 160f * 0.4f, 0f, 0.4f) : 0f;
        float userWind = config.windStrength;
        float overrideWind = o.hasWind() ? o.wind : -1f;
        // Sandstorms need sustained wind; test against the wind before the sandstorm's own boost to avoid latching.
        float calmBoost = WeatherModel.windBoost(precip, storm, 0f, snowShare);
        wind.update(worldTime, salt, calmBoost, windiness * userWind, altitudeBoost, overrideWind, camX, camZ);
        float sandTarget;
        if (forcedSand >= 0f) {
            sandTarget = forcedSand;
        } else if (natural && config.sandstorms) {
            sandTarget = WeatherModel.sandstorm(dustiness, dryFraction, wind.base * config.sandstormFrequency, rainGradient);
        } else {
            sandTarget = 0f;
        }
        sand = fresh ? sandTarget : LWMath.approach(sand, sandTarget, quick ? 0.3f : 0.045f, quick ? 0.4f : 0.07f, DT);
        if (sand < 0.002f) {
            sand = 0f;
        }
        float boost = Math.max(WeatherModel.windBoost(precip, storm, sand, snowShare), forcedWind);
        wind.update(worldTime, salt, boost, windiness * userWind, altitudeBoost, overrideWind, camX, camZ);

        float windExposureTarget = natural ? 0.12f + 0.88f * openness : 0.35f * openness + 0.1f;
        windExposure += (windExposureTarget - windExposure) * kShelter;
        windLocal = wind.speed * windExposure;

        // ---- accumulation --------------------------------------------------------------------
        float evaporation = 0.0035f + 0.012f * LWMath.smoothstep(12f, 34f, ambientC) * (0.4f + 0.6f * daylight * (1f - cloudCover))
                + 0.004f * wind.speed;
        wetness = LWMath.clamp01(wetness + (rain * 0.03f - (rain > 0.02f ? 0f : evaporation)) * DT);
        snowCover = LWMath.clamp01(snowCover + (snow * 0.02f - (snow > 0.02f ? 0f : 0.004f + 0.02f * LWMath.smoothstep(-1f, 8f, ambientC))) * DT);
        if (fresh && rainGradient > 0.5f && !forced) {
            wetness = Math.max(wetness, 0.7f * rainFraction);
        }

        // ---- temperature ---------------------------------------------------------------------
        if (fresh || ticks % 20 == 13) {
            heatSources = ClimateSampler.heatSources(world, viewer.getBlockPos(), 5);
        }
        lightStrength = natural ? (sy > -0.08f ? daylight : 0.22f) * (1f - 0.8f * cloudCover) * (1f - 0.7f * sand) : 0f;
        float sun = natural ? skyLight * (1f - cloudCover) * rainExposure : 0f;
        float ambientTarget = o.hasTemperature()
                ? o.temperatureC
                : ClimateMath.ambient(meanC, humidity, timeOfDay, camY, precip * skyLight, storm, sun, cave, heatSources,
                natural ? 1f : 0f);
        ambientC = fresh ? ambientTarget : LWMath.approach(ambientC, ambientTarget, 0.35f, DT);
        feelsLikeC = ClimateMath.feelsLike(ambientC, wind.speed, windExposure, 0f);

        weatherType = WeatherType.classify(rain, snow, storm, sand);

        // ---- lightning -----------------------------------------------------------------------
        flash *= 0.78f;
        if (flash < 0.01f) {
            flash = 0f;
        }
        if (world.getLightningTicksLeft() > 0) {
            flash = Math.max(flash, 1f);
        }
        distantThunderDistance = -1f;
        if (natural && storm > 0.4f && config.distantLightning) {
            // Deterministic in world time so every player sees the same sheet lightning.
            int t = (int) (worldTime & 0x7FFFFFFF);
            float roll = Noise.rand(t, salt ^ SEED_LIGHTNING);
            if (roll < 0.0028f * storm) {
                float distance = 0.45f + 0.55f * Noise.rand(t, salt ^ 0x1234);
                flash = Math.max(flash, 0.55f * (1.3f - distance));
                distantThunderDistance = distance;
                distantThunderPan = Noise.rand(t, salt ^ 0x4321) * 2f - 1f;
            }
        }
    }

    private int config_forecast(SimulationOverrides o) {
        return o.ticksUntilRain(worldTime);
    }

    /** Dry, loose, sun-baked ground colour of the surrounding biomes (desert sand vs. red badlands dust). */
    public void dustColor(float[] rgb) {
        float red = LWMath.clamp01(kind(Kind.BADLANDS) * 1.4f);
        rgb[0] = LWMath.lerp(0.84f, 0.74f, red);
        rgb[1] = LWMath.lerp(0.72f, 0.44f, red);
        rgb[2] = LWMath.lerp(0.48f, 0.27f, red);
    }
}
