package dev.livingworld.client.config;

import dev.livingworld.client.config.Opt.Category;
import dev.livingworld.config.ConfigIO;

/**
 * Every visual and audio option. Purely client-side: two players with completely different settings can
 * share a world. Stored in {@code config/livingworld-client.json}.
 */
public final class ClientConfig {
    private static final String FILE = "livingworld-client.json";
    private static ClientConfig instance;

    // ---- General ------------------------------------------------------------------------------
    @Opt(Category.GENERAL) public boolean enabled = true;
    @Opt(value = Category.GENERAL, max = 2f) public float effectIntensity = 1f;
    @Opt(Category.GENERAL) public boolean biomeAmbience = true;
    @Opt(Category.GENERAL) public boolean temperatureHud = false;
    @Opt(Category.GENERAL) public boolean fahrenheit = false;
    @Opt(Category.GENERAL) public boolean itemPhysics = true;

    // ---- Climate and temperature ----------------------------------------------------------------
    @Opt(Category.CLIMATE) public boolean heatHaze = true;
    @Opt(value = Category.CLIMATE, max = 2f) public float heatHazeStrength = 1f;
    @Opt(Category.CLIMATE) public boolean coldBreath = true;
    @Opt(Category.CLIMATE) public boolean temperatureTint = true;
    @Opt(Category.CLIMATE) public boolean dustDevils = true;

    // ---- Rain and storms ------------------------------------------------------------------------
    @Opt(Category.RAIN) public boolean customPrecipitation = true;
    @Opt(value = Category.RAIN, min = 0.2f, max = 2f) public float rainDensity = 1f;
    @Opt(Category.RAIN) public boolean rainSplashes = true;
    @Opt(Category.RAIN) public boolean wetSurfaces = true;
    @Opt(Category.RAIN) public boolean puddles = true;
    @Opt(Category.RAIN) public boolean stormClouds = true;
    @Opt(Category.RAIN) public boolean thunderDelay = true;
    @Opt(Category.RAIN) public boolean distantLightning = true;
    @Opt(value = Category.RAIN, max = 1f) public float lightningFlash = 0.7f;

    // ---- Snow and blizzards ---------------------------------------------------------------------
    @Opt(value = Category.SNOW, min = 0.2f, max = 2f) public float snowDensity = 1f;
    @Opt(Category.SNOW) public boolean blizzards = true;
    @Opt(Category.SNOW) public boolean snowCover = true;
    @Opt(Category.SNOW) public boolean iceCrystals = true;

    // ---- Sandstorms -----------------------------------------------------------------------------
    @Opt(Category.SAND) public boolean sandstorms = true;
    @Opt(value = Category.SAND, max = 2f) public float sandstormFrequency = 1f;
    @Opt(value = Category.SAND, min = 0.2f, max = 2f) public float sandDensity = 1f;
    @Opt(Category.SAND) public boolean blowingSand = true;

    // ---- Footprints and terrain -----------------------------------------------------------------
    @Opt(Category.FOOTPRINTS) public boolean footprints = true;
    @Opt(value = Category.FOOTPRINTS, min = 0.25f, max = 4f, step = 0.25f) public float footprintLifetime = 1f;
    @Opt(Category.FOOTPRINTS) public boolean mobFootprints = true;
    @Opt(Category.FOOTPRINTS) public boolean wetFootprints = true;
    @Opt(Category.FOOTPRINTS) public boolean stepParticles = true;
    @Opt(Category.FOOTPRINTS) public boolean litFootprints = true;

    // ---- Vegetation and wind --------------------------------------------------------------------
    @Opt(Category.VEGETATION) public boolean vegetationSway = true;
    @Opt(value = Category.VEGETATION, max = 2f) public float swayStrength = 1f;
    @Opt(Category.VEGETATION) public boolean vegetationBending = true;
    @Opt(Category.VEGETATION) public boolean fallingLeaves = true;
    @Opt(value = Category.VEGETATION, max = 1.6f) public float windStrength = 1f;

    // ---- Fog and atmosphere ---------------------------------------------------------------------
    @Opt(Category.FOG) public boolean atmosphericFog = true;
    @Opt(value = Category.FOG, max = 2f) public float fogDensity = 1f;
    @Opt(Category.FOG) public boolean groundMist = true;
    @Opt(Category.FOG) public boolean colorGrading = true;
    @Opt(value = Category.FOG, max = 1.5f) public float gradingStrength = 1f;
    @Opt(Category.FOG) public boolean exposureAdaptation = true;
    @Opt(Category.FOG) public boolean lightShafts = true;

