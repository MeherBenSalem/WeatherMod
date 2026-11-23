package dev.livingworld.client.audio.synth;

import java.util.Arrays;

/**
 * Generic short "blip" voice: up to three gliding sine partials plus one filtered noise burst, each with its own
 * start delay, attack and exponential decay. Drips, plops, lava blubs and ice cracks are all configurations of it.
 */
final class BlipVoice {
    static final int TONES = 3;
    static final int BP = 0, HP = 1, LP = 2;
    private static final int N = Dsp.BLOCK;

    boolean active;

    private final Rng rng;
    private final float[] tmp = new float[N];
    private final float[] ph = new float[TONES], f = new float[TONES], fT = new float[TONES], fK = new float[TONES],
            amp = new float[TONES], att = new float[TONES], attInc = new float[TONES], env = new float[TONES],
            dec = new float[TONES];
    private final int[] delay = new int[TONES];
    private final boolean[] on = new boolean[TONES];

    private boolean nOn;
    private int nDelay, nMode;
    private float nAmp, nAtt, nAttInc, nEnv, nDec, nFc, nFcMul, nQ;
    private final Svf nf = new Svf();
    private float gl, gr;

    BlipVoice(Rng rng) {
        this.rng = rng;
    }

    /** Starts a new sound on this voice; add components with {@link #tone} and {@link #noise} afterwards. */
    void begin(float pan, float gain) {
        active = true;
        Arrays.fill(on, false);
        nOn = false;
        gl = Dsp.panL(pan) * gain;
        gr = Dsp.panR(pan) * gain;
    }

    /** Sine partial gliding from f0 towards f1 (Hz) with the given glide time constant. */
    void tone(int i, float f0, float f1, float glideSec, float a, float attackSec, float decaySec, float delaySec) {
        on[i] = true;
        ph[i] = 0f;
        f[i] = f0 * Dsp.INV_SR;
        fT[i] = f1 * Dsp.INV_SR;
        fK[i] = Dsp.kSample(Math.max(glideSec, 1e-4f));
        amp[i] = a;
        att[i] = 0f;
        attInc[i] = 1f / Math.max(1f, attackSec * Dsp.SR);
        env[i] = 1f;
        dec[i] = Dsp.decay(decaySec);
        delay[i] = (int) (delaySec * Dsp.SR);
    }

    /** Noise burst through a band-/high-/low-pass whose cutoff moves by {@code fcMulPerSec} per second. */
    void noise(int mode, float fc, float fcMulPerSec, float q, float a, float attackSec, float decaySec, float delaySec) {
        nOn = true;
        nMode = mode;
        nFc = fc;
        nFcMul = (float) Math.pow(fcMulPerSec, Dsp.BLOCK_SEC);
        nQ = q;
        nAmp = a;
        nAtt = 0f;
        nAttInc = 1f / Math.max(1f, attackSec * Dsp.SR);
        nEnv = 1f;
        nDec = Dsp.decay(decaySec);
        nDelay = (int) (delaySec * Dsp.SR);
        nf.reset();
    }

    /**
     * Adds the voice to out (and, scaled by {@code send}, to the send buffers).
     */
    void render(float[] outL, float[] outR, float[] sendL, float[] sendR, float send) {
        final float[] t = tmp;
        Arrays.fill(t, 0f);
        boolean alive = false;
        for (int k = 0; k < TONES; k++) {
            if (!on[k]) {
                continue;
            }
            int i0 = 0;
            if (delay[k] > 0) {
                if (delay[k] >= N) {
                    delay[k] -= N;
                    alive = true;
                    continue;
                }
                i0 = delay[k];
                delay[k] = 0;
            }
            float p = ph[k], fr = f[k], ft = fT[k], fk = fK[k], am = amp[k], a = att[k], ai = attInc[k], e = env[k],
                    d = dec[k];
            for (int i = i0; i < N; i++) {
                fr += (ft - fr) * fk;
                p += fr;
                if (p >= 1f) {
                    p -= 1f;
                }
                if (a < 1f) {
                    a += ai;
                    if (a > 1f) {
                        a = 1f;
                    }
                }
                e *= d;
                t[i] += Dsp.sin(p) * am * a * e;
            }
            ph[k] = p;
            f[k] = fr;
            att[k] = a;
            env[k] = e;
            if (e < 1e-4f) {
                on[k] = false;
            } else {
                alive = true;
            }
        }
        if (nOn) {
            int i0 = 0;
            boolean skip = false;
            if (nDelay > 0) {
                if (nDelay >= N) {
                    nDelay -= N;
                    alive = true;
                    skip = true;
                } else {
                    i0 = nDelay;
                    nDelay = 0;
                }
            }
            if (!skip) {
                nf.set(nFc, nQ);
                nFc *= nFcMul;
                float a = nAtt, ai = nAttInc, e = nEnv, d = nDec, am = nAmp;
                int mode = nMode;
                for (int i = i0; i < N; i++) {
                    if (a < 1f) {
                        a += ai;
                        if (a > 1f) {
                            a = 1f;
                        }
                    }
                    e *= d;
                    float x = rng.bi() * a * e;
                    float y = mode == BP ? nf.bpn(x) : (mode == HP ? nf.hp(x) : nf.lp(x));
                    t[i] += y * am;
                }
                nAtt = a;
                nEnv = e;
                if (e < 1e-4f) {
                    nOn = false;
                } else {
                    alive = true;
                }
            }
        }
        float l = gl, r = gr;
        if (send > 0f) {
            float sl = l * send, sr = r * send;
            for (int i = 0; i < N; i++) {
                float s = t[i];
                outL[i] += s * l;
                outR[i] += s * r;
                sendL[i] += s * sl;
                sendR[i] += s * sr;
            }
        } else {
            for (int i = 0; i < N; i++) {
                float s = t[i];
                outL[i] += s * l;
                outR[i] += s * r;
            }
        }
        active = alive;
    }
}
