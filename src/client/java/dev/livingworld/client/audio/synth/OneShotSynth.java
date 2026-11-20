package dev.livingworld.client.audio.synth;

/**
 * Offline synthesis of short one-shot sounds (footsteps on weather-affected ground, splashes, drips).
 * Called from the game thread: every call allocates and returns a fresh mono 16-bit buffer at
 * {@link AmbienceSynth#SAMPLE_RATE}. Output is fully determined by the arguments, and varies audibly with the seed.
 */
public final class OneShotSynth {
    private static final int SR = AmbienceSynth.SAMPLE_RATE;
    /** -12 dBFS: peak of a footstep at intensity 1. */
    private static final float STEP_PEAK = 0.2512f;

    private OneShotSynth() {
    }

    /**
     * Mono signed 16-bit PCM at AmbienceSynth.SAMPLE_RATE, 60-400 ms.
     *
     * @param intensity 0..1 (0.2 sneaking, 0.5 walking, 0.8 sprinting, 1.0 landing from a fall)
     */
    public static short[] footstep(StepMaterial material, float intensity, long seed) {
        if (material == null) {
            material = StepMaterial.GRAVEL_DUST;
        }
        float in = Dsp.clamp01(intensity);
        Rng r = new Rng(seed * 0x9E3779B97F4A7C15L + material.ordinal() * 7919L + 1L);
        float[] b;
        switch (material) {
            case SNOW:
                b = snow(in, r);
                break;
            case SAND:
                b = sand(in, r);
                break;
            case MUD:
                b = mud(in, r);
                break;
            case PUDDLE:
                b = puddle(in, r);
                break;
            case WET_GRASS:
                b = wetGrass(in, r);
                break;
            case ICE:
                b = ice(in, r);
                break;
            default:
                b = gravel(in, r);
                break;
        }
        // hard steps and landings carry body weight: a short low thump under the surface sound
        if (in > 0.6f) {
            float pk = peak(b);
            float a = pk * 0.9f * (in - 0.6f) / 0.4f;
            float f = 78f * (0.9f + 0.2f * r.uni());
            double ph = 0;
            for (int i = 0; i < b.length; i++) {
                float t = i / (float) SR;
                ph += f * (1f + 0.6f * (float) Math.exp(-t / 0.012f)) / SR;
                b[i] += (float) Math.sin(2.0 * Math.PI * ph) * ad(t, 0.003f, 0.035f) * a;
            }
        }
        // soft steps are duller
        float k = 1f - (float) Math.exp(-2.0 * Math.PI * (2500f + 13500f * in) / SR);
        float s = 0f;
        for (int i = 0; i < b.length; i++) {
            s += k * (b[i] - s);
            b[i] = s;
        }
        return finish(b, STEP_PEAK * (0.18f + 0.82f * Dsp.pow(in, 1.2f)));
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

    // ------------------------------------------------------------------------------------------------ footsteps

    /** Compressed crunch: dense crackle grains in a mid band, heel then toe, over a dull compression thud. */
    private static float[] snow(float in, Rng r) {
        float dur = 0.15f + 0.07f * in + 0.03f * r.uni();
        float[] b = buf(dur);
        Svf body = new Svf(), crisp = new Svf(), low = new Svf(), creak = new Svf();
        body.set(1250f * (0.9f + 0.2f * r.uni()), 1.1f);
        crisp.set(3300f * (0.9f + 0.2f * r.uni()), 1.5f);
        low.set(320f, 0.8f);
        float creakF = 850f * (0.85f + 0.3f * r.uni());
        creak.set(creakF, 4f);
        float creakNorm = 1f / (Dsp.TWO_PI * creakF / SR);
        float c1 = 0.24f * dur, w1 = 0.15f * dur, c2 = 0.62f * dur, w2 = 0.24f * dur;
        Grains g = new Grains(r, 700f + 500f * in, 0.0016f, 3f);
        float cph = 0f, cjit = 1f;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) SR;
            float e1 = gauss((t - c1) / w1), e2 = 0.85f * gauss((t - c2) / w2);
            float env = e1 + e2;
            float n = r.bi() * (0.22f + g.next()) * env;
            float y = body.bpn(n) + crisp.bpn(n) * (0.45f + 0.35f * in) + low.lp(r.bi() * env) * (0.15f + 0.3f * in);
            // stick-slip squeak of snow being compressed: irregular pulse train into a resonance
            cph += (90f + 110f * e1 + 60f * e2) * cjit / SR;
            float x = 0f;
            if (cph >= 1f) {
                cph -= 1f;
                cjit = 0.7f + 0.6f * r.uni();
                x = env * (0.5f + 0.5f * r.uni());
            }
            b[i] = y + creak.bp(x) * creakNorm * 0.22f;
        }
        return b;
    }

    /** Soft dry "shff": short, high-passed, finely grainy, with a second softer slide as the sand gives way. */
    private static float[] sand(float in, Rng r) {
        float dur = 0.10f + 0.04f * in + 0.02f * r.uni();
        float[] b = buf(dur);
        Svf hp = new Svf(), body = new Svf(), lp = new Svf();
        hp.set(1500f + 600f * r.uni(), 0.7f);
        body.set(480f + 150f * r.uni(), 0.8f);
        lp.set(9000f, 0.7f);
        Grains g = new Grains(r, 2200f, 0.0004f, 1.4f);
        float slide = (0.5f + 0.2f * r.uni()) * dur;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) SR;
            float e1 = ad(t, 0.006f, 0.022f + 0.01f * in);
            float e2 = 0.55f * gauss((t - slide) / (0.16f * dur));
            float n = r.bi() * (0.5f + g.next()) * (e1 + e2);
            b[i] = lp.lp(hp.hp(n)) + body.bpn(r.bi() * e1) * (0.25f + 0.35f * in);
        }
        return b;
    }

    /** Wet squelch: a low-passed burst with bubbles, then a short resonant upward "suck" as the foot pulls free. */
    private static float[] mud(float in, Rng r) {
        float dur = 0.25f + 0.05f * in + 0.03f * r.uni();
        float[] b = buf(dur);
        Svf lp = new Svf(), suck = new Svf();
        lp.set(550f, 1.2f);
        float t0 = 0.12f + 0.04f * r.uni(), sl = 0.07f + 0.02f * r.uni();
        float suckF = 260f + 80f * r.uni();
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) SR;
            float y = lp.lp(r.bi() * ad(t, 0.005f, 0.03f + 0.01f * in)) * 1.6f;
            float x = (t - t0) / sl;
            if (x >= 0f) {
                float es = 0f;
                if (x < 1f) {
                    if ((i & 15) == 0) {
                        suck.set(suckF * (float) Math.exp(1.6f * x), 7f);
                    }
                    es = x < 0.75f ? x / 0.75f : (1f - x) / 0.25f;
                }
                y += suck.bp(r.bi() * es * es) * 0.75f * (0.6f + 0.4f * in);
            }
            b[i] = y;
        }
        int bubbles = 3 + r.below(4);
        for (int k = 0; k < bubbles; k++) {
            float f0 = 200f + 400f * r.uni();
            bloop(b, 0.01f + 0.09f * r.uni(), f0, f0 * 1.5f, 0.015f + 0.015f * r.uni(), 0.3f + 0.25f * r.uni());
        }
        float fp = 700f + 300f * r.uni();
        bloop(b, t0 + sl, fp, fp * 1.4f, 0.012f, 0.45f); // the little pop that ends the suck
        return b;
    }

    /** Small splash: a slap, a short burst of spray with a gloop underneath, and a few droplets landing. */
    private static float[] puddle(float in, Rng r) {
        float dur = 0.26f + 0.08f * in + 0.03f * r.uni();
        float[] b = buf(dur);
        Svf lp = new Svf(), hp = new Svf(), body = new Svf();
        lp.set(420f, 0.8f);
        hp.set(1500f, 0.7f);
        body.set(650f + 250f * r.uni(), 0.9f);
        Grains g = new Grains(r, 500f, 0.0012f, 1.2f);
        float spray = 0f;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) SR;
            float eT = ad(t, 0.003f, 0.018f);
            float eB = ad(t - 0.003f, 0.008f, 0.035f + 0.02f * in);
            float eS = ad(t - 0.004f, 0.006f, 0.04f + 0.035f * in);
            spray += 0.63f * (hp.hp(r.bi() * (0.35f + g.next()) * eS) - spray); // round off the top end (~7 kHz)
            b[i] = lp.lp(r.bi() * eT) * 3f + body.bpn(r.bi() * eB) * 2f + spray * 0.6f;
        }
        float fg = 280f + 100f * r.uni();
        bloop(b, 0.012f, fg, fg * 1.6f, 0.04f + 0.01f * r.uni(), 0.45f);
        int drops = 3 + (int) (5f * in * r.uni());
        for (int k = 0; k < drops; k++) {
            float u = r.uni();
            float f0 = 1200f * Dsp.exp2(1.5f * r.uni());
            bloop(b, 0.03f + 0.65f * dur * u, f0, f0 * (1.3f + 0.4f * r.uni()), 0.008f + 0.012f * r.uni(),
                    (0.15f + 0.25f * r.uni()) * (1f - 0.7f * u));
        }
        return b;
    }

    /** Soft squish (dull burst with a couple of tiny bubbles) plus a faint high swish of wet blades. */
    private static float[] wetGrass(float in, Rng r) {
        float dur = 0.16f + 0.04f * in + 0.02f * r.uni();
        float[] b = buf(dur);
        Svf lp = new Svf(), sw = new Svf();
        lp.set(950f, 0.9f);
        sw.set(4500f * (0.9f + 0.2f * r.uni()), 0.9f);
        Grains g = new Grains(r, 300f, 0.002f, 1.5f);
        float swT = 0.045f + 0.02f * r.uni();
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) SR;
            float e1 = ad(t, 0.008f, 0.03f + 0.01f * in);
            float e2 = gauss((t - swT) / 0.035f);
            b[i] = lp.lp(r.bi() * e1) * 1.5f + sw.bpn(r.bi() * (0.3f + g.next()) * e2) * 0.28f;
        }
        int bubbles = 1 + r.below(3);
        for (int k = 0; k < bubbles; k++) {
            float f0 = 400f + 500f * r.uni();
            bloop(b, 0.005f + 0.045f * r.uni(), f0, f0 * 1.4f, 0.01f + 0.004f * r.uni(), 0.22f);
        }
        return b;
    }

    /** Dry scuff: coarse crackle in the mids, a few pebble ticks, and a thin dusty tail. No low end. */
    private static float[] gravel(float in, Rng r) {
        float dur = 0.14f + 0.04f * in + 0.02f * r.uni();
        float[] b = buf(dur);
        Svf bp = new Svf(), hp = new Svf(), tick = new Svf();
        bp.set(2400f * (0.85f + 0.3f * r.uni()), 0.8f);
        hp.set(5000f, 0.7f);
        Grains g = new Grains(r, 320f, 0.0015f, 3.5f);
        float tickAcc = 0f, tickThr = r.exp1(), tickNorm = 0f;
        float toe = (0.45f + 0.15f * r.uni()) * dur;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) SR;
            float e = ad(t, 0.004f, 0.035f + 0.015f * in) + 0.5f * gauss((t - toe) / 0.03f);
            float x = 0f;
            tickAcc += 45f * e / SR;
            if (tickAcc >= tickThr) {
                tickAcc -= tickThr;
                tickThr = r.exp1();
                float f = 1800f * Dsp.exp2(1.3f * r.uni());
                tick.set(f, 6f);
                tickNorm = 1f / (Dsp.TWO_PI * f / SR);
                x = (0.4f + 0.6f * r.uni()) * e;
            }
            b[i] = bp.bpn(r.bi() * (0.2f + g.next()) * e) + hp.hp(r.bi() * ad(t, 0.01f, 0.06f)) * 0.12f
                    + tick.bp(x) * tickNorm * 0.5f;
        }
        return b;
    }

    /** Hard tick with a faint glassy ring (three inharmonic partials) and a small low knock. */
    private static float[] ice(float in, Rng r) {
        float dur = 0.15f + 0.04f * r.uni();
        float[] b = buf(dur);
        Svf bp = new Svf();
        bp.set(3200f * (0.9f + 0.2f * r.uni()), 1.5f);
        float f0 = 2600f + 800f * r.uni();
        float[] ratio = {1f, 1.59f + 0.05f * r.bi(), 2.71f + 0.08f * r.bi()};
        float[] dec = {0.035f, 0.025f, 0.015f};
        float[] amp = {0.10f, 0.07f, 0.04f};
        float knockF = 170f * (0.9f + 0.2f * r.uni());
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) SR;
            float y = bp.bpn(r.bi() * (float) Math.exp(-t / 0.0012f)) * 2.5f;
            float on = Math.min(1f, t / 0.0005f);
            for (int k = 0; k < 3; k++) {
                y += (float) Math.sin(2.0 * Math.PI * f0 * ratio[k] * t) * (float) Math.exp(-t / dec[k]) * amp[k] * on;
            }
            y += (float) Math.sin(2.0 * Math.PI * knockF * t) * (float) Math.exp(-t / 0.012f) * 0.5f * (0.5f + 0.5f * in);
            b[i] = y;
        }
        return b;
    }

    // -------------------------------------------------------------------------------------------------- helpers

    private static float[] buf(float sec) {
        return new float[Math.max(256, (int) (sec * SR))];
    }

    private static float gauss(float x) {
        return (float) Math.exp(-x * x);
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
