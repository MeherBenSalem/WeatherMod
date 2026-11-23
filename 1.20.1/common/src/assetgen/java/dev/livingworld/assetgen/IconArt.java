package dev.livingworld.assetgen;

/**
 * icon.png - 128x128 flat emblem: a low sun behind a snow-capped peak, a rain cloud on its shoulder and a
 * line of footprints leading across the snow. Built from distance fields, supersampled 4x4.
 */
final class IconArt {
    private IconArt() {}

    static final int N = 128, SS = 4;

    private static final double[][] SKY = {
            {0.00, 30, 46, 86}, {0.45, 50, 82, 136}, {0.74, 98, 124, 168}, {0.90, 232, 168, 124}, {1.00, 250, 196, 132}};

    static Img generate() {
        // footprints: alternating left/right along a curve, shrinking with distance
        final int steps = 5;
        final double[][] prints = new double[steps][5]; // x, y, rx, ry, rot
        for (int i = 0; i < steps; i++) {
            double t = 0.04 + i * 0.215, mt = 1 - t;
            double x = mt * mt * 36 + 2 * mt * t * 62 + t * t * 70;
            double y = mt * mt * 116.5 + 2 * mt * t * 115 + t * t * 99.5;
            double tx = 2 * mt * (62 - 36) + 2 * t * (70 - 62), ty = 2 * mt * (115 - 116.5) + 2 * t * (99.5 - 115);
            double tl = M.len(tx, ty);
            tx /= tl; ty /= tl;
            double size = M.lerp(4.6, 2.5, t);
            double side = (i % 2 == 0 ? 1 : -1) * size * 0.95;
            prints[i][0] = x - ty * side;
            prints[i][1] = y + tx * side;
            prints[i][2] = size * 0.72;
            prints[i][3] = size * 1.18;
            prints[i][4] = M.atan2(ty, tx) + M.PI / 2;
        }

        Img img = new Img(N, N);
        double[] c = new double[3];
        for (int py = 0; py < N; py++) {
            for (int px = 0; px < N; px++) {
                double ar = 0, ag = 0, ab = 0, aa = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        double x = px + (sx + 0.5) / SS, y = py + (sy + 0.5) / SS;
                        // rounded-square badge
                        double qx = Math.abs(x - 64) - (60 - 24), qy = Math.abs(y - 64) - (60 - 24);
                        double badge = M.len(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - 24;
                        if (badge > 0) continue;
                        colour(x, y, prints, c);
                        ar += c[0]; ag += c[1]; ab += c[2]; aa += 1;
                    }
                }
                if (aa > 0) img.set(px, py, ar / aa / 255.0, ag / aa / 255.0, ab / aa / 255.0, aa / (SS * SS));
                else img.set(px, py, 0, 0, 0, 0);
            }
        }
        img.bleed(0, 0, N, N, 0.2, 0.3, 0.5);
        return img;
    }

    private static void set(double[] c, double r, double g, double b) { c[0] = r; c[1] = g; c[2] = b; }

    private static void mix(double[] c, double r, double g, double b, double k) {
        c[0] = M.lerp(c[0], r, k); c[1] = M.lerp(c[1], g, k); c[2] = M.lerp(c[2], b, k);
    }

    private static double tri(double v) { return Math.abs(M.fract(v) * 2 - 1) * 2 - 1; } // -1..1 zig-zag

    private static void colour(double x, double y, double[][] prints, double[] c) {
        // --- sky ---
        M.ramp(M.sat((y - 4) / 92.0), SKY, c);

        // --- low sun with a flat glow ring ---
        double ds = M.len(x - 30, y - 82);
        if (ds < 26) mix(c, 255, 204, 128, 0.32);
        if (ds < 20.5) mix(c, 255, 214, 132, 0.45);
        if (ds < 15) set(c, 255, 222, 120);

        // --- mountain ---
        double apexX = 72, apexY = 29;
        if (y > apexY) {
            double dy = y - apexY;
            double left = apexX - dy * 0.66 - 2.0 * M.sin(dy * 0.12);
            double right = apexX + dy * 0.64 + 1.6 * M.sin(dy * 0.1 + 1.0);
            if (x > left && x < right) {
                double ridge = apexX + dy * 0.12 + 3.0 * tri(dy / 22.0 + 0.25);  // broken ridge line
                boolean lit = x < ridge;
                if (lit) set(c, 116, 136, 178); else set(c, 70, 86, 130);
                // snow cap with a zig-zag lower edge
                double cap = 53 + 5.5 * tri((x - 40) / 11.0) + (lit ? 0 : 3);
                if (y < cap) {
                    if (lit) set(c, 250, 252, 255); else set(c, 200, 216, 240);
                }
            }
        }

        // --- cloud (two tone, flat base) ---
        double cl = Math.min(M.sdCircle(x, y, 88, 35, 9.5), Math.min(M.sdCircle(x, y, 99, 30, 12.5), M.sdCircle(x, y, 110, 36, 9)));
        boolean cloud = (cl < 0 && y < 45) || (x > 86 && x < 111 && y >= 36 && y < 45);
        // rain under the cloud: slanted streaks, two staggered rows
        double rain = 1e9;
        for (int i = 0; i < 3; i++) {
            double rx = 88 + i * 10.5;
            rain = Math.min(rain, M.sdSegment(x, y, rx, 51, rx - 3.2, 59.5));
            rain = Math.min(rain, M.sdSegment(x, y, rx + 3.0, 64.5, rx - 0.2, 73));
        }
        if (rain < 1.9) set(c, 150, 208, 255);
        if (cloud) {
            if (y > 39.5 + 1.2 * M.sin(x * 0.35)) set(c, 196, 208, 230); else set(c, 240, 245, 252);
        }

        // --- snowy ground ---
        double ground = 93.5 + 0.0021 * M.sq(x - 70);
        if (y > ground) {
            set(c, 234, 242, 252);
            if (y > ground + 15 + 3 * M.sin(x * 0.06 + 1.0)) set(c, 220, 232, 248);   // nearer drift, one shade down
            for (double[] p : prints) {
                if (M.sdEllipse(x, y, p[0], p[1], p[2], p[3], p[4]) < 0) set(c, 104, 128, 172);
            }
        }
    }
}
