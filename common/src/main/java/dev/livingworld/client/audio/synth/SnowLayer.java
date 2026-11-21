package dev.livingworld.client.audio.synth;

/**
 * Wind-driven snow: a soft, wide hiss (broad band around 2-5 kHz over a duller airy layer) whose amplitude carries
 * a fine granular sizzle of ice grains. Level follows snowfall and the wind.
 */
final class SnowLayer {
    private static final int N = Dsp.BLOCK;
    private static final float GAIN = 0.12f;
    private static final float GRAIN_DEC = Dsp.decay(0.0007f);

    private final Rng rng;
    private final float[] na = new float[N], nb = new float[N];
    private final Svf bl = new Svf(), br = new Svf();
    private final Wander sheet, tone;
    private float airL, airR, envL, envR, accL, accR, thrL = 1f, thrR = 1f, prev;

    SnowLayer(Rng rng) {
        this.rng = rng;
        sheet = new Wander(rng, 0.9f);
        tone = new Wander(rng, 3f);
    }

    boolean render(float snow, float wind, float gust, float[] outL, float[] outR) {
        float e = wind * (0.6f + 0.7f * gust);
        if (e > 1.3f) {
            e = 1.3f;
        }
        float g1 = snow * (0.3f + 0.7f * e) * (0.85f + 0.15f * sheet.next(rng)) * GAIN;
        float g0 = prev;
        prev = g1;
        if (g0 == 0f && g1 == 0f) {
            return false;
        }
        rng.fill2(na, nb, N);
        float fc = 3000f * Dsp.exp2(0.25f * tone.next(rng)) * (1f + 0.15f * e);
        bl.set(fc, 0.7f);
        br.set(fc * 1.08f, 0.7f);
        float p = (500f + 1800f * Math.min(snow, 1.3f)) * Dsp.INV_SR;
        float el = envL, er = envR, al = accL, ar = accR, tl = thrL, tr = thrR, sl = airL, sr = airR;
        float g = g0, gs = (g1 - g0) * (1f / N);
        for (int i = 0; i < N; i++) {
            al += p;
            if (al >= tl) {
                al -= tl;
                tl = rng.exp1();
                float u = rng.uni();
                el += u * u * 0.6f;
            }
            ar += p;
            if (ar >= tr) {
                ar -= tr;
                tr = rng.exp1();
                float u = rng.uni();
                er += u * u * 0.6f;
            }
            el = el * GRAIN_DEC + 1e-12f;
            er = er * GRAIN_DEC + 1e-12f;
            float xl = na[i], xr = nb[i];
            sl += 0.12f * (xl - sl);
            sr += 0.12f * (xr - sr);
            float a = bl.bpn(xl * (0.7f + el)) + sl * 0.7f;
            float b = br.bpn(xr * (0.7f + er)) + sr * 0.7f;
            outL[i] += (a * 0.9f + b * 0.3f) * g;
            outR[i] += (b * 0.9f + a * 0.3f) * g;
            g += gs;
        }
        envL = el;
        envR = er;
        accL = al;
        accR = ar;
        thrL = tl;
        thrR = tr;
        airL = sl;
        airR = sr;
        return true;
    }
}
