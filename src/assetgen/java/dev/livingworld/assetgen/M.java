package dev.livingworld.assetgen;

/**
 * Small math / signed-distance toolbox shared by all generators.
 * Everything goes through StrictMath so the output is bit-identical on every JVM.
 */
final class M {
    private M() {}

    static final double PI = StrictMath.PI;
    static final double TAU = StrictMath.PI * 2.0;

    static double sin(double v) { return StrictMath.sin(v); }
    static double cos(double v) { return StrictMath.cos(v); }
    static double atan2(double y, double x) { return StrictMath.atan2(y, x); }
    static double exp(double v) { return StrictMath.exp(v); }
    static double pow(double a, double b) { return StrictMath.pow(a, b); }
    static double sqrt(double v) { return StrictMath.sqrt(v); }
    static double log(double v) { return StrictMath.log(v); }
    static double abs(double v) { return v < 0 ? -v : v; }
    static double sq(double v) { return v * v; }
    static double fract(double v) { return v - StrictMath.floor(v); }
    static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
    static double sat(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    static double len(double x, double y) { return StrictMath.sqrt(x * x + y * y); }

    /** Smoothstep; also valid with e0 &gt; e1 (then it falls from 1 to 0). */
    static double smooth(double e0, double e1, double x) {
        double t = sat((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    static double gauss(double d, double sigma) { return StrictMath.exp(-(d * d) / (2 * sigma * sigma)); }

    // ---- signed distances (negative inside) -------------------------------------------------

    static double sdCircle(double px, double py, double cx, double cy, double r) {
        return len(px - cx, py - cy) - r;
    }

    /** Parameter 0..1 of the closest point on segment a-b. */
    static double segT(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double l2 = dx * dx + dy * dy;
        if (l2 < 1e-12) return 0;
        return sat(((px - ax) * dx + (py - ay) * dy) / l2);
    }

    static double sdSegment(double px, double py, double ax, double ay, double bx, double by) {
        double t = segT(px, py, ax, ay, bx, by);
        return len(px - (ax + (bx - ax) * t), py - (ay + (by - ay) * t));
    }

    /** Tapered capsule: radius ra at a, rb at b (approximate but smooth). */
    static double sdTaper(double px, double py, double ax, double ay, double ra, double bx, double by, double rb) {
        double t = segT(px, py, ax, ay, bx, by);
        return len(px - (ax + (bx - ax) * t), py - (ay + (by - ay) * t)) - lerp(ra, rb, t);
    }

    /** Rotated ellipse, first-order distance estimate. */
    static double sdEllipse(double px, double py, double cx, double cy, double rx, double ry, double rot) {
        double dx = px - cx, dy = py - cy;
        double c = cos(rot), s = sin(rot);
        double x = dx * c + dy * s, y = -dx * s + dy * c;
        double k0 = len(x / rx, y / ry);
        double k1 = len(x / (rx * rx), y / (ry * ry));
        if (k1 < 1e-9) return -Math.min(rx, ry);
        return k0 * (k0 - 1.0) / k1;
    }

    /** Polynomial smooth minimum (smooth union of two distance fields). */
    static double smin(double a, double b, double k) {
        double h = sat(0.5 + 0.5 * (b - a) / k);
        return lerp(b, a, h) - k * h * (1 - h);
    }

    static double smax(double a, double b, double k) { return -smin(-a, -b, k); }

    /** Piecewise-linear colour ramp. stops = {t, r, g, b}, sorted by t. Writes o[0..2]. */
    static void ramp(double t, double[][] stops, double[] o) {
        if (t <= stops[0][0]) { o[0] = stops[0][1]; o[1] = stops[0][2]; o[2] = stops[0][3]; return; }
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i][0]) {
                double[] a = stops[i - 1], b = stops[i];
                double k = (t - a[0]) / (b[0] - a[0]);
                o[0] = lerp(a[1], b[1], k); o[1] = lerp(a[2], b[2], k); o[2] = lerp(a[3], b[3], k);
                return;
            }
        }
        double[] l = stops[stops.length - 1];
        o[0] = l[1]; o[1] = l[2]; o[2] = l[3];
    }
}
