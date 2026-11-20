package dev.livingworld.assetgen;

import java.util.ArrayList;
import java.util.Random;

/** overlay/frost.png (screen-edge frost) and overlay/lens_drops.png (rain on the camera lens, data texture). */
final class Overlays {
    private Overlays() {}

    // =============================================================================================
    // Frost
    // =============================================================================================
    static final int FROST = 512;

    static Img frost() {
        final int N = FROST;
        float[] den = new float[N * N];
        Random rn = new Random(20240117L);

        // long fern crystals fanning out of the four corners
        double[][] corners = {{0, 0}, {N, 0}, {0, N}, {N, N}};
        for (double[] c : corners) {
            double base = M.atan2(N / 2.0 - c[1], N / 2.0 - c[0]);
            int k = 7;
            for (int i = 0; i < k; i++) {
                double off = ((i + 0.5) / k - 0.5) * (M.PI / 2 * 0.94) + (rn.nextDouble() - 0.5) * 0.08;
                double L = (112 + 92 * M.cos(off * 2)) * (0.84 + 0.3 * rn.nextDouble());
                double an = base + off;
                grow(den, N, c[0] - M.cos(an) * 5, c[1] - M.sin(an) * 5, an, L, 2.9, 0, rn, 1.0);
            }
        }
        // shorter crystals along the edges, longer near the corners
        for (int e = 0; e < 4; e++) {
            double pos = 16 + rn.nextDouble() * 10;
            while (pos < N - 14) {
                double prox = Math.abs(pos - N / 2.0) / (N / 2.0);
                double L = M.lerp(50, 106, M.pow(prox, 1.3)) * (0.6 + 0.65 * rn.nextDouble());
                double inward = e == 0 ? M.PI / 2 : e == 1 ? -M.PI / 2 : e == 2 ? 0 : M.PI;
                double an = inward + (rn.nextDouble() - 0.5) * 1.0;
                double x = e == 0 ? pos : e == 1 ? pos : e == 2 ? -3 : N + 3;
                double y = e == 0 ? -3 : e == 1 ? N + 3 : pos;
                grow(den, N, x, y, an, L, 2.3, 0, rn, 0.95);
                pos += 15 + rn.nextDouble() * 15;
            }
        }
        // dense fringe of small needles right on the border
        for (int e = 0; e < 4; e++) {
            double pos = 3 + rn.nextDouble() * 4;
            while (pos < N - 2) {
                double inward = e == 0 ? M.PI / 2 : e == 1 ? -M.PI / 2 : e == 2 ? 0 : M.PI;
                double an = inward + (rn.nextDouble() - 0.5) * 1.3;
                double x = e == 0 ? pos : e == 1 ? pos : e == 2 ? -2 : N + 2;
                double y = e == 0 ? -2 : e == 1 ? N + 2 : pos;
                grow(den, N, x, y, an, 8 + rn.nextDouble() * 20, 1.5, 1, rn, 0.85);
                pos += 4 + rn.nextDouble() * 6;
            }
        }

        float[] glow = blur(den, N, 2.6);
        float[] wide = blur(den, N, 7.0);

        Img img = new Img(N, N);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int i = y * N + x;
                double px = x + 0.5, py = y + 0.5;
                double u = Math.abs(px - N / 2.0) / (N / 2.0), v = Math.abs(py - N / 2.0) / (N / 2.0);
                double m = Math.max(u, v), n = Math.min(u, v);
                double q = (1 - m) * (N / 2.0);                                   // px to the nearest edge
                double f1 = Nz.fbm(px * 0.012, py * 0.012, 4, 3001);
                double T = (15 + 92 * M.pow(n, 3.0)) * (0.5 + 1.0 * f1);          // rime depth, thick in the corners
                double rime = M.smooth(T, T * 0.08, q);
                // crystalline grain: ridged noise + fine sparkle so the rime is never a plain gradient
                double ridge = 1 - Math.abs(2 * Nz.fbm(px * 0.055, py * 0.055, 3, 3002) - 1);
                double fine = Nz.fbm(px * 0.23, py * 0.23, 2, 3003);
                double rimeA = M.pow(rime, 1.2) * (0.42 + 0.42 * ridge + 0.34 * fine);
                double D = den[i], G = glow[i], Wd = wide[i];
                double A = 1 - (1 - M.sat(rimeA)) * (1 - 0.95 * D) * (1 - 0.5 * M.sat(G * 1.5)) * (1 - 0.3 * M.sat(Wd * 2.2));
                // isolated ice specks in the transition zone
                double speck = Nz.rnd(x, y, 3004);
                if (speck > 0.992) A = Math.max(A, 0.75 * M.sat(Wd * 5 + rime) * M.sat((speck - 0.992) * 400));
                // centre stays perfectly clear: alpha is 0 inside the central 55% ellipse
                double e = M.len((px - N / 2.0) / (N * 0.275), (py - N / 2.0) / (N * 0.275));
                A *= M.smooth(1.0, 1.32, e);
                double k = M.sat(D * 0.85 + rimeA * 0.35);
                img.set(x, y,
                        M.lerp(214, 244, k) / 255.0, M.lerp(234, 250, k) / 255.0, 1.0,
                        M.sat(A) * 230.0 / 255.0);
            }
        }
        return img;
    }

    /** Recursive hexagonal (60 degree) dendrite: a stem with fern-like side branches. */
    private static void grow(float[] den, int N, double x, double y, double ang, double len, double width,
                             int depth, Random rn, double inten) {
        if (len < 3.2) return;
        final double step = 3.0;
        int steps = Math.max(1, (int) (len / step));
        double curve = (rn.nextDouble() - 0.5) * (depth == 0 ? 0.012 : 0.006);
        double spacing = depth == 0 ? 8.5 + rn.nextDouble() * 4 : depth == 1 ? 5.2 + rn.nextDouble() * 2 : 4.0;
        double next = spacing * (0.5 + rn.nextDouble() * 0.5);
        double travelled = 0;
        for (int i = 0; i < steps; i++) {
            double t0 = (double) i / steps, t1 = (double) (i + 1) / steps;
            ang += curve + (rn.nextDouble() - 0.5) * 0.025;
            double nx = x + M.cos(ang) * step, ny = y + M.sin(ang) * step;
            double w0 = width * (1 - 0.86 * t0), w1 = width * (1 - 0.86 * t1);
            seg(den, N, x, y, nx, ny, w0, w1, inten * (1 - 0.3 * t0), inten * (1 - 0.3 * t1));
            travelled += step;
            if (depth < 3 && travelled >= next) {
                next += spacing * (0.8 + rn.nextDouble() * 0.4);
                double bl = len * (depth == 0 ? 0.4 : 0.5) * M.pow(1 - t1, 0.75);
                double a60 = M.PI / 3 + (rn.nextDouble() - 0.5) * 0.1;
                double l1 = bl * (0.65 + rn.nextDouble() * 0.55), l2 = bl * (0.65 + rn.nextDouble() * 0.55);
                if (rn.nextDouble() < 0.94) grow(den, N, nx, ny, ang + a60, l1, w1 * 0.62, depth + 1, rn, inten * 0.88);
                if (rn.nextDouble() < 0.94) grow(den, N, nx, ny, ang - a60, l2, w1 * 0.62, depth + 1, rn, inten * 0.88);
            }
            x = nx;
            y = ny;
        }
    }

    private static void seg(float[] den, int N, double x0, double y0, double x1, double y1,
                            double w0, double w1, double i0, double i1) {
        double pad = Math.max(w0, w1) * 0.5 + 1.0;
        int xa = Math.max(0, (int) Math.floor(Math.min(x0, x1) - pad));
        int xb = Math.min(N - 1, (int) Math.ceil(Math.max(x0, x1) + pad));
        int ya = Math.max(0, (int) Math.floor(Math.min(y0, y1) - pad));
        int yb = Math.min(N - 1, (int) Math.ceil(Math.max(y0, y1) + pad));
        for (int py = ya; py <= yb; py++) {
            for (int px = xa; px <= xb; px++) {
                double cx = px + 0.5, cy = py + 0.5;
                double t = M.segT(cx, cy, x0, y0, x1, y1);
                double d = M.len(cx - (x0 + (x1 - x0) * t), cy - (y0 + (y1 - y0) * t));
                double hw = M.lerp(w0, w1, t) * 0.5;
                double c = M.sat(hw + 0.5 - d) * Math.min(1.0, 0.45 + hw * 1.6);
                if (c <= 0) continue;
                float v = (float) (c * M.lerp(i0, i1, t));
                int i = py * N + px;
                if (v > den[i]) den[i] = v;
            }
        }
    }

    private static float[] blur(float[] src, int N, double sigma) {
        int rad = (int) Math.ceil(sigma * 2.5);
        double[] k = new double[rad * 2 + 1];
        double ks = 0;
        for (int i = -rad; i <= rad; i++) { k[i + rad] = M.gauss(i, sigma); ks += k[i + rad]; }
        for (int i = 0; i < k.length; i++) k[i] /= ks;
        float[] tmp = new float[N * N], out = new float[N * N];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double s = 0;
                for (int i = -rad; i <= rad; i++) s += k[i + rad] * src[y * N + Math.max(0, Math.min(N - 1, x + i))];
                tmp[y * N + x] = (float) s;
            }
        }
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double s = 0;
                for (int i = -rad; i <= rad; i++) s += k[i + rad] * tmp[Math.max(0, Math.min(N - 1, y + i)) * N + x];
                out[y * N + x] = (float) s;
            }
        }
        return out;
    }

    // =============================================================================================
    // Lens drops
    // =============================================================================================
    static final int LENS = 256;

    /** One drop: centre, radius, outline wobble, optional trail above it (the drop has run downwards). */
    private static final class Drop {
        double x, y, r, a2, p2, a3, p3;
        double trail;       // trail length in px, 0 = none
        double sway, swayPh; // gentle wobble of the trail
    }

    static Img lensDrops() {
        final int N = LENS;
        Random rn = new Random(8675309L);
        ArrayList<Drop> drops = new ArrayList<>();
        ArrayList<double[]> occupied = new ArrayList<>(); // x, y, r of everything already placed

        double[] radii = new double[35];
        int ri = 0;
        radii[ri++] = 14.0; radii[ri++] = 12.6; radii[ri++] = 11.4;
        for (int i = 0; i < 8; i++) radii[ri++] = 6.6 + rn.nextDouble() * 3.8;
        for (int i = 0; i < 24; i++) radii[ri++] = 3.0 + rn.nextDouble() * 3.0;
        // which drops get a trail: one big, three medium, two small
        boolean[] trailed = new boolean[35];
        trailed[1] = true; trailed[4] = true; trailed[6] = true; trailed[9] = true; trailed[13] = true; trailed[20] = true;

        for (int k = 0; k < radii.length; k++) {
            double r = radii[k];
            double trail = trailed[k] ? r * (1.9 + rn.nextDouble() * 1.4) : 0;
            for (int attempt = 0; attempt < 4000; attempt++) {
                double x = r + 4 + rn.nextDouble() * (N - 2 * r - 8);
                double y = r + 4 + trail + rn.nextDouble() * (N - 2 * r - 8 - trail);
                boolean ok = true;
                for (double[] oc : occupied) {
                    if (M.len(x - oc[0], y - oc[1]) < r + oc[2] + 3.5) { ok = false; break; }
                    if (trail > 0) { // keep the trail corridor free as well
                        double dd = M.sdSegment(oc[0], oc[1], x, y - trail - r * 0.5, x, y);
                        if (dd < r * 0.6 + oc[2] + 3) { ok = false; break; }
                    }
                }
                if (!ok) continue;
                Drop d = new Drop();
                d.x = x; d.y = y; d.r = r;
                d.a2 = 0.02 + rn.nextDouble() * 0.04; d.p2 = rn.nextDouble() * M.TAU;
                d.a3 = 0.015 + rn.nextDouble() * 0.03; d.p3 = rn.nextDouble() * M.TAU;
                d.trail = trail;
                d.sway = 0.5 + rn.nextDouble() * 0.9; d.swayPh = rn.nextDouble() * M.TAU;
                drops.add(d);
                occupied.add(new double[]{x, y, r});
                if (trail > 0) {
                    for (double ty = r; ty <= trail + r * 0.5; ty += r * 0.6) occupied.add(new double[]{x, y - ty, r * 0.55});
                }
                break;
            }
        }
        System.out.println("lens_drops: placed " + drops.size() + " droplets");

        Img img = new Img(N, N);
        final int SS = 3;
        for (int py = 0; py < N; py++) {
            for (int px = 0; px < N; px++) {
                double sox = 0, soy = 0, ssp = 0, sm = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        double x = px + (sx + 0.5) / SS, y = py + (sy + 0.5) / SS;
                        double bm = 0, box = 0, boy = 0, bsp = 0;
                        for (Drop d : drops) {
                            double dx = x - d.x, dy = y - d.y;
                            if (Math.abs(dx) > d.r * 1.3 + 2 || dy > d.r * 1.3 + 2 || dy < -(d.r * 1.3 + 2 + d.trail)) continue;
                            // ---- trail (drawn first, the drop itself overrides it) ----
                            if (d.trail > 0 && dy < 0) {
                                double tt = (-dy - d.r * 0.3) / d.trail;               // 0 at the drop, 1 at the old end
                                if (tt > -0.2 && tt < 1) {
                                    double tc = M.sat(tt);
                                    double cxl = d.sway * M.sin(tc * 5.0 + d.swayPh) * tc; // slight meander
                                    double hw = d.r * M.lerp(0.44, 0.15, M.pow(tc, 0.8))
                                            * (1 + 0.18 * M.sin(tc * 17 + d.swayPh));     // beading
                                    hw *= M.smooth(1.0, 0.86, tt);                         // rounded far end
                                    double dl = x - (d.x + cxl);
                                    double mk = M.smooth(0.6, -0.6, Math.abs(dl) - hw) * M.lerp(1.0, 0.75, tc);
                                    if (mk > bm) {
                                        bm = mk;
                                        double nrm = hw > 0.01 ? M.sat(Math.abs(dl) / hw) : 0;
                                        box = -Math.signum(dl) * M.pow(nrm, 1.5) * 0.7 * mk;
                                        boy = 0;
                                        bsp = 0.4 * M.smooth(0.5, 0.0, Math.abs(dl + hw * 0.45) / Math.max(hw * 0.4, 0.5)) * mk;
                                    }
                                }
                            }
                            // ---- the drop ----
                            double dys = dy / (dy > 0 ? 1.07 : 0.95);                   // sags a little under gravity
                            double an = M.atan2(dys, dx);
                            double R = d.r * (1 + d.a2 * M.sin(2 * an + d.p2) + d.a3 * M.sin(3 * an + d.p3));
                            double dist = M.len(dx, dys);
                            double dn = dist / R;
                            double mk = M.smooth(0.7, -0.7, (dn - 1) * R);
                            if (mk > 0 && mk >= bm * 0.999) {
                                bm = mk;
                                double mag = M.pow(M.sat(dn), 1.6) * mk;                // grows toward the rim
                                double il = dist > 1e-6 ? 1 / dist : 0;
                                box = -dx * il * mag;
                                boy = -dys * il * mag;
                                // specular: crescent in the upper left (or a dot on tiny drops)
                                if (d.r >= 4.6) {
                                    double c1 = M.len(dx + 0.36 * d.r, dy + 0.38 * d.r) - 0.40 * d.r;
                                    double c2 = M.len(dx + 0.20 * d.r, dy + 0.21 * d.r) - 0.47 * d.r;
                                    bsp = M.smooth(0.5, -0.5, c1) * M.smooth(-0.5, 0.5, c2);
                                } else {
                                    bsp = 0.9 * M.smooth(0.4, -0.5, M.len(dx + 0.34 * d.r, dy + 0.36 * d.r) - Math.max(0.75, 0.27 * d.r));
                                }
                            }
                        }
                        sox += box; soy += boy; ssp += bsp; sm += bm;
                    }
                }
                double k = 1.0 / (SS * SS);
                img.set(px, py,
                        (128 + 127 * M.clamp(sox * k, -1, 1)) / 255.0,
                        (128 + 127 * M.clamp(soy * k, -1, 1)) / 255.0,
                        M.sat(ssp * k), M.sat(sm * k));
            }
        }
        return img;
    }
}
