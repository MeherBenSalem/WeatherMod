package dev.livingworld.client.audio.synth;

/**
 * Wind bed plus the three wind-driven textures: mountain howl (altitude), blowing-sand hiss and leaf rustle.
 * All four are scaled by one "effective wind" value built from windSpeed, windGust and internal turbulence.
 */
final class WindLayer {
    private static final int N = Dsp.BLOCK;
    private static final float WIND_GAIN = 0.145f;
    private static final float HOWL_GAIN = 0.18f;
    private static final float SAND_GAIN = 0.26f;
    private static final float FOLIAGE_GAIN = 0.52f;

    /** Centre frequencies (calm), base Q, Q added per unit of wind and relative level of the three body bands. */
    private static final float[] BASE = {165f, 310f, 520f};
    private static final float[] Q0 = {1.1f, 1.8f, 2.6f};
    private static final float[] Q1 = {1.2f, 2.4f, 4.0f};
    private static final float[] BAND_GAIN = {1.0f, 0.75f, 0.5f};
    private static final float[] FOL_BASE = {2500f, 4700f, 3300f, 6100f};
    private static final float[] FOL_TC = {0.009f, 0.005f, 0.012f, 0.006f};

    private final Rng rng;
    private final float[] wa = new float[N], wb = new float[N], wc = new float[N], wd = new float[N];
    private final float[] xl = new float[N], xr = new float[N], tl = new float[N], tr = new float[N];

    // wind body
    private final Pink pm = new Pink(), pl = new Pink(), pr = new Pink();
    private final Svf[] bl = {new Svf(), new Svf(), new Svf()}, br = {new Svf(), new Svf(), new Svf()};
    private final Wander[] drift = new Wander[3], bandAmp = new Wander[3];
    private final Wander turb, flutter;
    private float bodyL, bodyR, rumL, rumR, windPrev;

    // mountain howl
    private final Svf h1l = new Svf(), h1r = new Svf(), h2l = new Svf(), h2r = new Svf();
    private final Wander howlPitch, howlAmp;
    private float howlPrev;

    // sand
    private final Svf sandL = new Svf(), sandR = new Svf();
    private final Wander sandSheet, sandTone;
    private float sandEnvL, sandEnvR, sandAccL, sandAccR, sandThrL = 1f, sandThrR = 1f, sandPrev;
    private static final float SAND_DEC = Dsp.decay(0.002f);

    // foliage
    private final Svf[] fol = {new Svf(), new Svf(), new Svf(), new Svf()};
    private final Wander[] folTone = new Wander[4];
    private final Wander folAmp;
    private final float[] folEnv = new float[4], folSm = new float[4], folAcc = new float[4], folThr = {1f, 1f, 1f, 1f};
    private final float[] folDec = new float[4];
    private float folPrev;

    WindLayer(Rng rng) {
        this.rng = rng;
        for (int i = 0; i < 3; i++) {
            drift[i] = new Wander(rng, 2.5f + 1.5f * i);
            bandAmp[i] = new Wander(rng, 1.8f + 0.9f * i);
        }
        turb = new Wander(rng, 2.2f);
        flutter = new Wander(rng, 0.35f);
        howlPitch = new Wander(rng, 3.0f);
        howlAmp = new Wander(rng, 1.3f);
        sandSheet = new Wander(rng, 0.25f);
        sandTone = new Wander(rng, 2.0f);
        folAmp = new Wander(rng, 1.5f);
        for (int i = 0; i < 4; i++) {
            folTone[i] = new Wander(rng, 2.5f + 0.7f * i);
            folDec[i] = Dsp.decay(FOL_TC[i]);
        }
    }