    // ---- Water effects --------------------------------------------------------------------------
    @Opt(Category.WATER) public boolean waterRipples = true;
    @Opt(Category.WATER) public boolean entitySplashes = true;
    @Opt(Category.WATER) public boolean wetDrips = true;
    @Opt(Category.WATER) public boolean waterMist = true;
    @Opt(Category.WATER) public boolean waterShading = true;
    @Opt(Category.WATER) public boolean waterReflections = true;
    @Opt(value = Category.WATER, max = 2f) public float waveHeight = 1f;
    @Opt(Category.WATER) public boolean caustics = true;

    // ---- Sound and immersion --------------------------------------------------------------------
    @Opt(Category.SOUND) public boolean ambience = true;
    @Opt(value = Category.SOUND, max = 1.5f) public float ambienceVolume = 0.8f;
    @Opt(value = Category.SOUND, max = 1.5f) public float weatherVolume = 0.9f;
    @Opt(Category.SOUND) public boolean wildlife = true;
    @Opt(Category.SOUND) public boolean footstepLayers = true;
    @Opt(value = Category.SOUND, max = 1.5f) public float footstepVolume = 0.7f;
    @Opt(Category.SOUND) public boolean caveEcho = true;
    @Opt(Category.SOUND) public boolean replaceVanillaRain = true;

    // ---- Player visual effects ------------------------------------------------------------------
    @Opt(Category.PLAYER) public boolean shiver = true;
    @Opt(Category.PLAYER) public boolean heatSway = true;
    @Opt(Category.PLAYER) public boolean windBuffet = true;
    @Opt(value = Category.PLAYER, max = 1.5f) public float cameraEffects = 1f;
    @Opt(Category.PLAYER) public boolean frostOverlay = true;
    @Opt(Category.PLAYER) public boolean lensDroplets = true;

    // ---- Graphics and performance ---------------------------------------------------------------
    @Opt(Category.GRAPHICS) public QualityPreset preset = QualityPreset.BALANCED;
    @Opt(value = Category.GRAPHICS, min = 0.1f, max = 2f) public float particleDensity = 1f;
    @Opt(Category.GRAPHICS) public boolean adaptiveQuality = true;
    @Opt(value = Category.GRAPHICS, min = 30f, max = 144f, step = 1f, percent = false) public float targetFps = 60f;
    @Opt(value = Category.GRAPHICS, min = 16f, max = 48f, step = 1f, percent = false) public float effectDistance = 32f;
    @Opt(Category.GRAPHICS) public boolean bloom = true;
    @Opt(value = Category.GRAPHICS, max = 2f) public float bloomStrength = 1f;
    @Opt(Category.GRAPHICS) public boolean sunGlow = true;
    @Opt(value = Category.GRAPHICS, max = 1f) public float filmLook = 0.8f;
    @Opt(value = Category.GRAPHICS, max = 1f) public float vignette = 0.4f;

    // ---- Compatibility --------------------------------------------------------------------------
    @Opt(Category.COMPAT) public boolean postProcessing = true;
    @Opt(Category.COMPAT) public boolean yieldToShaderPacks = true;
    @Opt(Category.COMPAT) public boolean simpleRendering = false;
    @Opt(Category.COMPAT) public boolean overrideSkyColors = true;

    // ---- Accessibility --------------------------------------------------------------------------
    @Opt(Category.ACCESSIBILITY) public boolean reduceCameraMotion = false;
    @Opt(Category.ACCESSIBILITY) public boolean reduceFlashes = false;
    @Opt(Category.ACCESSIBILITY) public boolean reduceDistortion = false;
    @Opt(Category.ACCESSIBILITY) public boolean reduceFog = false;
    @Opt(Category.ACCESSIBILITY) public boolean reduceParticles = false;
    @Opt(Category.ACCESSIBILITY) public boolean screenOverlays = true;

    public static ClientConfig get() {
        if (instance == null) {
            instance = ConfigIO.load(FILE, ClientConfig.class, ClientConfig::new);
            if (instance.preset == null) {
                instance.preset = QualityPreset.BALANCED;
            }
        }
        return instance;
    }

    public void save() {
        ConfigIO.save(FILE, this);
    }

    // ---- Derived values that fold the accessibility switches in ---------------------------------

    /** Scale for all camera movement added by the mod. */
    public float cameraMotion() {
        return reduceCameraMotion ? 0f : cameraEffects;
    }

    public float flashScale() {
        return reduceFlashes ? Math.min(lightningFlash, 0.15f) : lightningFlash;
    }

    public float distortionScale() {
        return !heatHaze || reduceDistortion ? 0f : heatHazeStrength;
    }

    public float fogScale() {
        return !atmosphericFog ? 0f : reduceFog ? fogDensity * 0.35f : fogDensity;
    }

    public float particleScale() {
        return (reduceParticles ? 0.35f : 1f) * particleDensity * effectIntensity;
    }

    public boolean overlays() {
        return screenOverlays;
    }
}
