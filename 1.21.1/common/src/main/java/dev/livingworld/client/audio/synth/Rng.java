package dev.livingworld.client.audio.synth;

/** Small, fast xorshift64 PRNG used for noise and for all random decisions. Not thread-safe: one per owner. */
final class Rng {
    private static final float S16 = 1f / 32768f;
    private long s;

    Rng(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        s = z == 0L ? 0x2545F4914F6CDD1DL : z;
    }

    private long step() {
        long x = s;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        s = x;
        return x;
    }

    int nextInt() {
        return (int) (step() >>> 32);
    }

    /** Uniform in [0,1). */
    float uni() {
        return (nextInt() >>> 8) * (1f / 16777216f);
    }

    /** Uniform in [-1,1). */
    float bi() {
        return nextInt() * (1f / 2147483648f);
    }

    /** Uniform integer in [0,n). */
    int below(int n) {
        return (int) (uni() * n);
    }

    /**
     * Exponentially distributed with mean 1 (at most ~8.3): the gap of a Poisson process. Table-based on purpose:
     * it is called from inside per-sample loops, and a Math.log call there would make the JIT spill the loop state.
     */
    float exp1() {
        return EXP1[nextInt() >>> (32 - EXP_BITS)];
    }

    /**
     * Safe upper clamp for an "expected events so far" accumulator that is compared against {@link #exp1()}.
     * It must stay above the largest value exp1() can return (~8.3), or the accumulator could never reach its
     * threshold again and the event stream would stall for ever.
     */
    static final float EXP1_CAP = 9f;

    private static final int EXP_BITS = 11;
    private static final float[] EXP1 = new float[1 << EXP_BITS];

    static {
        for (int i = 0; i < EXP1.length; i++) {
            EXP1[i] = (float) -Math.log((i + 0.5) / EXP1.length);
        }
    }

    /** Two independent white noise streams (16-bit resolution) from one generator step per sample. */
    void fill2(float[] a, float[] b, int n) {
        long x = s;
        for (int i = 0; i < n; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            a[i] = (short) (x >>> 48) * S16;
            b[i] = (short) (x >>> 32) * S16;
        }
        s = x;
    }

    /** Four independent white noise streams (16-bit resolution) from one generator step per sample. */
    void fill4(float[] a, float[] b, float[] c, float[] d, int n) {
        long x = s;
        for (int i = 0; i < n; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            a[i] = (short) (x >>> 48) * S16;
            b[i] = (short) (x >>> 32) * S16;
            c[i] = (short) (x >>> 16) * S16;
            d[i] = (short) x * S16;
        }
        s = x;
    }
}
