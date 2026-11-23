package dev.livingworld.server;

import dev.livingworld.climate.BiomeProfile;
import dev.livingworld.climate.ClimateMath;
import dev.livingworld.climate.ClimateSampler;
import dev.livingworld.climate.Seasons;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.climate.WeatherModel;
import dev.livingworld.climate.WeatherType;
import dev.livingworld.climate.WindField;
import dev.livingworld.util.LWMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;

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
        /** Position in the year (0..1), or negative when seasons are off or the dimension has none. */
        public float yearPhase = -1f;
        final ClimateSampler.Sample sample = new ClimateSampler.Sample();
        final WindField wind = new WindField();
    }

    public static Reading read(ServerPlayer player, Reading out) {
        ServerLevel world = player.level();
        BlockPos pos = player.blockPosition();
        long time = world.getGameTime();
        SimulationOverrides overrides = ServerClimateSynchronizer.get(world);
        ClimateSampler.Sample s = out.sample;
        ClimateSampler.sampleBiomes(world, pos, s);

        boolean natural = world.dimensionType().hasSkyLight() && !world.dimensionType().hasCeiling();
        float skyLight = world.getBrightness(LightLayer.SKY, pos.above()) / 15f;
        float rainGradient = natural ? world.getRainLevel(1f) : 0f;
        float thunder = natural ? world.getThunderLevel(1f) : 0f;
        int salt = WindField.saltOf(world.dimension().identifier().toString());

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
        out.yearPhase = natural ? SeasonState.yearPhase(world.getServer()) : -1f;
        float seasonOffset = out.yearPhase >= 0f ? Seasons.temperatureOffsetC(out.yearPhase, s.seasonality) : 0f;
        float ambient = overrides.hasTemperature()
                ? overrides.temperatureC
                : ClimateMath.ambient(s.meanC + seasonOffset, s.humidity, world.getOverworldClockTime(), player.getY(), exposedRain, thunder,
                sun, cave, heat, natural ? 1f : 0f);
        float windExposure = natural ? skyLight : 0.3f;
        float wet = player.isInWaterOrRain() ? 1f : 0f;

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
