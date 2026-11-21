package dev.livingworld.config;

import dev.livingworld.util.LWMath;

/**
 * Gameplay-affecting rules. Owned by the server (or by the integrated server in single-player); stored in
 * {@code config/livingworld-server.json}. Everything visual lives in the client config instead.
 */
public final class ServerConfig {
    private static final String FILE = "livingworld-server.json";
    private static ServerConfig instance;

    /** Master switch for survival penalties from heat and cold. Off by default: immersion first. */
    public boolean temperatureEffects = false;
    /** Scales the extra hunger drain caused by extreme temperatures. */
    public float exhaustionScale = 1f;
    /** Whether prolonged extreme exposure slows the player slightly. */
    public boolean extremeSlowness = true;
    /** Tell clients when rain is about to start so clouds and wind can build up beforehand. */
    public boolean syncForecast = true;
    /**
     * How often real rain and thunderstorms occur relative to vanilla (1 = unchanged, 2 = twice as often).
     * This is the one setting that changes the real world weather schedule; it only shortens or lengthens
     * the dry spells between storms.
     */
    public float stormFrequency = 1f;
    /** Whether the year has seasons (temperature, snow instead of rain in winter, foliage colours on clients). */
    public boolean seasons = true;
    /** Length of one season in Minecraft days. Change it in-game with {@code /livingworld season length}. */
    public float seasonLengthDays = 8f;
    /**
     * Crops, stems, saplings, berry bushes and cocoa grow with the year: faster in spring, slowly in
     * autumn, hardly at all in winter. Off by default.
     */
    public boolean seasonalCrops = false;
    /**
     * Winter changes the world: snow settles and builds up while it snows where the season makes it cold
     * enough, and still water freezes over; both go again in the thaw. Off by default: it is the only
     * setting that makes the mod place and remove blocks. Only blocks it placed itself are ever removed.
     */
    public boolean seasonalSnowAndIce = false;
    /** How deep seasonal snow may build up, in layers (8 layers = one block). */
    public int maxSnowLayers = 2;
    /** How many columns near each player the seasons look at per tick: more makes snow and thaw faster. */
    public int seasonalChangeRate = 16;
    /** Permission level required for the world-affecting /livingworld sub-commands. */
    public int commandPermissionLevel = 2;

    public static ServerConfig get() {
        if (instance == null) {
            instance = ConfigIO.load(FILE, ServerConfig.class, ServerConfig::new);
            instance.sanitize();
        }
        return instance;
    }

    public void save() {
        sanitize();
        ConfigIO.save(FILE, this);
    }

    private void sanitize() {
        exhaustionScale = LWMath.clamp(exhaustionScale, 0f, 4f);
        stormFrequency = LWMath.clamp(stormFrequency, 0.1f, 10f);
        seasonLengthDays = Float.isNaN(seasonLengthDays) ? 8f : LWMath.clamp(seasonLengthDays, 0.25f, 400f);
        commandPermissionLevel = Math.max(0, Math.min(4, commandPermissionLevel));
        maxSnowLayers = Math.max(1, Math.min(8, maxSnowLayers));
        seasonalChangeRate = Math.max(1, Math.min(256, seasonalChangeRate));
    }
}
