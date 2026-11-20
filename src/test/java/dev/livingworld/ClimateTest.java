package dev.livingworld;

import dev.livingworld.climate.ClimateMath;
import dev.livingworld.climate.SimulationOverrides;
import dev.livingworld.climate.WeatherModel;
import dev.livingworld.climate.WeatherType;
import dev.livingworld.climate.WindField;
import dev.livingworld.util.Noise;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure simulation: temperature model, wind field, weather intensity, noise and the sync packet. */
class ClimateTest {
    private static final long NOON = 6000L;
    private static final long NIGHT = 18000L;
    private static final float DESERT_MEAN = ClimateMath.biomeMeanC(2.0f);
    private static final float DESERT_HUMIDITY = 0.04f;

    private static float air(float mean, float humidity, long time, double y, float rain, float sun, float cave) {
        return ClimateMath.ambient(mean, humidity, time, y, rain, 0f, sun, cave, 0f, 1f);
    }

    // ---- temperature -----------------------------------------------------------------------------

    @Test
    void biomeMeanRisesWithVanillaTemperature() {
        float previous = Float.NEGATIVE_INFINITY;
        for (float t = -0.8f; t <= 2.1f; t += 0.05f) {
            float c = ClimateMath.biomeMeanC(t);
            assertTrue(c >= previous - 1.0e-4f, "mean temperature must not fall as biome temperature rises");
            previous = c;
        }
        assertTrue(ClimateMath.biomeMeanC(0f) < 0f, "snowy plains average below freezing");
        assertTrue(ClimateMath.biomeMeanC(0.8f) > 12f && ClimateMath.biomeMeanC(0.8f) < 25f, "plains are temperate");
    }

    @Test
    void desertIsScorchingByDayAndColdAtNight() {
        float noon = air(DESERT_MEAN, DESERT_HUMIDITY, NOON, 64, 0f, 1f, 0f);
        float night = air(DESERT_MEAN, DESERT_HUMIDITY, NIGHT, 64, 0f, 0f, 0f);
        assertTrue(noon > 40f, "desert noon should exceed 40C but was " + noon);
        assertTrue(night < 20f, "desert night should drop below 20C but was " + night);
        assertTrue(noon - night > 22f, "dry air swings widely between day and night");
    }

    @Test
    void humidAirBarelySwings() {
        float jungle = ClimateMath.biomeMeanC(0.95f);
        float swing = air(jungle, 0.95f, NOON, 64, 0f, 0f, 0f) - air(jungle, 0.95f, NIGHT, 64, 0f, 0f, 0f);
        assertTrue(swing > 0f && swing < 9f, "jungle day/night difference should be small but was " + swing);
    }

    @Test
    void altitudeRainShadeAndDepthAllCool() {
        float mean = ClimateMath.biomeMeanC(0.8f);
        float base = air(mean, 0.5f, NOON, 64, 0f, 1f, 0f);
        assertTrue(base - air(mean, 0.5f, NOON, 200, 0f, 1f, 0f) > 8f, "a mountain top is much colder than the plain below");
        assertTrue(air(mean, 0.5f, NOON, 64, 1f, 1f, 0f) < base - 4f, "rain cools the air");
        assertTrue(air(mean, 0.5f, NOON, 64, 0f, 0f, 0f) < base, "shade is cooler than direct sun");
        float deep = air(DESERT_MEAN, DESERT_HUMIDITY, NOON, 20, 0f, 0f, 1f);
        assertEquals(ClimateMath.caveTemperature(20), deep, 1.0e-3f, "deep underground the surface climate no longer matters");
    }

    @Test
    void dimensionsWithoutASunHaveNoDailySwing() {
        float a = ClimateMath.ambient(50f, 0f, NOON, 64, 0f, 0f, 0f, 0f, 0f, 0f);
        float b = ClimateMath.ambient(50f, 0f, NIGHT, 120, 0f, 0f, 0f, 0f, 0f, 0f);
        assertEquals(50f, a, 1.0e-4f);
        assertEquals(a, b, 1.0e-4f);
        assertEquals(60f, ClimateMath.ambient(50f, 0f, NOON, 64, 0f, 0f, 0f, 0f, 10f, 0f), 1.0e-4f, "lava nearby adds heat");
    }

    @Test
    void feelsLikeAccountsForWindAndWetness() {
        assertEquals(25f, ClimateMath.feelsLike(25f, 1f, 1f, 0f), 1.0e-4f, "no wind chill in warm air");
        assertTrue(ClimateMath.feelsLike(-5f, 1f, 1f, 0f) < -10f, "a gale makes freezing air feel far colder");
        assertEquals(-5f, ClimateMath.feelsLike(-5f, 1f, 0f, 0f), 1.0e-4f, "shelter removes wind chill");
        assertTrue(ClimateMath.feelsLike(10f, 0f, 1f, 1f) < 8f, "being soaked is chilling");
    }

