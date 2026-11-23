package dev.livingworld.assetgen;

import java.util.Arrays;
import java.util.Comparator;

/**
 * fx/noise.png - 256x256, tiles seamlessly in both directions.
 * R = low-frequency gradient noise (8 features across), G = 5-octave fbm, B = Worley F1 (12 cells across,
 * bright at the cell centres), A = high-frequency blue-ish noise with a flat histogram.
 */
final class NoiseTexture {
    static final int N = 256;

    private NoiseTexture() {}

    static Img generate() {
        double[] r = new double[N * N], g = new double[N * N], b = new double[N * N];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double u = x / (double) N, v = y / (double) N;
                int i = y * N + x;
                r[i] = Nz.perlinP(u * 8, v * 8, 8, 11);
                double sum = 0, amp = 1;
                for (int o = 0; o < 5; o++) {
                    int f = 4 << o;
                    sum += amp * Nz.perlinP(u * f, v * f, f, 20 + o * 7);
                    amp *= 0.5;
                }
                g[i] = sum;
                b[i] = -worley(u * 12, v * 12, 12, 31);
            }
        }
        centre(r);
        centre(g);
        centre(b);
        double[] a = blue(41);
        Img img = new Img(N, N);
        for (int i = 0; i < N * N; i++) {
            img.r[i] = (float) r[i]; img.g[i] = (float) g[i]; img.b[i] = (float) b[i]; img.a[i] = (float) a[i];
        }
        return img;
    }

    /** Toroidal F1 distance to one jittered feature point per lattice cell. */
    private static double worley(double x, double y, int per, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double best = 1e9;
        for (int oy = -2; oy <= 2; oy++) {
            for (int ox = -2; ox <= 2; ox++) {
                int cx = ix + ox, cy = iy + oy;
                int wx = Math.floorMod(cx, per), wy = Math.floorMod(cy, per);
                double px = cx + 0.1 + 0.8 * Nz.rnd(wx, wy, seed);
                double py = cy + 0.1 + 0.8 * Nz.rnd(wx, wy, seed + 1);
                best = Math.min(best, M.len(x - px, y - py));
            }
        }
        return best;
    }

    /** Stretch to the full 0..1 range, then bend with a gamma so the median sits at 0.5. */
    private static void centre(double[] v) {
        double lo = 1e9, hi = -1e9;
        for (double d : v) { lo = Math.min(lo, d); hi = Math.max(hi, d); }
        for (int i = 0; i < v.length; i++) v[i] = (v[i] - lo) / (hi - lo);
        double[] s = v.clone();
        Arrays.sort(s);
        double med = M.clamp(s[s.length / 2], 0.05, 0.95);
        double gamma = M.log(0.5) / M.log(med);
        for (int i = 0; i < v.length; i++) v[i] = M.pow(v[i], gamma);
    }

    /** White noise pushed towards blue by repeatedly removing its (toroidal) low-pass and re-equalising. */
    private static double[] blue(int seed) {
        double[] v = new double[N * N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) v[y * N + x] = Nz.rnd(x, y, seed);
        double[] k = new double[9];
        double ks = 0;
        for (int i = -4; i <= 4; i++) { k[i + 4] = M.gauss(i, 1.5); ks += k[i + 4]; }
        for (int i = 0; i < 9; i++) k[i] /= ks;
        double[] tmp = new double[N * N], low = new double[N * N];
        for (int it = 0; it < 4; it++) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    double s = 0;
                    for (int i = -4; i <= 4; i++) s += k[i + 4] * v[y * N + ((x + i) & (N - 1))];
                    tmp[y * N + x] = s;
                }
            }
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    double s = 0;
                    for (int i = -4; i <= 4; i++) s += k[i + 4] * tmp[((y + i) & (N - 1)) * N + x];
                    low[y * N + x] = s;
                }
            }
            for (int i = 0; i < N * N; i++) v[i] -= low[i];
            equalise(v);
        }
        // quantise so every one of the 256 levels is used by exactly 256 texels
        for (int i = 0; i < N * N; i++) v[i] = Math.min(255, (int) (v[i] * 256)) / 255.0;
        return v;
    }

    /** Replace every value by its rank / count: a perfectly flat histogram in [0, 1). */
    private static void equalise(double[] v) {
        Integer[] idx = new Integer[v.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        final double[] src = v.clone();
        Arrays.sort(idx, Comparator.<Integer>comparingDouble(i -> src[i]).thenComparingInt(i -> i));
        for (int rank = 0; rank < idx.length; rank++) v[idx[rank]] = rank / (double) idx.length;
    }
}
