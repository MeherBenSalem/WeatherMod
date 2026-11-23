package dev.livingworld.client.audio.synth;

import java.util.Arrays;

/**
 * Common machinery of one output bus: fixed-size internal blocks (so the DSP never depends on the caller's
 * {@code frames}), the "outdoor" sub-mix that is muffled by {@code openness}, the underwater filter, DC blocker,
 * soft limiter and conversion to 16-bit PCM.
 */
abstract class Bus {
    static final int N = Dsp.BLOCK;

    /** Limiter starts reducing gain above this level (about -6 dBFS). */
    private static final float LIMIT_THRESHOLD = 0.5f;
    /** Absolute output ceiling of the soft clipper: just under -3 dBFS. */
    private static final float CEILING = 0.70f;
    private static final float KNEE = 0.5f;
    private static final float LIMIT_ATTACK = 0.3f;
    private static final float LIMIT_RELEASE = 1f / (0.2f * Dsp.SR);
    private static final float DC_R = 1f - Dsp.TWO_PI * 12f / Dsp.SR;
    /** Blocks of "nothing playing" after which the bus outputs exact digital silence and stops doing work. */
    private static final int SILENT_HOLD = 24;

    /** Outdoor sub-mix (filtered and attenuated by openness). */
    final float[] outL = new float[N], outR = new float[N];
    /** Direct sub-mix (not affected by openness). */
    final float[] dirL = new float[N], dirR = new float[N];
    final EventQueue queue = new EventQueue(32);

    private final short[] pcm = new short[N * 2];
    private int pos = N;

    private final Svf opL = new Svf(), opR = new Svf(), uwL = new Svf(), uwR = new Svf();
    private float opGainPrev = 1f, opMixPrev, uwMixPrev, uwGainPrev = 1f;
    private float dcXL, dcYL, dcXR, dcYR, lim = 1f;
    private int silentBlocks = SILENT_HOLD;
    private boolean zeroed;

    /** Renders one block into outL/outR/dirL/dirR and ends by calling {@link #finish}. */
    abstract void renderBlock();

    final void render(short[] out, int frames) {
        int o = 0;
        while (frames > 0) {
            if (pos == N) {
                renderBlock();
                pos = 0;
            }
            int n = Math.min(frames, N - pos);
            System.arraycopy(pcm, pos * 2, out, o, n * 2);
            o += n * 2;
            pos += n;
            frames -= n;
        }
    }

    final void clear() {
        Arrays.fill(outL, 0f);
        Arrays.fill(outR, 0f);
        Arrays.fill(dirL, 0f);
        Arrays.fill(dirR, 0f);
    }

