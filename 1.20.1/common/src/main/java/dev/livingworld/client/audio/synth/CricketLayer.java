package dev.livingworld.client.audio.synth;

/**
 * Night crickets: six individuals, each a 4-5 kHz carrier gated by a 50-70 Hz pulse train. Four of them chirp
 * (3-5 pulses, a few chirps per second, with occasional long rests), two produce long quiet trills.
 */
final class CricketLayer {
    private static final int N = Dsp.BLOCK;
    private static final int COUNT = 6;
    private static final float GAIN = 0.11f;
    private static final float DUTY = 0.55f;

    private final Rng rng;
    private final float[] carPh = new float[COUNT], carInc = new float[COUNT], pulPh = new float[COUNT],
            pulInc = new float[COUNT], amp = new float[COUNT], gl = new float[COUNT], gr = new float[COUNT],
            presPrev = new float[COUNT], gapBase = new float[COUNT];
    private final int[] pulIdx = new int[COUNT], ppc = new int[COUNT], gap = new int[COUNT];
    private final boolean[] trill = new boolean[COUNT];

    CricketLayer(Rng rng) {
        this.rng = rng;
        for (int i = 0; i < COUNT; i++) {
            trill[i] = i == 2 || i == 5;
            carInc[i] = (trill[i] ? 4050f + 500f * rng.uni() : 4200f + 1000f * rng.uni()) * Dsp.INV_SR;
            pulInc[i] = (trill[i] ? 55f + 13f * rng.uni() : 50f + 20f * rng.uni()) * Dsp.INV_SR;
            ppc[i] = trill[i] ? 80 + rng.below(160) : 3 + rng.below(3);
            gapBase[i] = trill[i] ? 0.5f + 1.2f * rng.uni() : 0.26f + 0.3f * rng.uni();
            amp[i] = (trill[i] ? 0.35f : 1f) * (0.4f + 0.6f * rng.uni());
            place(i);
            gap[i] = rng.below(Dsp.SR);
        }
    }

    private void place(int i) {
        float pan = rng.bi() * 0.95f;
        gl[i] = Dsp.panL(pan);
        gr[i] = Dsp.panR(pan);
    }

    private int nextGap(int i) {
        if (rng.uni() < (trill[i] ? 0.15f : 0.04f)) {
            place(i); // long rest: the cricket may well start up again somewhere else
            return (int) (Dsp.SR * (2f + 5f * rng.uni()));
        }
        return (int) (Dsp.SR * gapBase[i] * (0.9f + 0.2f * rng.uni()));
    }

    boolean render(float activity, float[] outL, float[] outR) {
        boolean any = false;
        for (int c = 0; c < COUNT; c++) {
            float p1 = Dsp.clamp01((activity - c * 0.13f) * 5f) * (0.55f + 0.45f * activity) * amp[c] * GAIN;
            float p0 = presPrev[c];
            presPrev[c] = p1;
            if (p0 == 0f && p1 == 0f) {
                continue;
            }
            any = true;
            int gp = gap[c];
            if (gp >= N) {
                gap[c] = gp - N;
                continue;
            }
            float cp = carPh[c], ci = carInc[c], pp = pulPh[c], pi = pulInc[c];
            int idx = pulIdx[c], per = ppc[c];
            float l = gl[c], r = gr[c];
            float g = p0, gs = (p1 - p0) * (1f / N);
            for (int i = 0; i < N; i++, g += gs) {
                if (gp > 0) {
                    gp--;
                    continue;
                }
                pp += pi;
                if (pp >= 1f) {
                    pp -= 1f;
                    if (++idx >= per) {
                        idx = 0;
                        gp = nextGap(c);
                        l = gl[c];
                        r = gr[c];
                        continue;
                    }
                }
                if (pp < DUTY) {
                    cp += ci;
                    if (cp >= 1f) {
                        cp -= 1f;
                    }
                    float w = Dsp.sin(pp * (0.5f / DUTY));
                    float s = Dsp.sin(cp) * w * w * g * (idx == 0 ? 0.6f : 1f);
                    outL[i] += s * l;
                    outR[i] += s * r;
                }
            }
            gap[c] = gp;
            carPh[c] = cp;
            pulPh[c] = pp;
            pulIdx[c] = idx;
        }
        return any;
    }
}
