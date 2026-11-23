package dev.livingworld.client.audio.synth;

/**
 * One-off ambient events queued through {@link AmbienceSynth#triggerEvent}: ice crack, branch creak, lava pop,
 * distant gust and water plop. Fixed voice pools; a new event steals the oldest voice of its kind if all are busy.
 */
final class EventLayer {
    private static final int N = Dsp.BLOCK;
    static final int ICE_CRACK = AmbientEvent.ICE_CRACK.ordinal();
    static final int BRANCH_CREAK = AmbientEvent.BRANCH_CREAK.ordinal();
    static final int LAVA_POP = AmbientEvent.LAVA_POP.ordinal();
    static final int DISTANT_GUST = AmbientEvent.DISTANT_GUST.ordinal();
    static final int WATER_PLOP = AmbientEvent.WATER_PLOP.ordinal();

    /** Bits of the value returned by {@link #render}. */
    static final int ANY = 1, OUTDOOR_SEND = 2, CAVE_SEND = 4;

    private static final float ICE_GAIN = 0.20f, PLOP_GAIN = 0.19f, GUST_GAIN = 0.20f, CREAK_GAIN = 0.13f;

    private final Rng rng;
    private final NetherLayer nether;
    private final BlipVoice[] ice = new BlipVoice[3], plop = new BlipVoice[3];
    private final Gust[] gusts = {new Gust(), new Gust()};
    private final Creak[] creaks = {new Creak(), new Creak()};
    private int nextIce, nextPlop, nextGust, nextCreak;

    EventLayer(Rng rng, NetherLayer nether) {
        this.rng = rng;
        this.nether = nether;
        for (int i = 0; i < 3; i++) {
            ice[i] = new BlipVoice(new Rng(rng.nextInt() * 7L + i));
            plop[i] = new BlipVoice(new Rng(rng.nextInt() * 11L + i));
        }
    }

    void trigger(int type, float pan, float gain) {
        pan = Dsp.clamp(pan, -1f, 1f);
        gain = Dsp.clamp01(gain);
        if (gain <= 0f) {
            return;
        }
        if (type == ICE_CRACK) {
            BlipVoice v = ice[nextIce];
            nextIce = (nextIce + 1) % ice.length;
            float k = 0.85f + 0.3f * rng.uni();
            v.begin(pan, gain * ICE_GAIN);
            // dispersive "pew": highs arrive first, then a weaker echo of it across the ice, over a low thump
            v.tone(0, 3000f * k, 300f * k, 0.035f + 0.02f * rng.uni(), 0.6f, 0.001f, 0.07f, 0f);
            v.tone(1, 2600f * k, 280f * k, 0.045f, 0.22f, 0.002f, 0.08f, 0.06f + 0.04f * rng.uni());
            v.tone(2, 115f * k, 70f * k, 0.05f, 0.75f, 0.002f, 0.09f, 0f);
            v.noise(BlipVoice.HP, 2500f, 1f, 0.8f, 0.9f, 0.0002f, 0.004f, 0f);
        } else if (type == WATER_PLOP) {
            BlipVoice v = plop[nextPlop];
            nextPlop = (nextPlop + 1) % plop.length;
            float k = 0.8f + 0.4f * rng.uni();
            v.begin(pan, gain * PLOP_GAIN);
            v.tone(0, 260f * k, 800f * k, 0.03f, 1f, 0.004f, 0.05f, 0f);
            v.tone(1, 520f * k, 1300f * k, 0.02f, 0.35f, 0.002f, 0.03f, 0.065f + 0.03f * rng.uni());
            v.noise(BlipVoice.BP, 2800f, 1f, 1f, 0.5f, 0.001f, 0.02f, 0f);
        } else if (type == LAVA_POP) {
            nether.blub(pan, gain * 1.6f, 0.7f + 0.3f * rng.uni());
        } else if (type == DISTANT_GUST) {
            gusts[nextGust].start(rng, pan, gain * GUST_GAIN);
            nextGust = (nextGust + 1) % gusts.length;
        } else if (type == BRANCH_CREAK) {
            creaks[nextCreak].start(rng, pan, gain * CREAK_GAIN);
            nextCreak = (nextCreak + 1) % creaks.length;
        }
    }

    /** Outdoor events go to out (+ outdoor reverb send), water plops to dir (+ cave send). */
    int render(float[] outL, float[] outR, float[] dirL, float[] dirR, float[] obL, float[] obR, float[] cvL,
               float[] cvR, float caveSend) {
        int flags = 0;
        for (int i = 0; i < ice.length; i++) {
            if (ice[i].active) {
                ice[i].render(outL, outR, obL, obR, 0.35f);
                flags |= ANY | OUTDOOR_SEND;
            }
            if (plop[i].active) {
                plop[i].render(dirL, dirR, cvL, cvR, caveSend);
                flags |= ANY | (caveSend > 0f ? CAVE_SEND : 0);
            }
        }
        for (int i = 0; i < gusts.length; i++) {
            if (gusts[i].active) {
                gusts[i].render(rng, outL, outR);
                flags |= ANY;
            }
            if (creaks[i].active) {
                creaks[i].render(rng, outL, outR, obL, obR);
                flags |= ANY | OUTDOOR_SEND;
            }
        }
        return flags;
    }

