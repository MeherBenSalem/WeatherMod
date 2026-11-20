package dev.livingworld.client.audio.synth;

/**
 * Swamp frogs: sparse pulsed croaks. Each croak is a glottal-style impulse train ringing two formant band-passes
 * (200-900 Hz). Three call types: deep croak, two-syllable "ribbit" and a slow creaky rattle.
 */
final class FrogLayer {
    private static final int N = Dsp.BLOCK;
    private static final int COUNT = 4;
    private static final float GAIN = 0.10f;
    private static final float SEND = 0.2f;

    private final Frog[] frogs = new Frog[COUNT];
    private final float[] tmp = new float[N];

    FrogLayer(Rng rng) {
        for (int i = 0; i < COUNT; i++) {
            frogs[i] = new Frog(new Rng(rng.nextInt() * 131L + i));
        }
    }

    boolean render(float activity, float[] outL, float[] outR, float[] sendL, float[] sendR) {
        boolean any = false;
        for (int i = 0; i < COUNT; i++) {
            float presence = Dsp.clamp01((activity - i * 0.2f) * 5f);
            any |= frogs[i].render(activity, presence, tmp, outL, outR, sendL, sendR);
        }
        return any;
    }

    private static final class Frog {
        private final Rng rng;
        private final Svf f1 = new Svf(), f2 = new Svf();
        private int type, callsLeft, repeatsLeft, syllable, wait, tail;
        private boolean sounding;
        private float gl, gr, pitch, callGain;
        private float p, pInc, attack, pulsePh, inc0, inc1, a1, a2;

        Frog(Rng rng) {
            this.rng = rng;
            wait = (int) (Dsp.SR * (0.5f + 7f * rng.uni()));
        }

        private void relocate() {
            type = rng.below(3);
            float pan = rng.bi() * 0.9f;
            float dist = 0.4f + 0.6f * rng.uni();
            gl = Dsp.panL(pan) * dist * GAIN;
            gr = Dsp.panR(pan) * dist * GAIN;
            pitch = 0.88f + 0.24f * rng.uni();
            callsLeft = 2 + rng.below(5);
        }

        private void croak(float durSec, float rate0, float rate1, float form1, float q1, float form2, float q2,
                           float attackFrac) {
            p = 0f;
            pInc = 1f / (durSec * Dsp.SR);
            attack = attackFrac;
            inc0 = rate0 * pitch * Dsp.INV_SR;
            inc1 = rate1 * pitch * Dsp.INV_SR;
            pulsePh = 1f; // first pulse right away
            float fa = form1 * pitch, fb = form2 * pitch;
            f1.set(fa, q1);
            f2.set(fb, q2);
            // an impulse rings a raw band-pass at roughly w0 amplitude: normalise both formants
            a1 = 1f / (Dsp.TWO_PI * fa * Dsp.INV_SR);
            a2 = 0.5f / (Dsp.TWO_PI * fb * Dsp.INV_SR);
            sounding = true;
        }

        private void beginSyllable() {
            switch (type) {
                case 0: // deep croak
                    croak(0.35f + 0.25f * rng.uni(), 88f, 72f, 270f, 8f, 720f, 10f, 0.2f);
                    break;
                case 1: // ribbit: short low syllable, then a longer higher one
                    if (syllable == 0) {
                        croak(0.10f, 50f, 60f, 480f, 9f, 850f, 11f, 0.25f);
                    } else {
                        croak(0.16f, 58f, 72f, 560f, 9f, 900f, 11f, 0.2f);
                    }
                    break;
                default: // slow creaky rattle
                    croak(0.5f + 0.4f * rng.uni(), 23f, 30f, 600f, 10f, 880f, 12f, 0.15f);
                    break;
            }
        }

        boolean render(float activity, float presence, float[] tmp, float[] outL, float[] outR, float[] sendL,
                       float[] sendR) {
            if (!sounding && tail == 0) {
                if (wait >= N) {
                    wait -= N;
                    return false;
                }
                wait = 0;
                if (repeatsLeft == 0 && syllable == 0) {
                    if (presence <= 0f) {
                        wait = (int) (Dsp.SR * (0.5f + 2.5f * rng.uni()));
                        return false;
                    }
                    if (callsLeft <= 0) {
                        relocate();
                    }
                    callsLeft--;
                    repeatsLeft = type == 2 ? 1 + rng.below(2) : 1 + rng.below(3);
                    callGain = presence * (0.7f + 0.3f * rng.uni());
                }
                beginSyllable();
            }
            float ph = pulsePh, pp = p;
            for (int i = 0; i < N; i++) {
                float x = 0f;
                if (sounding) {
                    float env = pp < attack ? pp / attack : (1f - pp) / (1f - attack);
                    ph += inc0 + (inc1 - inc0) * pp;
                    if (ph >= 1f) {
                        ph -= 1f;
                        x = env * env * (0.8f + 0.4f * rng.uni());
                    }
                    pp += pInc;
                    if (pp >= 1f) {
                        sounding = false;
                        tail = 3072; // let the formants ring out
                    }
                } else if (tail > 0) {
                    tail--;
                }
                tmp[i] = f1.bp(x) * a1 + f2.bp(x) * a2;
            }
            pulsePh = ph;
            p = pp;
            float l = gl * callGain, r = gr * callGain;
            for (int i = 0; i < N; i++) {
                float s = tmp[i];
                outL[i] += s * l;
                outR[i] += s * r;
                sendL[i] += s * l * SEND;
                sendR[i] += s * r * SEND;
            }
            if (!sounding && tail == 0) {
                // schedule what comes next: second syllable, a repeat, or a long pause
                if (type == 1 && syllable == 0) {
                    syllable = 1;
                    wait = (int) (Dsp.SR * 0.02f);
                } else {
                    syllable = 0;
                    repeatsLeft--;
                    if (repeatsLeft > 0) {
                        wait = (int) (Dsp.SR * (0.3f + 0.4f * rng.uni()));
                    } else {
                        wait = (int) (Dsp.SR * (3f + 8f * rng.uni()) / (0.4f + activity));
                    }
                }
            }
            return true;
        }
    }
}
