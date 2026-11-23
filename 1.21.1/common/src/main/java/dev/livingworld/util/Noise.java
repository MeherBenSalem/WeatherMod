package dev.livingworld.util;

/**
 * Deterministic hash-based value noise. Everything here is a pure function of its inputs, which is what
 * lets every client derive identical wind and weather-intensity curves from the synchronised world time
 * without any networking.
 */
public final class Noise {
    private Noise() {
    }

    public static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    public static int hash(int x, int y) {
        return hash(x + 0x9E3779B9 * hash(y));
    }

    public static int hash(int x, int y, int z) {
        return hash(x + 0x9E3779B9 * hash(y + 0x85EBCA6B * hash(z)));
    }

    public static int hash(int x, int y, int z, int w) {
        return hash(x + 0x9E3779B9 * hash(y, z, w));
    }

    /** Maps a hash to [0, 1). */
    public static float unit(int h) {
        return (h >>> 8) * (1f / 16777216f);
    }

    public static float rand(int x, int seed) {
        return unit(hash(x, seed));
    }

    public static float rand(int x, int y, int seed) {
        return unit(hash(x, y, seed));
    }

    public static float rand(int x, int y, int z, int seed) {
        return unit(hash(x, y, z, seed));
    }

    private static float fade(float f) {
        return f * f * (3f - 2f * f);
    }

    /** 1D value noise in [0, 1]. Takes a double so large world times keep sub-cell precision. */
    public static float value1(double x, int seed) {
        int i = LWMath.fastFloor(x);
        float u = fade((float) (x - i));
        return LWMath.lerp(rand(i, seed), rand(i + 1, seed), u);
    }

    /** 2D value noise in [0, 1]. */
    public static float value2(double x, double y, int seed) {
        int ix = LWMath.fastFloor(x);
        int iy = LWMath.fastFloor(y);
        float u = fade((float) (x - ix));
        float v = fade((float) (y - iy));
        float a = LWMath.lerp(rand(ix, iy, seed), rand(ix + 1, iy, seed), u);
        float b = LWMath.lerp(rand(ix, iy + 1, seed), rand(ix + 1, iy + 1, seed), u);
        return LWMath.lerp(a, b, v);
    }

    /** 3D value noise in [0, 1]. */
    public static float value3(double x, double y, double z, int seed) {
        int ix = LWMath.fastFloor(x);
        int iy = LWMath.fastFloor(y);
        int iz = LWMath.fastFloor(z);
        float u = fade((float) (x - ix));
        float v = fade((float) (y - iy));
        float w = fade((float) (z - iz));
        float a = LWMath.lerp(rand(ix, iy, iz, seed), rand(ix + 1, iy, iz, seed), u);
        float b = LWMath.lerp(rand(ix, iy + 1, iz, seed), rand(ix + 1, iy + 1, iz, seed), u);
        float c = LWMath.lerp(rand(ix, iy, iz + 1, seed), rand(ix + 1, iy, iz + 1, seed), u);
        float d = LWMath.lerp(rand(ix, iy + 1, iz + 1, seed), rand(ix + 1, iy + 1, iz + 1, seed), u);
        return LWMath.lerp(LWMath.lerp(a, b, v), LWMath.lerp(c, d, v), w);
    }

    /** Fractal 1D noise, normalised to [0, 1]. */
    public static float fbm1(double x, int octaves, int seed) {
        float sum = 0f;
        float amp = 0.5f;
        float norm = 0f;
        for (int o = 0; o < octaves; o++) {
            sum += value1(x, seed + o * 131) * amp;
            norm += amp;
            amp *= 0.5f;
            x *= 2.03;
        }
        return sum / norm;
    }

    /** Fractal 3D noise, normalised to [0, 1]. */
    public static float fbm3(double x, double y, double z, int octaves, int seed) {
        float sum = 0f;
        float amp = 0.5f;
        float norm = 0f;
        for (int o = 0; o < octaves; o++) {
            sum += value3(x, y, z, seed + o * 131) * amp;
            norm += amp;
            amp *= 0.5f;
            x *= 2.03;
            y *= 2.03;
            z *= 2.03;
        }
        return sum / norm;
    }
}
