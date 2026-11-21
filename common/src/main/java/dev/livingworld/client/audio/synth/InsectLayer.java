package dev.livingworld.client.audio.synth;

/**
 * Daytime insect texture: three cicada-like voices (narrow noise band at 5-8 kHz, amplitude-pulsed at ~100-170 Hz,
 * each swelling and fading over many seconds) over a quieter, fluttering stereo buzz bed.
 */
final class InsectLayer {
    private static final int N = Dsp.BLOCK;
    private static final int CICADAS = 3;
    private static final float GAIN = 0.16f;
    private static final float[] CIC_FC = {5200f, 6500f, 7700f};
    private static final float[] CIC_AM = {104f, 138f, 167f};

    private final Rng rng;
    private final float[] na = new float[N], nb = new float[N];
    private final Svf[] cf = {new Svf(), new Svf(), new Svf()};
    private final float[] amPh = new float[CICADAS], lvl = new float[CICADAS], step = new float[CICADAS],
            gPrev = new float[CICADAS], gl = new float[CICADAS], gr = new float[CICADAS], tune = new float[CICADAS];
    private final int[] state = new int[CICADAS], left = new int[CICADAS];
    private final Svf bedL = new Svf(), bedR = new Svf();
    private final Wander bedAmp, bedTone;
    private float flL, flR, bedPrev;

    InsectLayer(Rng rng) {
        this.rng = rng;
        for (int i = 0; i < CICADAS; i++) {
            float pan = (i - 1) * 0.6f + rng.bi() * 0.25f;
            gl[i] = Dsp.panL(pan);
            gr[i] = Dsp.panR(pan);
            tune[i] = 0.93f + 0.14f * rng.uni();
            left[i] = Dsp.blocks(0.2f + 3f * rng.uni());
        }
        bedAmp = new Wander(rng, 3.5f);
        bedTone = new Wander(rng, 5f);
    }

    boolean render(float activity, float[] outL, float[] outR) {
        float bed1 = Dsp.pow(activity, 0.8f) * (0.75f + 0.25f * bedAmp.next(rng)) * 0.4f * GAIN;
        float bed0 = bedPrev;
        bedPrev = bed1;
        boolean bedOn = bed0 != 0f || bed1 != 0f;
        boolean any = bedOn;
        boolean filled = false;
        if (bedOn) {
            rng.fill2(na, nb, N);
            filled = true;
            float fc = 3800f * Dsp.exp2(0.15f * bedTone.next(rng));
            bedL.set(fc, 1.8f);
            bedR.set(fc * 1.09f, 1.8f);
            float pl = flL, pr = flR;
            float g = bed0, gs = (bed1 - bed0) * (1f / N);
            for (int i = 0; i < N; i++) {
                pl += 42f * Dsp.INV_SR;
                if (pl >= 1f) {
                    pl -= 1f;
                }
                pr += 47f * Dsp.INV_SR;
                if (pr >= 1f) {
                    pr -= 1f;
                }
                // flutter: cheap parabolic hump instead of a sine (0.2 .. 1.0)
                outL[i] += bedL.bpn(na[i]) * (0.2f + 3.2f * pl * (1f - pl)) * g;
                outR[i] += bedR.bpn(nb[i]) * (0.2f + 3.2f * pr * (1f - pr)) * g;
                g += gs;
            }
            flL = pl;
            flR = pr;
        }

        for (int c = 0; c < CICADAS; c++) {
            float presence = Dsp.clamp01((activity - c * 0.25f) * 4f);
            // slow swell state machine: rest -> rise -> hold -> fall
            if (--left[c] <= 0) {
                int s = state[c] = (state[c] + 1) & 3;
                switch (s) {
                    case 1:
                        left[c] = Dsp.blocks(2.5f + 2.5f * rng.uni());
                        step[c] = 1f / left[c];
                        break;
                    case 2:
                        left[c] = Dsp.blocks(1.5f + 3.5f * rng.uni());
                        step[c] = 0f;
                        break;
                    case 3:
                        left[c] = Dsp.blocks(1.5f + 2f * rng.uni());
                        step[c] = -1f / left[c];
                        break;
                    default:
                        left[c] = Dsp.blocks((0.5f + 4.5f * rng.uni()) / (0.3f + activity));
                        step[c] = 0f;
                        lvl[c] = 0f;
                        break;
                }
            }
            float lv = Dsp.clamp01(lvl[c] + step[c]);
            lvl[c] = lv;
            float g1 = lv * lv * presence * (0.5f + 0.5f * activity) * GAIN;
            float g0 = gPrev[c];
            gPrev[c] = g1;
            if (g0 == 0f && g1 == 0f) {
                continue;
            }
            any = true;
            if (!filled) {
                rng.fill2(na, nb, N);
                filled = true;
            }
            Svf f = cf[c];
            f.set(CIC_FC[c] * tune[c], 7f);
            float amInc = CIC_AM[c] * tune[c] * (0.85f + 0.2f * lv) * Dsp.INV_SR;
            float ph = amPh[c];
            float l = gl[c], r = gr[c];
            float[] src = (c & 1) == 0 ? na : nb;
            float g = g0, gs = (g1 - g0) * (1f / N);
            for (int i = 0; i < N; i++) {
                ph += amInc;
                if (ph >= 1f) {
                    ph -= 1f;
                }
                float am = 4f * ph * (1f - ph); // parabolic pulse 0..1..0 per cycle
                float s = f.bpn(src[i]) * (0.2f + 0.8f * am * am) * g * 2.2f;
                outL[i] += s * l;
                outR[i] += s * r;
                g += gs;
            }
            amPh[c] = ph;
        }
        return any;
    }
}