    @Test
    void visualThresholdsAreOrdered() {
        assertEquals(0f, ClimateMath.heatHaze(20f, 1f), 1.0e-4f);
        assertTrue(ClimateMath.heatHaze(45f, 1f) > 0.95f);
        assertTrue(ClimateMath.heatHaze(45f, 0f) < ClimateMath.heatHaze(45f, 1f), "shimmer is weaker out of the sun");
        assertEquals(0f, ClimateMath.breathVisibility(15f), 1.0e-4f);
        assertEquals(1f, ClimateMath.breathVisibility(-10f), 1.0e-4f);
        assertEquals(0f, ClimateMath.frost(0f), 1.0e-4f);
        assertTrue(ClimateMath.frost(-30f) > 0.99f);
        assertEquals(0f, ClimateMath.coldStress(15f) + ClimateMath.heatStress(15f), 1.0e-4f, "mild air stresses nobody");
    }

    // ---- wind ------------------------------------------------------------------------------------

    @Test
    void windIsDeterministicSoAllClientsAgree() {
        WindField a = new WindField();
        WindField b = new WindField();
        a.update(123456.5, 42, 0.2f, 1.1f, 0.05f, -1f, 100.0, -300.0);
        b.update(123456.5, 42, 0.2f, 1.1f, 0.05f, -1f, 100.0, -300.0);
        assertEquals(a.dirX, b.dirX);
        assertEquals(a.dirZ, b.dirZ);
        assertEquals(a.speed, b.speed);
        assertEquals(a.gust, b.gust);
        b.update(123456.5, 43, 0.2f, 1.1f, 0.05f, -1f, 100.0, -300.0);
        assertNotEquals(a.angle, b.angle, "dimensions have independent wind");
    }

    @Test
    void windVariesSmoothlyAndStaysInRange() {
        WindField w = new WindField();
        float lastSpeed = Float.NaN, lastAngle = Float.NaN;
        float min = Float.MAX_VALUE, max = 0f;
        for (int t = 0; t < 20 * 60 * 40; t++) {
            w.update(t, 7, 0f, 1f, 0f, -1f, 0.0, 0.0);
            assertEquals(1f, Math.hypot(w.dirX, w.dirZ), 1.0e-4, "direction is a unit vector");
            assertTrue(w.speed >= 0f && w.speed <= 1.6f * 1.4f + 1.0e-3f);
            if (!Float.isNaN(lastSpeed)) {
                assertTrue(Math.abs(w.speed - lastSpeed) < 0.02f, "speed must not jump between ticks");
                assertTrue(Math.abs(w.angle - lastAngle) < 0.02f, "direction must not jump between ticks");
            }
            lastSpeed = w.speed;
            lastAngle = w.angle;
            min = Math.min(min, w.base);
            max = Math.max(max, w.base);
        }
        assertTrue(min < 0.15f, "there are calm spells");
        assertTrue(max > 0.35f, "there are windy spells");
    }

    @Test
    void stormsAndOverridesDriveTheWind() {
        WindField calm = new WindField();
        WindField stormy = new WindField();
        calm.update(5000, 1, 0f, 1f, 0f, -1f, 0, 0);
        stormy.update(5000, 1, 0.6f, 1f, 0f, -1f, 0, 0);
        assertTrue(stormy.base > calm.base + 0.5f);
        WindField forced = new WindField();
        forced.update(5000, 1, 0.6f, 1.3f, 0.4f, 0.25f, 0, 0);
        assertEquals(0.25f, forced.base, 1.0e-6f, "a test override fixes the sustained strength");
    }

    // ---- weather ---------------------------------------------------------------------------------

    @Test
    void precipitationIntensityFollowsTheGradients() {
        assertEquals(0f, WeatherModel.intensity(0f, 0f, 1000, 3));
        double rain = 0.0, storm = 0.0;
        for (int t = 0; t < 200000; t += 37) {
            float r = WeatherModel.intensity(1f, 0f, t, 3);
            float s = WeatherModel.intensity(1f, 1f, t, 3);
            assertTrue(r > 0.05f && r <= 0.95f, "ordinary rain ranges from drizzle to heavy");
            assertTrue(s >= 0.7f && s <= 1.3f, "thunderstorms are always heavy");
            rain += r;
            storm += s;
        }
        assertTrue(storm > rain * 1.5, "thunderstorms are much wetter on average");
        assertEquals(0.5f * WeatherModel.intensity(1f, 0f, 777, 3), WeatherModel.intensity(0.5f, 0f, 777, 3), 1.0e-5f,
                "intensity ramps with the vanilla rain gradient");
    }