    /** Adds to the given (outdoor) buffers. @return true if anything was written. */
    boolean render(float wind, float gust, float altitude, float sand, float foliage, float[] outL, float[] outR) {
        // effective wind strength, with our own turbulence on top of the game's gust envelope
        float e = wind * (0.6f + 0.7f * gust) * (1f + 0.22f * turb.next(rng) + 0.07f * flutter.next(rng));
        if (!(e > 0f)) {
            e = 0f;
        } else if (e > 2.2f) {
            e = 2.2f;
        }
        float ec = e > 1.6f ? 1.6f : e;

        float wind1 = Dsp.pow(e, 0.85f) / Dsp.sqrt(1f + 0.25f * e * e) * WIND_GAIN;
        float howl1 = altitude * Dsp.pow(ec, 1.3f) * (0.6f + 0.4f * howlAmp.next(rng)) * HOWL_GAIN;
        float sand1 = sand * (0.12f * Math.min(1f, e * 8f) + ec * Dsp.sqrt(ec))
                * (0.72f + 0.28f * sandSheet.next(rng)) * SAND_GAIN;
        float fol1 = foliage * Dsp.sqrt(Math.min(1.3f, e * 1.4f)) * (0.65f + 0.35f * folAmp.next(rng)) * FOLIAGE_GAIN;
        float wind0 = windPrev, howl0 = howlPrev, sand0 = sandPrev, fol0 = folPrev;
        windPrev = wind1;
        howlPrev = howl1;
        sandPrev = sand1;
        folPrev = fol1;

        boolean windOn = wind0 != 0f || wind1 != 0f;
        boolean howlOn = howl0 != 0f || howl1 != 0f;
        boolean sandOn = sand0 != 0f || sand1 != 0f;
        boolean folOn = fol0 != 0f || fol1 != 0f;
        if (!(windOn || howlOn || sandOn || folOn)) {
            return false;
        }

        rng.fill4(wa, wb, wc, wd, N);
        if (windOn || howlOn) {
            for (int i = 0; i < N; i++) {
                float m = pm.next(wa[i]) * 0.6f;
                xl[i] = m + pl.next(wb[i]) * 0.8f;
                xr[i] = m + pr.next(wc[i]) * 0.8f;
            }
        }
        if (windOn) {
            body(wind0, wind1, ec, outL, outR);
        }
        if (howlOn) {
            howl(howl0, howl1, gust, altitude, outL, outR);
        }
        if (sandOn) {
            sand(sand0, sand1, ec, outL, outR);
        }
        if (folOn) {
            foliage(fol0, fol1, ec, foliage, outL, outR);
        }
        return true;
    }

    private void body(float g0, float g1, float ec, float[] outL, float[] outR) {
        float c0, c1, c2;
        float[] c = tl; // scratch for the three band gains
        for (int b = 0; b < 3; b++) {
            float fc = BASE[b] * (1f + 0.85f * ec) * Dsp.exp2(0.4f * drift[b].next(rng));
            fc = Dsp.clamp(fc, 120f, 1100f);
            float q = Q0[b] + Q1[b] * ec;
            bl[b].set(fc * 0.985f, q);
            br[b].set(fc * 1.015f, q);
            c[b] = BAND_GAIN[b] * (0.75f + 0.25f * bandAmp[b].next(rng)) / Dsp.sqrt(q);
        }
        c0 = c[0];
        c1 = c[1];
        c2 = c[2];
        float kb = Dsp.TWO_PI * (250f + 500f * ec) * Dsp.INV_SR;
        float cr = 0.5f * ec * ec;
        Svf l0 = bl[0], l1 = bl[1], l2 = bl[2], r0 = br[0], r1 = br[1], r2 = br[2];
        float bL = bodyL, bR = bodyR, rL = rumL, rR = rumR;
        float g = g0, gs = (g1 - g0) * (1f / N);
        for (int i = 0; i < N; i++) {
            float l = xl[i], r = xr[i];
            bL += kb * (l - bL);
            bR += kb * (r - bR);
            rL += 0.008f * (l - rL);
            rR += 0.008f * (r - rR);
            outL[i] += (bL * 0.6f + rL * cr + l0.bp(l) * c0 + l1.bp(l) * c1 + l2.bp(l) * c2) * g;
            outR[i] += (bR * 0.6f + rR * cr + r0.bp(r) * c0 + r1.bp(r) * c1 + r2.bp(r) * c2) * g;
            g += gs;
        }
        bodyL = bL;
        bodyR = bR;
        rumL = rL;
        rumR = rR;
    }

    /** Hollow whistling resonance: very narrow band-passes on the wind noise, pitch wandering 520-1200 Hz. */
    private void howl(float g0, float g1, float gust, float altitude, float[] outL, float[] outR) {
        float pos = Dsp.clamp01(0.45f + 0.45f * howlPitch.next(rng) + 0.35f * (gust - 0.3f));
        float fh = 520f * Dsp.exp2(1.2f * pos);
        float q = 20f + 12f * altitude;
        h1l.set(fh * 0.994f, q);
        h1r.set(fh * 1.006f, q);
        h2l.set(fh * 1.507f, q * 1.3f);
        h2r.set(fh * 1.493f, q * 1.3f);
        float n1 = 1f / Dsp.sqrt(q), n2 = 0.4f / Dsp.sqrt(q * 1.3f);
        float g = g0, gs = (g1 - g0) * (1f / N);
        for (int i = 0; i < N; i++) {
            float l = xl[i], r = xr[i];
            outL[i] += (h1l.bp(l) * n1 + h2l.bp(l) * n2) * g;
            outR[i] += (h1r.bp(r) * n1 + h2r.bp(r) * n2) * g;
            g += gs;
        }
    }