    /**
     * Mixes the two sub-mixes, applies the space filters and the output stage, and stores the block as PCM.
     *
     * @param any whether any layer produced signal this block
     */
    final void finish(boolean any, float openness, float underwater) {
        float og1 = 0.12f + 0.88f * openness;
        float om1 = Dsp.clamp01((1f - openness) * 5f);
        float um1 = Dsp.clamp01(underwater * 5f);
        float ug1 = 1f - 0.55f * underwater;
        float og0 = opGainPrev, om0 = opMixPrev, um0 = uwMixPrev, ug0 = uwGainPrev;
        opGainPrev = og1;
        opMixPrev = om1;
        uwMixPrev = um1;
        uwGainPrev = ug1;

        if (any) {
            silentBlocks = 0;
        } else if (silentBlocks < SILENT_HOLD) {
            silentBlocks++;
        }
        if (silentBlocks >= SILENT_HOLD) {
            if (!zeroed) {
                Arrays.fill(pcm, (short) 0);
                opL.reset();
                opR.reset();
                uwL.reset();
                uwR.reset();
                dcXL = dcYL = dcXR = dcYR = 0f;
                lim = 1f;
                zeroed = true;
            }
            return;
        }
        zeroed = false;

        final float[] l = outL, r = outR, dl = dirL, dr = dirR;
        final float inv = 1f / N;

        // ---- openness: outdoor sub-mix gets quieter and low-passed, then joins the direct sub-mix ----
        float og = og0, ogStep = (og1 - og0) * inv;
        if (om0 == 0f && om1 == 0f) {
            for (int i = 0; i < N; i++) {
                l[i] = l[i] * og + dl[i];
                r[i] = r[i] * og + dr[i];
                og += ogStep;
            }
            opL.reset();
            opR.reset();
        } else {
            float fc = 480f * Dsp.exp2(openness * 4.6f);
            opL.set(fc, 0.6f);
            opR.set(fc, 0.6f);
            float om = om0, omStep = (om1 - om0) * inv;
            for (int i = 0; i < N; i++) {
                float xl = l[i], xr = r[i];
                xl += om * (opL.lp(xl) - xl);
                xr += om * (opR.lp(xr) - xr);
                l[i] = xl * og + dl[i];
                r[i] = xr * og + dr[i];
                og += ogStep;
                om += omStep;
            }
            opL.flush();
            opR.flush();
        }

        // ---- underwater: heavy low-pass and attenuation of the whole bus ----
        if (um0 != 0f || um1 != 0f) {
            float fc = 9000f * Dsp.exp2(-4.7f * underwater);
            uwL.set(fc, 0.7f);
            uwR.set(fc, 0.7f);
            float um = um0, umStep = (um1 - um0) * inv;
            float ug = ug0, ugStep = (ug1 - ug0) * inv;
            for (int i = 0; i < N; i++) {
                float xl = l[i], xr = r[i];
                xl += um * (uwL.lp(xl) - xl);
                xr += um * (uwR.lp(xr) - xr);
                l[i] = xl * ug;
                r[i] = xr * ug;
                um += umStep;
                ug += ugStep;
            }
            uwL.flush();
            uwR.flush();
        } else {
            uwL.reset();
            uwR.reset();
        }

        // ---- DC blocker, limiter, soft clip, PCM ----
        float xl1 = dcXL, yl1 = dcYL, xr1 = dcXR, yr1 = dcYR, g = lim;
        final short[] p = pcm;
        for (int i = 0; i < N; i++) {
            float xl = l[i], xr = r[i];
            float yl = xl - xl1 + DC_R * yl1;
            float yr = xr - xr1 + DC_R * yr1;
            xl1 = xl;
            xr1 = xr;
            yl1 = yl;
            yr1 = yr;
            float al = yl < 0f ? -yl : yl, ar = yr < 0f ? -yr : yr;
            float pk = al > ar ? al : ar;
            if (!(pk < 64f)) {
                // NaN/Inf or absurd level: should be impossible, but never let it reach the device or stick.
                xl1 = yl1 = xr1 = yr1 = 0f;
                yl = 0f;
                yr = 0f;
                pk = 0f;
            }
            float tg = pk > LIMIT_THRESHOLD ? LIMIT_THRESHOLD / pk : 1f;
            g += (tg - g) * (tg < g ? LIMIT_ATTACK : LIMIT_RELEASE);
            p[2 * i] = toPcm(yl * g);
            p[2 * i + 1] = toPcm(yr * g);
        }
        if (yl1 < 1e-18f && yl1 > -1e-18f) {
            yl1 = 0f;
        }
        if (yr1 < 1e-18f && yr1 > -1e-18f) {
            yr1 = 0f;
        }
        dcXL = xl1;
        dcYL = yl1;
        dcXR = xr1;
        dcYR = yr1;
        lim = g;
    }

    /** Soft clip: linear below the knee, then a rational curve that approaches the ceiling but never reaches it. */
    private static short toPcm(float x) {
        float a = x < 0f ? -x : x;
        if (a > KNEE) {
            float z = (a - KNEE) * (1f / (CEILING - KNEE));
            a = KNEE + (CEILING - KNEE) * z / (1f + z);
            x = x < 0f ? -a : a;
        }
        return (short) (int) (x * 32767f + (x < 0f ? -0.5f : 0.5f));
    }
}
