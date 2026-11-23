package dev.livingworld.client.audio.synth;

/**
 * Plain parameter block. Written by the game thread, read by the audio thread.
 * All fields are volatile floats in the range 0..1 unless noted. Out-of-range and NaN values are clamped by the
 * synth, and every value is smoothed internally, so the game thread may write whatever it likes whenever it likes.
 */
public final class AmbienceParams {
    // ---- wind ----
    /** 0 calm, 0.3 breeze, 0.7 strong, 1.0 gale, up to 1.5 extreme. */
    public volatile float windSpeed;
    /** 0..1 instantaneous gust envelope (already smooth, changes over ~1-3 s). */
    public volatile float windGust;
    /** Leaves/grass nearby: leaf rustle layer driven by wind. */
    public volatile float foliage;
    /** 0 lowland .. 1 high exposed peak: hollow mountain howl. */
    public volatile float altitude;
    /** Blowing sand hiss (desert wind, sandstorm when windSpeed is high). */
    public volatile float sandiness;
    /** 0..1.3 soft granular hiss of wind-driven snow (blizzard). */
    public volatile float snowfall;

    // ---- rain ----
    /** 0 none, 0.15 drizzle, 0.4 moderate, 0.7 heavy, 1.0 torrential, up to 1.3 extreme. */
    public volatile float rain;
    /** Surface mix weights, roughly summing to 1 (soft = dirt/grass/sand). */
    public volatile float rainOnLeaves, rainOnStone, rainOnWater, rainOnWood, rainOnSoft;
    /** 0 standing in the open .. 1 hearing the rain from inside a building/cave mouth (muffled, low thrum). */
    public volatile float roofed;

    // ---- space ----
    /** 1 open sky .. 0 fully enclosed: outdoor layers get quieter and low-passed as this drops. Defaults to 1. */
    public volatile float openness = 1f;
    /** 0..1 deep cave bed: sub-bass drone, faint airy resonance. */
    public volatile float cave;
    /** 0..1 rate of water drips (cave ceiling, or leaves dripping after rain); drips echo when cave &gt; 0.3. */
    public volatile float drips;
    /** 0..1 everything heavily low-passed/attenuated. */
    public volatile float underwater;

    // ---- life ----
    /** Daytime birdsong activity (several distinct synthesized species, sparse, never continuous). */
    public volatile float birds;
    /** Daytime buzzing/cicada-like texture (jungle = high). */
    public volatile float insects;
    /** Night crickets. */
    public volatile float crickets;
    /** Swamp frogs (night). */
    public volatile float frogs;
    /** Ocean/beach waves: slow swells. */
    public volatile float surf;

    // ---- other dimensions ----
    /** Deep volcanic rumble + occasional lava blub. */
    public volatile float netherRumble;
    /** Eerie detuned drone with slow beating + thin airy whistle. */
    public volatile float endDrone;
}
