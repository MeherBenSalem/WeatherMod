package dev.livingworld.client.audio.synth;

/**
 * Offline synthesis of short one-shot sounds (splashes, drips).
 * Called from the game thread: every call allocates and returns a fresh mono 16-bit buffer at
 * {@link AmbienceSynth#SAMPLE_RATE}. Output is fully determined by the arguments, and varies audibly with the seed.
 */
public final class OneShotSynth {
    private static final int SR = AmbienceSynth.SAMPLE_RATE;
    /** -12 dBFS: peak of the loudest one-shot. */
    private static final float STEP_PEAK = 0.2512f;

    private OneShotSynth() {
    }

    /** Water splash, size 0..1 (0.2 = a foot in a puddle, 1 = diving in). Length grows with size: 0.25-0.85 s. */
    public static short[] splash(float size, long seed) {
        float s = Dsp.clamp01(size);
        Rng r = new Rng(seed * 0xD1B54A32D192ED03L + 0x51EDL);
        float dur = 0.25f + 0.6f * s;
        float[] b = buf(dur);
        Svf lp = new Svf(), body = new Svf(), hp = new Svf();
        lp.set(520f - 260f * s, 0.8f);
        hp.set(2200f - 600f * s, 0.7f);
        Grains g = new Grains(r, 400f + 500f * s, 0.0015f, 2.2f);
        float thF = (140f - 70f * s) * (0.9f + 0.2f * r.uni());
        double thPh = 0;
        float spray = 0f;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) SR;
            float eI = ad(t, 0.003f, 0.02f + 0.04f * s);
            float eB = ad(t - 0.005f, 0.012f + 0.01f * s, 0.05f + 0.13f * s);
            float eS = ad(t - 0.008f, 0.01f + 0.03f * s, 0.045f + 0.2f * s);
            if ((i & 31) == 0) {
                body.set(400f + 500f * (float) Math.exp(-t / (0.05f + 0.1f * s)), 1f);
            }
            thPh += thF * (1f + 0.5f * (float) Math.exp(-t / 0.02f)) / SR;
            float th = (float) Math.sin(2.0 * Math.PI * thPh) * ad(t, 0.004f, 0.05f + 0.06f * s) * (0.5f + 0.4f * s);
            spray += 0.6f * (hp.hp(r.bi() * (0.35f + g.next()) * eS) - spray); // round off the top end (~6.5 kHz)
            b[i] = lp.lp(r.bi() * eI) * 2f + body.bpn(r.bi() * eB) * 2.2f + spray * (0.4f + 0.25f * s) + th;
        }
        int bubbles = 2 + (int) (10f * s);
        for (int k = 0; k < bubbles; k++) {
            float u = r.uni();
            float t = 0.02f + 0.6f * dur * u * Dsp.sqrt(u);
            float f0 = (1500f - 900f * s) * Dsp.exp2(-1.5f * r.uni());
            bloop(b, t, f0, f0 * (1.3f + 0.5f * r.uni()), 0.02f + 0.03f * r.uni(), (0.25f + 0.25f * r.uni()) * (1f - t / dur));
        }
        if (s > 0.3f) { // spray falling back on the surface
            int plips = (int) (12f * s);
            for (int k = 0; k < plips; k++) {
                float t = dur * (0.25f + 0.65f * r.uni());
                float f0 = 1500f * Dsp.exp2(1.4f * r.uni());
                bloop(b, t, f0, f0 * 1.4f, 0.008f + 0.008f * r.uni(), 0.08f + 0.12f * r.uni());
            }
        }
        return finish(b, STEP_PEAK * (0.45f + 0.55f * s));
    }

    /** A single water drip "bloop" (about 100-140 ms, peak around -14 dBFS). */
    public static short[] drip(long seed) {
        Rng r = new Rng(seed * 0xA24BAED4963EE407L + 0xD21FL);
        float dur = 0.10f + 0.04f * r.uni();
        float[] b = buf(dur);
        float f0 = 700f * Dsp.exp2(1.1f * r.uni());
        bloop(b, 0.002f, f0, f0 * (1.8f + 0.5f * r.uni()), dur * 0.85f, 1f);
        if (r.uni() < 0.5f) {
            bloop(b, 0.014f, f0 * 0.5f, f0 * 0.85f, dur * 0.7f, 0.4f);
        }
        Svf tick = new Svf();
        tick.set(3500f, 1.2f);
        int n = (int) (0.012f * SR);
        for (int i = 0; i < n && i < b.length; i++) {
            float t = i / (float) SR;
            b[i] += tick.bpn(r.bi() * (float) Math.exp(-t / 0.002f)) * 0.45f;
        }
        return finish(b, 0.2f);
    }

    // -------------------------------------------------------------------------------------------------- helpers

    private static float[] buf(float sec) {
        return new float[Math.max(256, (int) (sec * SR))];
    }

    /** Attack/decay envelope: linear rise over {@code attack}, then exponential decay. Zero before t = 0. */
    private static float ad(float t, float attack, float decay) {
        if (t <= 0f) {
            return 0f;
        }
        return t < attack ? t / attack : (float) Math.exp(-(t - attack) / decay);
    }

    private static float peak(float[] b) {
        float m = 0f;
        for (float v : b) {
            float a = Math.abs(v);
            if (a > m) {
                m = a;
            }
        }
        return m;
    }

    /** Adds a pitched water blip: sine gliding f0 to f1 over its length, fast attack, decaying to zero at the end. */
    private static void bloop(float[] b, float tSec, float f0, float f1, float lenSec, float amp) {
        int start = Math.max(0, (int) (tSec * SR));
        int n = Math.max(8, (int) (lenSec * SR));
        double ph = 0;
        for (int i = 0; i < n && start + i < b.length; i++) {
            float x = i / (float) n;
            ph += (f0 + (f1 - f0) * x) / SR;
            float env = Math.min(1f, i / (0.0015f * SR)) * (float) Math.exp(-4.5f * x) * (1f - x);
            b[start + i] += (float) Math.sin(2.0 * Math.PI * ph) * env * amp;
        }
    }

    /** Remove DC, fade the tail to zero, normalise to the requested peak and convert to 16-bit. */
    private static short[] finish(float[] b, float targetPeak) {
        int n = b.length;
        float k = 1f - Dsp.TWO_PI * 25f / SR;
        float x1 = 0f, y1 = 0f;
        for (int i = 0; i < n; i++) {
            float y = b[i] - x1 + k * y1;
            x1 = b[i];
            y1 = y;
            b[i] = y;
        }
        int fade = Math.min(n / 4, (int) (0.008f * SR));
        for (int i = 0; i < fade; i++) {
            b[n - 1 - i] *= 0.5f - 0.5f * (float) Math.cos(Math.PI * i / fade);
        }
        for (int i = 0; i < 8; i++) { // 0.2 ms fade-in: even the hardest tick starts from exactly zero
            b[i] *= i / 8f;
        }
        // Tame isolated grain spikes: anything beyond twice the 99.5th-percentile level is squeezed smoothly, so
        // peak-normalising below gives every material a comparable loudness instead of one set by a stray spike.
        float[] sorted = new float[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = Math.abs(b[i]);
        }
        java.util.Arrays.sort(sorted);
        float knee = 2f * sorted[(int) (0.995f * (n - 1))];
        if (knee > 1e-9f) {
            for (int i = 0; i < n; i++) {
                float a = Math.abs(b[i]);
                if (a > knee) {
                    float z = (a - knee) / knee;
                    a = knee * (1f + z / (1f + z));
                    b[i] = b[i] < 0f ? -a : a;
                }
            }
        }
        float pk = peak(b);
        float g = pk > 1e-6f ? targetPeak / pk : 0f;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            out[i] = (short) Math.round(b[i] * g * 32767f);
        }
        return out;
    }

    /** Poisson stream of tiny decaying grains: the crackle/sizzle amplitude texture of granular surfaces. */
    private static final class Grains {
        private final Rng r;
        private final float p, dec, amp;
        private float env, acc, thr;

        Grains(Rng r, float ratePerSec, float decaySec, float amp) {
            this.r = r;
            this.p = ratePerSec / SR;
            this.dec = (float) Math.exp(-1.0 / (decaySec * SR));
            this.amp = amp;
            this.thr = r.exp1();
        }

        float next() {
            acc += p;
            if (acc >= thr) {
                acc -= thr;
                thr = r.exp1();
                float u = r.uni();
                env += u * u * u * amp;
            }
            env *= dec;
            return env;
        }
    }
}
