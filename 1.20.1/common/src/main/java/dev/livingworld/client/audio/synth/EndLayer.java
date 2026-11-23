package dev.livingworld.client.audio.synth;

/**
 * End ambience: a quiet cluster of detuned sine pairs (55-220 Hz) that beat slowly, at slightly different rates in
 * the left and right channels, plus a faint glassy whistle (very narrow noise bands with a slow pitch wander).
 * <p>
 * Each partial is the sum of a sine at f and a second one at f + beat. Instead of running two oscillators per
 * channel, one quadrature oscillator (sin, cos) per partial is mixed with slowly rotating coefficients:
 * {@code sin(wt) + sin(wt + phi) = sin(wt) * (1 + cos phi) + cos(wt) * sin phi}, where phi advances at the beat
 * rate and is different for L and R. Both channels share the un-detuned sine, so the mix stays mono-compatible.
 */
final class EndLayer {
    private static final int N = Dsp.BLOCK;
    private static final int P = 5;
    private static final float GAIN = 0.034f;
    private static final float[] FREQ = {55.0f, 82.6f, 110.3f, 146.9f, 220.4f};
    private static final float[] LEVEL = {1.0f, 0.8f, 0.62f, 0.45f, 0.3f};

    private final Rng rng;
    private final float[] na = new float[N], nb = new float[N], tl = new float[N], tr = new float[N];
    /** Coupled-form ("magic circle") quadrature oscillators: x += e*y; y -= e*x. No table lookups, stable amplitude. */
    private final float[] ox = new float[P], oy = new float[P], oe = new float[P];
    private final float[] phL = new float[P], phR = new float[P], incL = new float[P], incR = new float[P];
    private final float[] aL = new float[P], bL = new float[P], aR = new float[P], bR = new float[P];
    private final Wander[] ampL = new Wander[P], ampR = new Wander[P];
    private final Svf w1l = new Svf(), w1r = new Svf(), w2l = new Svf(), w2r = new Svf();
    private final Wander whistleTone, whistleAmp;
    private float prev;
    private int renorm;

    EndLayer(Rng rng) {
        this.rng = rng;
        for (int p = 0; p < P; p++) {
            oe[p] = (float) (2.0 * Math.sin(Math.PI * FREQ[p] / Dsp.SR));
            double ph = rng.uni() * 2.0 * Math.PI;
            ox[p] = (float) Math.sin(ph);
            oy[p] = (float) Math.cos(ph);
            float beat = 0.12f + 0.4f * rng.uni();
            float skew = (0.03f + 0.09f * rng.uni()) * (rng.uni() < 0.5f ? -1f : 1f);
            incL[p] = beat * Dsp.BLOCK_SEC;
            incR[p] = (beat + skew) * Dsp.BLOCK_SEC;
            phL[p] = rng.uni();
            phR[p] = rng.uni();
            ampL[p] = new Wander(rng, 9f + 2f * p);
            ampR[p] = new Wander(rng, 10f + 2f * p);
        }
        whistleTone = new Wander(rng, 8f);
        whistleAmp = new Wander(rng, 5f);
    }

    boolean render(float end, float[] outL, float[] outR) {
        float g1 = end * GAIN;
        float g0 = prev;
        prev = g1;
        if (g0 == 0f && g1 == 0f) {
            return false;
        }
        // ---- drone cluster: mixing coefficients move so slowly that one value per block is plenty ----
        for (int p = 0; p < P; p++) {
            float l = phL[p] + incL[p], r = phR[p] + incR[p];
            if (l >= 1f) {
                l -= 1f;
            }
            if (r >= 1f) {
                r -= 1f;
            }
            phL[p] = l;
            phR[p] = r;
            float lvL = LEVEL[p] * (0.75f + 0.25f * ampL[p].next(rng));
            float lvR = LEVEL[p] * (0.75f + 0.25f * ampR[p].next(rng));
            aL[p] = lvL * (1f + Dsp.sin(l + 0.25f));
            bL[p] = lvL * Dsp.sin(l);
            aR[p] = lvR * (1f + Dsp.sin(r + 0.25f));
            bR[p] = lvR * Dsp.sin(r);
        }
        float x0 = ox[0], y0 = oy[0], x1 = ox[1], y1 = oy[1], x2 = ox[2], y2 = oy[2], x3 = ox[3], y3 = oy[3],
                x4 = ox[4], y4 = oy[4];
        final float e0 = oe[0], e1 = oe[1], e2 = oe[2], e3 = oe[3], e4 = oe[4];
        final float al0 = aL[0], al1 = aL[1], al2 = aL[2], al3 = aL[3], al4 = aL[4];
        final float bl0 = bL[0], bl1 = bL[1], bl2 = bL[2], bl3 = bL[3], bl4 = bL[4];
        final float ar0 = aR[0], ar1 = aR[1], ar2 = aR[2], ar3 = aR[3], ar4 = aR[4];
        final float br0 = bR[0], br1 = bR[1], br2 = bR[2], br3 = bR[3], br4 = bR[4];
        for (int i = 0; i < N; i++) {
            x0 += e0 * y0;
            y0 -= e0 * x0;
            x1 += e1 * y1;
            y1 -= e1 * x1;
            x2 += e2 * y2;
            y2 -= e2 * x2;
            x3 += e3 * y3;
            y3 -= e3 * x3;
            x4 += e4 * y4;
            y4 -= e4 * x4;
            tl[i] = (x0 * al0 + y0 * bl0) + (x1 * al1 + y1 * bl1) + (x2 * al2 + y2 * bl2) + (x3 * al3 + y3 * bl3)
                    + (x4 * al4 + y4 * bl4);
            tr[i] = (x0 * ar0 + y0 * br0) + (x1 * ar1 + y1 * br1) + (x2 * ar2 + y2 * br2) + (x3 * ar3 + y3 * br3)
                    + (x4 * ar4 + y4 * br4);
        }
        ox[0] = x0;
        oy[0] = y0;
        ox[1] = x1;
        oy[1] = y1;
        ox[2] = x2;
        oy[2] = y2;
        ox[3] = x3;
        oy[3] = y3;
        ox[4] = x4;
        oy[4] = y4;
        // keep the oscillators at unit amplitude for ever: nudge one of them per block using the exact invariant
        int k = renorm;
        renorm = (k + 1) % P;
        float inv2 = ox[k] * ox[k] + oy[k] * oy[k] + oe[k] * ox[k] * oy[k];
        if (inv2 > 0.25f && inv2 < 4f) {
            float c = 1f + 0.02f * (1f - inv2); // gentle: at most a 1e-4-ish step, far below audibility
            ox[k] *= c;
            oy[k] *= c;
        } else {
            ox[k] = 0f;
            oy[k] = 1f;
        }

        // ---- glassy whistle ----
        rng.fill2(na, nb, N);
        float fw = 2700f * Dsp.exp2(0.3f * whistleTone.next(rng));
        float q = 45f;
        w1l.set(fw * 0.998f, q);
        w1r.set(fw * 1.002f, q);
        w2l.set(fw * 2.76f, q);
        w2r.set(fw * 2.756f, q);
        float wa = 0.08f * (0.55f + 0.45f * whistleAmp.next(rng));
        float g = g0, gs = (g1 - g0) * (1f / N);
        for (int i = 0; i < N; i++) {
            float l = na[i], r = nb[i];
            outL[i] += (tl[i] + (w1l.bp(l) + w2l.bp(l) * 0.3f) * wa) * g;
            outR[i] += (tr[i] + (w1r.bp(r) + w2r.bp(r) * 0.3f) * wa) * g;
            g += gs;
        }
        return true;
    }
}
