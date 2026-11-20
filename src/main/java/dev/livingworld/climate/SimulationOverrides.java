package dev.livingworld.climate;

import net.minecraft.network.PacketByteBuf;

/**
 * The small amount of per-dimension state the server shares with clients: test overrides set through
 * {@code /livingworld}, the rain forecast and the survival-rule switch. Everything else is derived
 * locally from synchronised vanilla state, which keeps networking to one tiny packet on change.
 */
public final class SimulationOverrides {
    /** Simulated weather forced by a test command, or null to follow the world. */
    public WeatherType weather;
    /** Multiplier applied to the forced weather's intensity. */
    public float weatherIntensity = 1f;
    /** World time at which the forced weather ends. */
    public long weatherUntil;
    /** Forced ambient temperature in degrees Celsius, or NaN. */
    public float temperatureC = Float.NaN;
    /** Forced sustained wind strength, or a negative value. */
    public float wind = -1f;
    /** World time at which vanilla rain is scheduled to begin, or -1 if unknown / already raining. */
    public long rainStartTime = -1L;
    /** Whether the server applies heat and cold survival penalties. */
    public boolean gameplayEffects;

    public boolean hasWeather(long worldTime) {
        return weather != null && worldTime < weatherUntil;
    }

    public boolean hasTemperature() {
        return !Float.isNaN(temperatureC);
    }

    public boolean hasWind() {
        return wind >= 0f;
    }

    /** Ticks until the forecast rain begins, or -1. */
    public int ticksUntilRain(long worldTime) {
        if (rainStartTime < 0L) {
            return -1;
        }
        long d = rainStartTime - worldTime;
        return d < 0L || d > 12000L ? -1 : (int) d;
    }

    public void write(PacketByteBuf buf) {
        buf.writeByte(weather == null ? -1 : weather.ordinal());
        buf.writeFloat(weatherIntensity);
        buf.writeLong(weatherUntil);
        buf.writeFloat(temperatureC);
        buf.writeFloat(wind);
        buf.writeLong(rainStartTime);
        buf.writeBoolean(gameplayEffects);
    }

    public void read(PacketByteBuf buf) {
        int w = buf.readByte();
        WeatherType[] types = WeatherType.values();
        weather = w < 0 || w >= types.length ? null : types[w];
        weatherIntensity = buf.readFloat();
        weatherUntil = buf.readLong();
        temperatureC = buf.readFloat();
        wind = buf.readFloat();
        rainStartTime = buf.readLong();
        gameplayEffects = buf.readBoolean();
    }

    public void copyFrom(SimulationOverrides other) {
        weather = other.weather;
        weatherIntensity = other.weatherIntensity;
        weatherUntil = other.weatherUntil;
        temperatureC = other.temperatureC;
        wind = other.wind;
        rainStartTime = other.rainStartTime;
        gameplayEffects = other.gameplayEffects;
    }

    public void reset() {
        weather = null;
        weatherIntensity = 1f;
        weatherUntil = 0L;
        temperatureC = Float.NaN;
        wind = -1f;
        rainStartTime = -1L;
        gameplayEffects = false;
    }
}
