package dev.livingworld.client.audio.synth;

/** The weather bus: rain on all surfaces, wind-driven snow hiss and thunder. */
final class WeatherBus extends Bus {
    private static final float K = Dsp.kBlock(0.12f);
    private static final float K_SLOW = Dsp.kBlock(0.2f);

    private final AmbienceParams params;
    private final RainLayer rain;
    private final SnowLayer snow;
    private final ThunderLayer thunder;

    // smoothed parameters
    private float sRain, sLeaves, sStone, sWater, sWood, sSoft, sRoofed, sOpen = 1f, sUnder, sSnow, sWind, sGust, sCave;

    WeatherBus(AmbienceParams params, long seed) {
        this.params = params;
        Rng r = new Rng(seed ^ 0xB5297A4DL);
        rain = new RainLayer(new Rng(r.nextInt()));
        snow = new SnowLayer(new Rng(r.nextInt()));
        thunder = new ThunderLayer(new Rng(r.nextInt()));
    }

    @Override
    void renderBlock() {
        while (queue.poll() >= 0) {
            thunder.trigger(queue.outA, queue.outB, queue.outC);
        }

        AmbienceParams p = params;
        sRain = Dsp.smooth(sRain, Dsp.clamp(p.rain, 0f, 1.3f), K_SLOW);
        sLeaves = Dsp.smooth(sLeaves, Dsp.clamp01(p.rainOnLeaves), K);
        sStone = Dsp.smooth(sStone, Dsp.clamp01(p.rainOnStone), K);
        sWater = Dsp.smooth(sWater, Dsp.clamp01(p.rainOnWater), K);
        sWood = Dsp.smooth(sWood, Dsp.clamp01(p.rainOnWood), K);
        sSoft = Dsp.smooth(sSoft, Dsp.clamp01(p.rainOnSoft), K);
        sRoofed = Dsp.smooth(sRoofed, Dsp.clamp01(p.roofed), K_SLOW);
        sOpen = Dsp.smooth(sOpen, Dsp.clamp01(p.openness), K_SLOW);
        sUnder = Dsp.smooth(sUnder, Dsp.clamp01(p.underwater), K_SLOW);
        sSnow = Dsp.smooth(sSnow, Dsp.clamp(p.snowfall, 0f, 1.3f), K_SLOW);
        sWind = Dsp.smooth(sWind, Dsp.clamp(p.windSpeed, 0f, 1.5f), K);
        sGust = Dsp.smooth(sGust, Dsp.clamp01(p.windGust), K);

        sCave = Dsp.smooth(sCave, Dsp.clamp01(p.cave), K_SLOW);

        clear();
        // Open-air rain and snow go through the outdoor sub-mix (quieter and duller as openness drops). What you
        // hear of the rain under a roof is carried by the structure, so it is only mildly reduced by enclosure,
        // but it fades out completely with cave depth: nobody hears rain deep underground.
        float shallow = Dsp.sq(1f - sCave);
        float roofLevel = sRoofed * (0.6f + 0.4f * sOpen) * shallow;
        boolean any = rain.render(sRain, sLeaves, sStone, sWater, sWood, sSoft, sRoofed, roofLevel, outL, outR, dirL, dirR);
        any |= snow.render(sSnow, sWind, sGust, outL, outR);
        // thunder: the crack is an outdoor sound, the rumble passes through walls (and, weakly, through rock)
        any |= thunder.render(outL, outR, dirL, dirR, (0.5f + 0.5f * sOpen) * (1f - 0.8f * sCave));
        finish(any, sOpen, sUnder);
    }
}