    /** Dry high hiss with a fine granular (grain-impact) amplitude texture. */
    private void sand(float g0, float g1, float ec, float[] outL, float[] outR) {
        float p = (250f + 900f * ec) * Dsp.INV_SR;
        float fc = 5200f * Dsp.exp2(0.2f * sandTone.next(rng)) * (1f + 0.1f * ec);
        sandL.set(fc, 0.95f);
        sandR.set(fc * 1.07f, 0.95f);
        float el = sandEnvL, er = sandEnvR, al = sandAccL, ar = sandAccR, tL = sandThrL, tR = sandThrR;
        float g = g0, gs = (g1 - g0) * (1f / N);
        for (int i = 0; i < N; i++) {
            al += p;
            if (al >= tL) {
                al -= tL;
                tL = rng.exp1();
                float u = rng.uni();
                el += u * u * 0.7f;
            }
            ar += p;
            if (ar >= tR) {
                ar -= tR;
                tR = rng.exp1();
                float u = rng.uni();
                er += u * u * 0.7f;
            }
            el = el * SAND_DEC + 1e-12f;
            er = er * SAND_DEC + 1e-12f;
            float a = sandL.bpn(wb[i] * (0.35f + el));
            float b = sandR.bpn(wc[i] * (0.35f + er));
            outL[i] += (a * 0.9f + b * 0.25f) * g;
            outR[i] += (b * 0.9f + a * 0.25f) * g;
            g += gs;
        }
        sandEnvL = el;
        sandEnvR = er;
        sandAccL = al;
        sandAccR = ar;
        sandThrL = tL;
        sandThrR = tR;
    }

    /** Leaf rustle: four streams of short overlapping noise grains (2-7 kHz) whose density follows the wind. */
    private void foliage(float g0, float g1, float ec, float amount, float[] outL, float[] outR) {
        float rate = (15f + 420f * Dsp.pow(ec, 1.4f)) * (0.4f + 0.6f * amount);
        float p = rate * Dsp.INV_SR;
        for (int s = 0; s < 4; s++) {
            Svf f = fol[s];
            f.set(FOL_BASE[s] * Dsp.exp2(0.3f * folTone[s].next(rng)), 1.3f);
            float dec = folDec[s];
            // grain size shrinks as they start to overlap, so the level grows gently instead of exploding
            float a = 1f / (1.5f + rate * FOL_TC[s]);
            float env = folEnv[s], smo = folSm[s], acc = folAcc[s], thr = folThr[s];
            float[] src = s < 2 ? wd : wa;
            float[] dst = s < 2 ? tl : tr;
            if ((s & 1) == 0) {
                for (int i = 0; i < N; i++) {
                    acc += p;
                    if (acc >= thr) {
                        acc -= thr;
                        thr = rng.exp1();
                        env += (0.3f + 0.7f * rng.uni()) * a;
                    }
                    env = env * dec + 1e-12f;
                    smo += 0.015f * (env - smo); // ~1.5 ms attack: a soft rustle, not a tick
                    dst[i] = f.bpn(src[i] * smo);
                }
            } else {
                for (int i = 0; i < N; i++) {
                    acc += p;
                    if (acc >= thr) {
                        acc -= thr;
                        thr = rng.exp1();
                        env += (0.3f + 0.7f * rng.uni()) * a;
                    }
                    env = env * dec + 1e-12f;
                    smo += 0.015f * (env - smo);
                    dst[i] += f.bpn(src[i] * smo);
                }
            }
            folEnv[s] = env;
            folSm[s] = smo;
            folAcc[s] = acc;
            folThr[s] = thr;
        }
        float g = g0, gs = (g1 - g0) * (1f / N);
        for (int i = 0; i < N; i++) {
            float a = tl[i], b = tr[i];
            outL[i] += (a * 0.9f + b * 0.22f) * g;
            outR[i] += (b * 0.9f + a * 0.22f) * g;
            g += gs;
        }
    }
}
