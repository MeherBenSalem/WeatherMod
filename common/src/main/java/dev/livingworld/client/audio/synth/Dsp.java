package dev.livingworld.client.audio.synth;

/** Shared constants and small math helpers for the synth package. */
final class Dsp {
    static final int SR = 44100;
    static final float INV_SR = 1f / SR;
    /** Internal control block: every layer renders exactly this many frames at a time. */
    static final int BLOCK = 64;
    static final float BLOCK_SEC = BLOCK / (float) SR;
    static final float PI = (float) Math.PI;
    static final float TWO_PI = (float) (2.0 * Math.PI);

    private static final int SIN_SIZE = 4096;
    private static final int SIN_MASK = SIN_SIZE - 1;
    private static final float[] SIN = new float[SIN_SIZE + 1];

    static {
        for (int i = 0; i <= SIN_SIZE; i++) {
            SIN[i] = (float) Math.sin(2.0 * Math.PI * i / SIN_SIZE);
        }
    }

    private Dsp() {
    }

    /** Sine of a phase given in cycles (table lookup with linear interpolation). Phase should be &gt;= 0. */
    static float sin(float phase) {
        float p = phase * SIN_SIZE;
        int i = (int) p;
        float f = p - i;
        i &= SIN_MASK;
        float a = SIN[i];
        return a + f * (SIN[i + 1] - a);
    }

    /** Constant-power pan gains, pan in -1..1. */
    static float panL(float pan) {
        return sin(0.25f + (clamp(pan, -1f, 1f) + 1f) * 0.125f);
    }

    static float panR(float pan) {
        return sin((clamp(pan, -1f, 1f) + 1f) * 0.125f);
    }

    /** Clamp that also maps NaN to {@code lo}. */
    static float clamp(float x, float lo, float hi) {
        if (!(x > lo)) {
            return lo;
        }
        return x > hi ? hi : x;
    }

    static float clamp01(float x) {
        return clamp(x, 0f, 1f);
    }

    static float exp2(float x) {
        return (float) Math.exp(x * 0.69314718f);
    }

    static float pow(float x, float e) {
        return x <= 0f ? 0f : (float) Math.pow(x, e);
    }

    static float sqrt(float x) {
        return (float) Math.sqrt(x);
    }

    static float sq(float x) {
        return x * x;
    }

    /** tan(x) for 0 <= x < ~1.3, good to a fraction of a percent below 0.7 (filter cutoffs up to ~10 kHz). */
    static float tanFast(float x) {
        if (x > 1.3f) {
            x = 1.3f;
        }
        float x2 = x * x;
        return x * (1f + x2 * (0.33333334f + x2 * (0.13333334f + x2 * (0.05396825f + x2 * 0.0218694f))));
    }

    /** One-pole smoothing step that snaps onto the target once it is within 1e-4 (so zero really becomes zero). */
    static float smooth(float cur, float target, float k) {
        float d = target - cur;
        if (d < 1e-4f && d > -1e-4f) {
            return target;
        }
        return cur + d * k;
    }

    /** One-pole coefficient for a time constant in seconds, applied once per sample. */
    static float kSample(float sec) {
        return 1f - (float) Math.exp(-1.0 / (sec * (double) SR));
    }

    /** One-pole coefficient for a time constant in seconds, applied once per block. */
    static float kBlock(float sec) {
        return 1f - (float) Math.exp(-BLOCK / (sec * (double) SR));
    }

    /** Per-sample multiplier of an exponential decay with the given time constant. */
    static float decay(float sec) {
        return (float) Math.exp(-1.0 / (sec * (double) SR));
    }

    static int blocks(float sec) {
        int b = (int) (sec / BLOCK_SEC);
        return b < 1 ? 1 : b;
    }
}
