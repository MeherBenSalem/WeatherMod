package dev.livingworld.client.audio.synth;

/**
 * Beach surf: a main wave cycling every 6-10 s through swell (a low-pass slowly opening on a growing pink-noise
 * rumble), crash (fast broadband burst) and a receding, sizzling high-passed fizz; a second, smaller wave on its
 * own period overlaps it so the pattern never repeats exactly.
 */
final class SurfLayer {
    private static final int N = Dsp.BLOCK;
    private static final float GAIN = 0.28f;
    private static final float GRAIN_DEC = Dsp.decay(0.0015f);
    private static final float K_ENV = Dsp.kBlock(0.08f);
    private static final float FLOOR = 0.12f;

    private final Rng rng;
    private final Wave[] waves = {new Wave(), new Wave()};
    private final float[] nm = new float[N], nl = new float[N], nr = new float[N], nx = new float[N];
    private float prev;

    private static final class Wave {
        float p, pInc, size, panC, panSweep;
        final Pink pkL = new Pink(), pkR = new Pink();
        final Svf lowL = new Svf(), lowR = new Svf(), fizL = new Svf(), fizR = new Svf();
        float lowAmp = FLOOR, lowCut = 200f, fizAmp, fizCut = 2600f;
        float envL, envR, accL, accR, thrL = 1f, thrR = 1f, smL, smR, hpL, hpR;
        float glPrev = 0.7f, grPrev = 0.7f;
    }

    SurfLayer(Rng rng) {
        this.rng = rng;
        newWave(waves[0], 0);
        newWave(waves[1], 1);
        waves[0].p = 0.15f;
        waves[1].p = 0.62f;
    }

    private void newWave(Wave w, int k) {
        w.pInc = 1f / ((6f + 4f * rng.uni()) * Dsp.SR);
        w.size = (0.6f + 0.4f * rng.uni()) * (k == 0 ? 1f : 0.55f);
        w.panC = rng.bi() * (k == 0 ? 0.3f : 0.6f);
        w.panSweep = rng.bi() * 0.9f;
    }

    boolean render(float surf, float[] outL, float[] outR) {
        float g1 = surf * GAIN;
        float g0 = prev;
        prev = g1;
        if (g0 == 0f && g1 == 0f) {
            return false;
        }
        for (int k = 0; k < 2; k++) {
            Wave w = waves[k];
            w.p += w.pInc * N;
            if (w.p >= 1f) {
                w.p -= 1f;
                newWave(w, k);
            }
            float p = w.p;
            float tAmp, tCut, tFiz;
            if (p < 0.42f) { // swell: growing rumble, filter opening
                float s = p / 0.42f;
                tAmp = FLOOR + 0.42f * s * s;
                tCut = 160f + 1000f * s * s;
            } else if (p < 0.47f) { // crash
                float s = (p - 0.42f) / 0.05f;
                tAmp = 0.6f + 0.4f * s;
                tCut = 1160f + 3600f * s;
            } else { // collapse and wash back
                float s = p - 0.47f;
                tAmp = FLOOR + (1f - FLOOR) * (float) Math.exp(-s / 0.13f);
                tCut = 160f + 4600f * (float) Math.exp(-s / 0.2f);
            }
            if (p < 0.44f) {
                tFiz = 0f;
            } else if (p < 0.54f) {
                float s = (p - 0.44f) / 0.10f;
                tFiz = s * s * (3f - 2f * s);
            } else {
                float s = 1f - (p - 0.54f) / 0.44f;
                tFiz = s > 0f ? s * s : 0f;
            }
            tAmp = FLOOR + (tAmp - FLOOR) * w.size;
            tFiz *= w.size;
            w.lowAmp += K_ENV * (tAmp - w.lowAmp);
            w.lowCut += K_ENV * (tCut - w.lowCut);
            w.fizAmp += K_ENV * (tFiz - w.fizAmp);
            if (tFiz == 0f && w.fizAmp < 1e-5f) {
                w.fizAmp = 0f; // do not let it decay into denormals during the long swell
            }
            w.fizCut = 2600f + 2800f * Dsp.clamp01((p - 0.5f) * 2f);
            w.lowL.set(w.lowCut, 0.7f);
            w.lowR.set(w.lowCut * 1.06f, 0.7f);
            w.fizL.set(w.fizCut, 0.8f);
            w.fizR.set(w.fizCut * 1.05f, 0.8f);
            float la = w.lowAmp;
            float fa = w.fizAmp * 0.30f;
            float pan = w.panC + w.panSweep * (p - 0.5f);
            float gl1 = Dsp.panL(pan) * 1.2f, gr1 = Dsp.panR(pan) * 1.2f;
            float gl = w.glPrev, gr = w.grPrev;
            float gls = (gl1 - gl) * (1f / N), grs = (gr1 - gr) * (1f / N);
            w.glPrev = gl1;
            w.grPrev = gr1;

            rng.fill4(nm, nl, nr, nx, N);
            float pg = (300f + 500f * w.fizAmp) * Dsp.INV_SR;
            float el = w.envL, er = w.envR, al = w.accL, ar = w.accR, tl = w.thrL, tr = w.thrR, sl = w.smL, sr = w.smR;
            float hl = w.hpL, hr = w.hpR;
            boolean fizz = w.fizAmp > 1e-4f;
            Pink pkL = w.pkL, pkR = w.pkR;
            Svf lowL = w.lowL, lowR = w.lowR, fizL = w.fizL, fizR = w.fizR;
            float g = g0, gs = (g1 - g0) * (1f / N);
            for (int i = 0; i < N; i++) {
                float m = nm[i] * 0.6f;
                float yl = lowL.lp(pkL.next(m + nl[i] * 0.8f));
                float yr = lowR.lp(pkR.next(m + nr[i] * 0.8f));
                // keep the sub-bass of the rumble in check (one-pole high-pass around 70 Hz)
                hl += 0.01f * (yl - hl);
                hr += 0.01f * (yr - hr);
                yl = (yl - hl) * la;
                yr = (yr - hr) * la;
                if (fizz) {
                    al += pg;
                    if (al >= tl) {
                        al -= tl;
                        tl = rng.exp1();
                        float u = rng.uni();
                        el += u * u * 0.9f;
                    }
                    ar += pg;
                    if (ar >= tr) {
                        ar -= tr;
                        tr = rng.exp1();
                        float u = rng.uni();
                        er += u * u * 0.9f;
                    }
                    el = el * GRAIN_DEC + 1e-12f;
                    er = er * GRAIN_DEC + 1e-12f;
                    // sizzling foam: grainy high-passed noise, top end rounded off
                    sl += 0.6f * (fizL.hp(nx[i] * (0.5f + el)) - sl);
                    sr += 0.6f * (fizR.hp(nl[i] * (0.5f + er)) - sr);
                    yl += sl * fa;
                    yr += sr * fa;
                }
                outL[i] += yl * gl * g;
                outR[i] += yr * gr * g;
                gl += gls;
                gr += grs;
                g += gs;
            }
            w.envL = el;
            w.envR = er;
            w.accL = al;
            w.accR = ar;
            w.thrL = tl;
            w.thrR = tr;
            w.smL = sl;
            w.smR = sr;
            w.hpL = hl;
            w.hpR = hr;
        }
        return true;
    }
}