    @Test
    void cloudsGatherBeforeTheRain() {
        assertEquals(0f, WeatherModel.cloudCover(0f, 0f, -1));
        float far = WeatherModel.cloudCover(0f, 0f, 2300);
        float near = WeatherModel.cloudCover(0f, 0f, 200);
        assertTrue(far < 0.1f && near > 0.6f && near > far, "cover builds during the two minutes before rain");
        assertTrue(WeatherModel.cloudCover(1f, 1f, -1) > 0.95f);
    }

    @Test
    void sandstormsNeedDustDrynessAndWind() {
        assertEquals(0f, WeatherModel.sandstorm(0f, 1f, 1.2f, 1f), "no dust, no sandstorm");
        assertEquals(0f, WeatherModel.sandstorm(1f, 0f, 1.2f, 1f), "wet biomes do not raise sandstorms");
        assertEquals(0f, WeatherModel.sandstorm(1f, 1f, 0.3f, 0f), "a breeze is not enough");
        assertTrue(WeatherModel.sandstorm(1f, 1f, 1.0f, 0f) > 0.95f);
        assertTrue(WeatherModel.sandstorm(1f, 1f, 0.55f, 1f) > WeatherModel.sandstorm(1f, 1f, 0.55f, 0f),
                "a passing weather front makes sandstorms more likely");
    }

    @Test
    void weatherStatesAreNamedSensibly() {
        assertEquals(WeatherType.CLEAR, WeatherType.classify(0f, 0f, 0f, 0f));
        assertEquals(WeatherType.DRIZZLE, WeatherType.classify(0.1f, 0f, 0f, 0f));
        assertEquals(WeatherType.HEAVY_RAIN, WeatherType.classify(0.7f, 0f, 0f, 0f));
        assertEquals(WeatherType.THUNDERSTORM, WeatherType.classify(0.8f, 0f, 1f, 0f));
        assertEquals(WeatherType.EXTREME_STORM, WeatherType.classify(1.2f, 0f, 1f, 0f));
        assertEquals(WeatherType.BLIZZARD, WeatherType.classify(0f, 1f, 0f, 0f));
        assertEquals(WeatherType.SANDSTORM, WeatherType.classify(0f, 0f, 0f, 0.8f));
        assertEquals(WeatherType.SANDSTORM, WeatherType.byId("sandstorm"));
        assertNull(WeatherType.byId("frogs"));
    }

    // ---- noise and networking --------------------------------------------------------------------

    @Test
    void noiseIsBoundedAndContinuous() {
        float last = Noise.value1(0.0, 9);
        for (double x = 0.0; x < 200.0; x += 0.01) {
            float v = Noise.value1(x, 9);
            assertTrue(v >= 0f && v <= 1f);
            assertTrue(Math.abs(v - last) < 0.05f, "value noise must be continuous");
            last = v;
            float f = Noise.fbm3(x, x * 0.7, x * 1.3, 3, 5);
            assertTrue(f >= 0f && f <= 1f);
        }
        assertEquals(Noise.value3(1.5, 2.5, 3.5, 11), Noise.value3(1.5, 2.5, 3.5, 11));
        assertNotEquals(Noise.value3(1.5, 2.5, 3.5, 11), Noise.value3(1.5, 2.5, 3.5, 12));
    }

    @Test
    void overridesSurviveTheNetwork() {
        SimulationOverrides sent = new SimulationOverrides();
        sent.weather = WeatherType.BLIZZARD;
        sent.weatherIntensity = 1.3f;
        sent.weatherUntil = 987654321L;
        sent.temperatureC = -17.5f;
        sent.wind = 0.9f;
        sent.rainStartTime = 12345L;
        sent.gameplayEffects = true;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        sent.write(buf);
        SimulationOverrides received = new SimulationOverrides();
        received.read(buf);
        assertEquals(WeatherType.BLIZZARD, received.weather);
        assertEquals(1.3f, received.weatherIntensity);
        assertEquals(987654321L, received.weatherUntil);
        assertEquals(-17.5f, received.temperatureC);
        assertEquals(0.9f, received.wind);
        assertEquals(12345L, received.rainStartTime);
        assertTrue(received.gameplayEffects);
        assertEquals(0, buf.readableBytes(), "the packet is read completely");

        assertTrue(received.hasWeather(100L));
        assertFalse(received.hasWeather(987654321L), "forced weather expires");
        assertEquals(345, received.ticksUntilRain(12000L));
        assertEquals(-1, received.ticksUntilRain(13000L), "a forecast in the past is ignored");

        SimulationOverrides none = new SimulationOverrides();
        PacketByteBuf empty = new PacketByteBuf(Unpooled.buffer());
        none.write(empty);
        received.read(empty);
        assertNull(received.weather);
        assertFalse(received.hasTemperature());
        assertFalse(received.hasWind());
    }
}
