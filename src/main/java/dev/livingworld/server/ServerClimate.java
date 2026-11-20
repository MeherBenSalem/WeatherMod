package dev.livingworld.server;

import dev.livingworld.climate.BiomeProfile;
import dev.livingworld.climate.ClimateMath;
import dev.livingworld.climate.ClimateSampler;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.climate.WeatherModel;
import dev.livingworld.climate.WeatherType;
import dev.livingworld.climate.WindField;
import dev.livingworld.util.LWMath;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/** Evaluates the climate a server-side player is standing in. Used by the survival rules and by /livingworld status. */
public final class ServerClimate {
    private ServerClimate() {
    }

    public static final class Reading {
        public float ambientC;
        public float feelsLikeC;
        public float windSpeed;
        public float humidity;
        public float precipitation;
        public BiomeProfile.Kind biome = BiomeProfile.Kind.PLAINS;
        public WeatherType weather = WeatherType.CLEAR;
        public boolean simulatedWeather;
        final ClimateSampler.Sample sample = new ClimateSampler.Sample();
        final WindField wind = new WindField();
    }

    public static Reading read(ServerPlayerEntity player, Reading out) {
        ServerWorld world = player.getServerWorld();
        BlockPos pos = player.getBlockPos();
        long time = world.getTime();
        SimulationOverrides overrides = ServerClimateSynchronizer.get(world);
        ClimateSampler.Sample s = out.sample;
        ClimateSampler.sampleBiomes(world, pos, s);

        boolean natural = world.getDimension().hasSkyLight() && !world.getDimension().hasCeiling();
        float skyLight = world.getLightLevel(LightType.SKY, pos.up()) / 15f;
        float rainGradient = natural ? world.getRainGradient(1f) : 0f;
        float thunder = natural ? world.getThunderGradient(1f) : 0f;
        int salt = WindField.saltOf(world.getRegistryKey().getValue().toString());

        float intensity = WeatherModel.intensity(rainGradient, thunder, time, salt) * (1f - s.dryFraction);
        float snowFraction = s.snowFraction;
        float extraWind = 0f;
        float sand;
        out.simulatedWeather = overrides.hasWeather(time);
        if (out.simulatedWeather) {
            WeatherType forced = overrides.weather;
            intensity = forced.precipitation * overrides.weatherIntensity;
            thunder = forced.thunder;
            sand = forced.sand * overrides.weatherIntensity;
            extraWind = forced.wind * overrides.weatherIntensity;
            if (forced.forcesSnow()) {
                snowFraction = 1f;
            }
        } else {
            float calmBase = 0.3f * s.windiness;
            sand = natural ? WeatherModel.sandstorm(s.dustiness, s.dryFraction, calmBase + 0.6f * rainGradient, rainGradient) : 0f;
        }

        float altitudeBoost = natural ? LWMath.clamp((float) (player.getY() - 90.0) / 160f * 0.4f, 0f, 0.4f) : 0f;
        float boost = Math.max(WeatherModel.windBoost(intensity, thunder, sand, snowFraction), extraWind);
        out.wind.update(time, salt, boost, s.windiness, altitudeBoost, overrides.wind, player.getX(), player.getZ());

        float exposedRain = intensity * skyLight;
        float cave = natural ? LWMath.smoothstep(0.75f, 0.1f, skyLight) : 0f;
        float sun = natural ? skyLight * (1f - LWMath.clamp01(rainGradient)) : 0f;
        float heat = ClimateSampler.heatSources(world, pos, 4);
        float ambient = overrides.hasTemperature()
                ? overrides.temperatureC
                : ClimateMath.ambient(s.meanC, s.humidity, world.getTimeOfDay(), player.getY(), exposedRain, thunder,
                sun, cave, heat, natural ? 1f : 0f);
        float windExposure = natural ? skyLight : 0.3f;
        float wet = player.isTouchingWaterOrRain() ? 1f : 0f;

        out.ambientC = ambient;
        out.windSpeed = out.wind.speed * (0.25f + 0.75f * windExposure);
        out.feelsLikeC = ClimateMath.feelsLike(ambient, out.wind.speed, windExposure, wet);
        out.humidity = Math.max(s.humidity, LWMath.clamp01(intensity) * 0.9f);
        out.precipitation = intensity;
        out.biome = s.dominant;
        float snow = intensity * snowFraction;
        out.weather = WeatherType.classify(intensity - snow, snow, thunder, sand);
        return out;
    }
}
