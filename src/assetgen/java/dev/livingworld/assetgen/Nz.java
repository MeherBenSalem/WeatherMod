package dev.livingworld.assetgen;

/** Deterministic hash based noise: value noise, fbm and periodic (tileable) gradient noise. */
final class Nz {
    private Nz() {}

    static int hash(int x, int y, int seed) {
        int h = seed * 0x9E3779B1 + x * 0x85EBCA6B + y * 0xC2B2AE35;
        h ^= h >>> 15; h *= 0x2C1B3C6D;
        h ^= h >>> 12; h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    /** Uniform in [0,1). */
    static double rnd(int x, int y, int seed) {
        return (hash(x, y, seed) >>> 8) * (1.0 / 16777216.0);
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    /** Smooth value noise, 0..1. */
    static double value(double x, double y, int seed) {
        double fx = Math.floor(x), fy = Math.floor(y);
        int ix = (int) fx, iy = (int) fy;
        double tx = fade(x - fx), ty = fade(y - fy);
        double a = rnd(ix, iy, seed), b = rnd(ix + 1, iy, seed);
        double c = rnd(ix, iy + 1, seed), d = rnd(ix + 1, iy + 1, seed);
        return M.lerp(M.lerp(a, b, tx), M.lerp(c, d, tx), ty);
    }

    /** Fractal value noise, 0..1 (centred on 0.5). */
    static double fbm(double x, double y, int oct, int seed) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < oct; i++) {
            sum += amp * value(x, y, seed + i * 131);
            norm += amp;
            amp *= 0.5;
            x = x * 2.03 + 17.3;
            y = y * 2.03 + 9.1;
        }
        return sum / norm;
    }

    private static double grad(int ix, int iy, int per, int seed, double dx, double dy) {
        int wx = Math.floorMod(ix, per), wy = Math.floorMod(iy, per);
        double a = rnd(wx, wy, seed) * M.TAU;
        return M.cos(a) * dx + M.sin(a) * dy;
    }

    /** Gradient (Perlin) noise that repeats every {@code per} lattice cells; roughly -1..1. */
    static double perlinP(double x, double y, int per, int seed) {
        double fx = Math.floor(x), fy = Math.floor(y);
        int ix = (int) fx, iy = (int) fy;
        double dx = x - fx, dy = y - fy;
        double tx = fade(dx), ty = fade(dy);
        double n00 = grad(ix, iy, per, seed, dx, dy);
        double n10 = grad(ix + 1, iy, per, seed, dx - 1, dy);
        double n01 = grad(ix, iy + 1, per, seed, dx, dy - 1);
        double n11 = grad(ix + 1, iy + 1, per, seed, dx - 1, dy - 1);
        return M.lerp(M.lerp(n00, n10, tx), M.lerp(n01, n11, tx), ty) * 1.41421356;
    }
}
