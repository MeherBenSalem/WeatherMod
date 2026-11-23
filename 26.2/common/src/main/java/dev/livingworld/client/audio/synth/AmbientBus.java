package dev.livingworld.client.audio.synth;

import java.util.Arrays;

/** The ambient bus: wind family, life, surf, cave, drips, nether, end and one-off ambient events. */
final class AmbientBus extends Bus {
    private static final float K = Dsp.kBlock(0.12f);
    private static final float K_SLOW = Dsp.kBlock(0.2f);

    private final AmbienceParams params;
    private final WindLayer wind;
    private final BirdLayer birds;
    private final InsectLayer insects;
    private final CricketLayer crickets;
    private final FrogLayer frogs;
    private final SurfLayer surf;
    private final CaveLayer cave;
    private final DripLayer drips;
    private final NetherLayer nether;
    private final EndLayer end;
    private final EventLayer events;
    private final Fdn outdoorVerb = new Fdn(1163, 1487, 1823, 2251, 0.9f, 5000f);
    private final Fdn caveVerb = new Fdn(2053, 2731, 3407, 4273, 2.2f, 3500f);
    private final float[] obL = new float[N], obR = new float[N], cvL = new float[N], cvR = new float[N];

    // smoothed parameters
    private float sWind, sGust, sFoliage, sAltitude, sSand, sOpen = 1f, sCave, sDrips, sUnder, sBirds, sInsects,
            sCrickets, sFrogs, sSurf, sNether, sEnd;

    AmbientBus(AmbienceParams params, long seed) {
        this.params = params;
        Rng r = new Rng(seed ^ 0x5DEECE66DL);
        wind = new WindLayer(new Rng(r.nextInt()));
        birds = new BirdLayer(new Rng(r.nextInt()));
        insects = new InsectLayer(new Rng(r.nextInt()));
        crickets = new CricketLayer(new Rng(r.nextInt()));
        frogs = new FrogLayer(new Rng(r.nextInt()));
        surf = new SurfLayer(new Rng(r.nextInt()));
        cave = new CaveLayer(new Rng(r.nextInt()));
        drips = new DripLayer(new Rng(r.nextInt()));
        nether = new NetherLayer(new Rng(r.nextInt()));
        end = new EndLayer(new Rng(r.nextInt()));
        events = new EventLayer(new Rng(r.nextInt()), nether);
    }

    @Override
    void renderBlock() {
        int ev;
        while ((ev = queue.poll()) >= 0) {
            events.trigger(ev, queue.outA, queue.outB);
        }

        AmbienceParams p = params;
        sWind = Dsp.smooth(sWind, Dsp.clamp(p.windSpeed, 0f, 1.5f), K);
        sGust = Dsp.smooth(sGust, Dsp.clamp01(p.windGust), K);
        sFoliage = Dsp.smooth(sFoliage, Dsp.clamp01(p.foliage), K);
        sAltitude = Dsp.smooth(sAltitude, Dsp.clamp01(p.altitude), K);
        sSand = Dsp.smooth(sSand, Dsp.clamp01(p.sandiness), K);
        sOpen = Dsp.smooth(sOpen, Dsp.clamp01(p.openness), K_SLOW);
        sCave = Dsp.smooth(sCave, Dsp.clamp01(p.cave), K_SLOW);
        sDrips = Dsp.smooth(sDrips, Dsp.clamp01(p.drips), K);
        sUnder = Dsp.smooth(sUnder, Dsp.clamp01(p.underwater), K_SLOW);
        sBirds = Dsp.smooth(sBirds, Dsp.clamp01(p.birds), K);
        sInsects = Dsp.smooth(sInsects, Dsp.clamp01(p.insects), K);
        sCrickets = Dsp.smooth(sCrickets, Dsp.clamp01(p.crickets), K);
        sFrogs = Dsp.smooth(sFrogs, Dsp.clamp01(p.frogs), K);
        sSurf = Dsp.smooth(sSurf, Dsp.clamp01(p.surf), K_SLOW);
        sNether = Dsp.smooth(sNether, Dsp.clamp01(p.netherRumble), K_SLOW);
        sEnd = Dsp.smooth(sEnd, Dsp.clamp01(p.endDrone), K_SLOW);

        clear();
        Arrays.fill(obL, 0f);
        Arrays.fill(obR, 0f);
        Arrays.fill(cvL, 0f);
        Arrays.fill(cvR, 0f);

        boolean any = wind.render(sWind, sGust, sAltitude, sSand, sFoliage, outL, outR);
        boolean outdoorSend = birds.render(sBirds, outL, outR, obL, obR);
        outdoorSend |= frogs.render(sFrogs, outL, outR, obL, obR);
        any |= outdoorSend;
        any |= insects.render(sInsects, outL, outR);
        any |= crickets.render(sCrickets, outL, outR);
        any |= surf.render(sSurf, outL, outR);
        any |= cave.render(sCave, dirL, dirR);
        float caveSend = Dsp.clamp01((sCave - 0.3f) / 0.4f) * 0.5f;
        boolean dripping = drips.render(sDrips, caveSend, dirL, dirR, cvL, cvR);
        any |= dripping;
        any |= nether.render(sNether, dirL, dirR);
        any |= end.render(sEnd, dirL, dirR);
        int flags = events.render(outL, outR, dirL, dirR, obL, obR, cvL, cvR, caveSend);
        any |= (flags & EventLayer.ANY) != 0;
        outdoorSend |= (flags & EventLayer.OUTDOOR_SEND) != 0;
        boolean caveIn = caveSend > 0f && (dripping || (flags & EventLayer.CAVE_SEND) != 0);

        any |= outdoorVerb.process(obL, obR, outdoorSend, outL, outR, N);
        any |= caveVerb.process(cvL, cvR, caveIn, dirL, dirR, N);

        finish(any, sOpen, sUnder);
    }
}