    /** A far-off gust: a 3-4 s swell of band-passed pink noise whose band rises and falls with the swell. */
    private static final class Gust {
        boolean active;
        private final Pink pa = new Pink(), pb = new Pink();
        private final Svf fl = new Svf(), fr = new Svf();
        private float p, pInc, f0, gl, gr, envPrev;

        void start(Rng rng, float pan, float gain) {
            // restarting a sounding voice keeps its filter state and current level, so there is no click
            active = true;
            p = 0f;
            pInc = N / ((2.6f + 1.6f * rng.uni()) * Dsp.SR);
            f0 = 230f + 110f * rng.uni();
            gl = Dsp.panL(pan * 0.8f) * gain;
            gr = Dsp.panR(pan * 0.8f) * gain;
        }

        void render(Rng rng, float[] outL, float[] outR) {
            p += pInc;
            float e1;
            if (p >= 1f) {
                e1 = 0f;
                active = false;
            } else {
                float w = Dsp.sin(0.5f * Dsp.pow(p, 0.75f)); // skewed hump: quicker rise, longer fall
                e1 = w * w;
            }
            float q = 1.2f + e1;
            float fc = f0 * (1f + 1.1f * e1);
            fl.set(fc * 0.97f, q);
            fr.set(fc * 1.03f, q);
            float norm = 1f / Dsp.sqrt(q);
            float e = envPrev, es = (e1 - envPrev) * (1f / N);
            envPrev = e1;
            for (int i = 0; i < N; i++) {
                float a = pa.next(rng.bi()), b = pb.next(rng.bi());
                outL[i] += fl.bp(a * 0.85f + b * 0.4f) * norm * e * gl;
                outR[i] += fr.bp(b * 0.85f + a * 0.4f) * norm * e * gr;
                e += es;
            }
        }
    }

    /** A branch creaking: stick-slip impulse train (rate rising then falling) ringing three woody resonances. */
    private static final class Creak {
        boolean active;
        private final Svf r1 = new Svf(), r2 = new Svf(), r3 = new Svf();
        private final float[] tmp = new float[N];
        private float p, pInc, ph, jit, rate0, rate1, f1, f2, f3, gl, gr;
        private int tail;

        void start(Rng rng, float pan, float gain) {
            active = true;
            p = 0f;
            pInc = 1f / ((0.5f + 0.6f * rng.uni()) * Dsp.SR);
            ph = 1f;
            jit = 1f;
            rate0 = 20f + 15f * rng.uni();
            rate1 = 70f + 50f * rng.uni();
            float k = 0.8f + 0.5f * rng.uni();
            f1 = 420f * k;
            f2 = 1150f * k * (0.9f + 0.2f * rng.uni());
            f3 = 2300f * k * (0.9f + 0.2f * rng.uni());
            gl = Dsp.panL(pan) * gain;
            gr = Dsp.panR(pan) * gain;
            tail = 0;
        }

        void render(Rng rng, float[] outL, float[] outR, float[] sendL, float[] sendR) {
            float bend = 1f + 0.08f * p; // wood under growing tension: resonances creep upwards
            r1.set(f1 * bend, 14f);
            r2.set(f2 * bend, 18f);
            r3.set(f3 * bend, 12f);
            float a1 = 1f / (Dsp.TWO_PI * f1 * Dsp.INV_SR), a2 = 0.7f / (Dsp.TWO_PI * f2 * Dsp.INV_SR),
                    a3 = 0.35f / (Dsp.TWO_PI * f3 * Dsp.INV_SR);
            float pp = p, phase = ph;
            for (int i = 0; i < N; i++) {
                float x = 0f;
                if (pp < 1f) {
                    float hump = Dsp.sin(0.5f * pp);
                    float rate = rate0 + (rate1 - rate0) * hump * hump;
                    phase += rate * jit * Dsp.INV_SR;
                    if (phase >= 1f) {
                        phase -= 1f;
                        jit = 0.75f + 0.5f * rng.uni();
                        x = hump * (0.5f + 0.5f * rng.uni());
                    }
                    pp += pInc;
                    if (pp >= 1f) {
                        tail = 4096;
                    }
                } else if (tail > 0) {
                    tail--;
                }
                tmp[i] = r1.bp(x) * a1 + r2.bp(x) * a2 + r3.bp(x) * a3;
            }
            p = pp;
            ph = phase;
            for (int i = 0; i < N; i++) {
                float s = tmp[i];
                outL[i] += s * gl;
                outR[i] += s * gr;
                sendL[i] += s * gl * 0.25f;
                sendR[i] += s * gr * 0.25f;
            }
            if (pp >= 1f && tail == 0) {
                active = false;
            }
        }
    }
}
