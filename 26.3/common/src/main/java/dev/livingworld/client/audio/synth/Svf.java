package dev.livingworld.client.audio.synth;

/**
 * Trapezoidal (TPT) state-variable filter. Unconditionally stable and safe under fast coefficient modulation,
 * which is what all the drifting resonances in this package rely on.
 * <p>
 * The state update is written in the expanded form {@code ic1' = c1*ic1 + c2*v3}, {@code ic2' = ic2 + c2*ic1 + c3*v3}
 * (algebraically identical to the textbook {@code ic = 2*v - ic}) because it shortens the loop-carried dependency
 * chain, which is what bounds the speed of the per-sample loops.
 */
final class Svf {
    private float ic1, ic2;
    private float a1 = 1f, a2, a3, k = 1f;
    private float c1 = 1f, c2, c3;

    void set(float fc, float q) {
        if (!(fc > 10f)) {
            fc = 10f;
        } else if (fc > 18000f) {
            fc = 18000f;
        }
        if (!(q > 0.3f)) {
            q = 0.3f;
        }
        float g = Dsp.tanFast(Dsp.PI * fc * Dsp.INV_SR);
        k = 1f / q;
        a1 = 1f / (1f + g * (g + k));
        a2 = g * a1;
        a3 = g * a2;
        c1 = 2f * a1 - 1f;
        c2 = 2f * a2;
        c3 = 2f * a3;
    }

    void reset() {
        ic1 = 0f;
        ic2 = 0f;
    }

    /** Zero out states that have decayed into the denormal range (call once per block on filters that may see silence). */
    void flush() {
        if (ic1 < 1e-18f && ic1 > -1e-18f) {
            ic1 = 0f;
        }
        if (ic2 < 1e-18f && ic2 > -1e-18f) {
            ic2 = 0f;
        }
    }

    float lp(float x) {
        float s1 = ic1, s2 = ic2;
        float v3 = x - s2;
        ic1 = c1 * s1 + c2 * v3;
        ic2 = s2 + c2 * s1 + c3 * v3;
        return s2 + a2 * s1 + a3 * v3;
    }

    /** Raw band-pass: peak gain equals Q. */
    float bp(float x) {
        float s1 = ic1, s2 = ic2;
        float v3 = x - s2;
        ic1 = c1 * s1 + c2 * v3;
        ic2 = s2 + c2 * s1 + c3 * v3;
        return a1 * s1 + a2 * v3;
    }

    /** Normalised band-pass: unity peak gain. */
    float bpn(float x) {
        float s1 = ic1, s2 = ic2;
        float v3 = x - s2;
        ic1 = c1 * s1 + c2 * v3;
        ic2 = s2 + c2 * s1 + c3 * v3;
        return (a1 * s1 + a2 * v3) * k;
    }

    float hp(float x) {
        float s1 = ic1, s2 = ic2;
        float v3 = x - s2;
        ic1 = c1 * s1 + c2 * v3;
        ic2 = s2 + c2 * s1 + c3 * v3;
        return x - k * (a1 * s1 + a2 * v3) - (s2 + a2 * s1 + a3 * v3);
    }
}
