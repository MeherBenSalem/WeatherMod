package dev.livingworld.assetgen;

import java.util.ArrayList;
import java.util.Random;

/**
 * fx/particles.png - 256x256, 8x8 grid of 32x32 cells, index = row * 8 + col.
 * Every sprite is described as a function of the position inside its cell and supersampled.
 */
final class ParticleAtlas {
    static final int CELL = 32, GRID = 8, SIZE = CELL * GRID, MARGIN = 2, SS = 6;

    /** Straight-alpha colour of a sprite at cell position (x, y), both in 0..32. */
    interface Shade { void at(double x, double y, double[] o); }

    /** One 32x32 cell, premultiplied while it is being composed. */
    static final class Cell {
        final double[] r = new double[CELL * CELL], g = new double[CELL * CELL];
        final double[] b = new double[CELL * CELL], a = new double[CELL * CELL];

        void over(int x, int y, double sr, double sg, double sb, double sa) {
            if (x < 0 || y < 0 || x >= CELL || y >= CELL || sa <= 0) return;
            if (sa > 1) sa = 1;
            int i = y * CELL + x;
            double k = 1 - sa;
            r[i] = sr * sa + r[i] * k;
            g[i] = sg * sa + g[i] * k;
            b[i] = sb * sa + b[i] * k;
            a[i] = sa + a[i] * k;
        }

        /** Exact, un-antialiased pixel (for crisp grains). */
        void px(int x, int y, double v, double al) { over(x, y, v, v, v, al); }

        /** Supersampled paint of a shade over the current content. */
        void paint(Shade s) {
            double[] o = new double[4];
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    double ar = 0, ag = 0, ab = 0, aa = 0;
                    for (int sy = 0; sy < SS; sy++) {
                        for (int sx = 0; sx < SS; sx++) {
                            o[0] = o[1] = o[2] = 1; o[3] = 0;
                            s.at(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS, o);
                            double al = M.sat(o[3]);
                            ar += o[0] * al; ag += o[1] * al; ab += o[2] * al; aa += al;
                        }
                    }
                    if (aa > 0) over(x, y, ar / aa, ag / aa, ab / aa, aa / (SS * SS));
                }
            }
        }

        /** One sample per pixel (for deliberately grainy sprites). */
        void paintPx(Shade s) {
            double[] o = new double[4];
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    o[0] = o[1] = o[2] = 1; o[3] = 0;
                    s.at(x + 0.5, y + 0.5, o);
                    over(x, y, o[0], o[1], o[2], M.sat(o[3]));
                }
            }
        }
    }

    private final Img img = new Img(SIZE, SIZE);
    private final StringBuilder warnings = new StringBuilder();

    static Img generate() {
        ParticleAtlas p = new ParticleAtlas();
        p.build();
        if (p.warnings.length() > 0) System.out.println("particles: sprites touching the margin -> " + p.warnings);
        return p.img;
    }

    private void commit(int idx, Cell c) {
        int cx = (idx % GRID) * CELL, cy = (idx / GRID) * CELL;
        int clipped = 0;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                int i = y * CELL + x;
                double al = M.sat(c.a[i]);
                double R = 1, G = 1, B = 1;
                if (al > 1e-6) {
                    R = M.sat(c.r[i] / al); G = M.sat(c.g[i] / al); B = M.sat(c.b[i] / al);
                }
                boolean inMargin = x < MARGIN || y < MARGIN || x >= CELL - MARGIN || y >= CELL - MARGIN;
                if (inMargin) {
                    if (al > 1.5 / 255.0) clipped++;
                    al = 0;
                }
                img.set(cx + x, cy + y, R, G, B, al);
            }
        }
        if (clipped > 0) warnings.append(idx).append('(').append(clipped).append("px) ");
        img.bleed(cx, cy, CELL, CELL, 1, 1, 1);
    }

    private void one(int idx, Shade s) {
        Cell c = new Cell();
        c.paint(s);
        commit(idx, c);
    }

    private static void g(double[] o, double v, double al) { o[0] = v; o[1] = v; o[2] = v; o[3] = al; }

    private void build() {
        for (int i = 0; i < 4; i++) one(i, dust(101 + i * 17));
        for (int i = 0; i < 4; i++) { Cell c = new Cell(); sandGrains(c, 211 + i * 13); commit(4 + i, c); }
        one(8, flakeSoft());
        one(9, flakeCrystal());
        one(10, flakeDot());
        one(11, flakeClump());
        for (int i = 0; i < 4; i++) { Cell c = new Cell(); c.paintPx(powder(307 + i * 19)); commit(12 + i, c); }
        for (int i = 0; i < 4; i++) one(16 + i, leaf(i));
        for (int i = 0; i < 4; i++) one(20 + i, splash(i));
        for (int i = 0; i < 4; i++) one(24 + i, ripple(i));
        for (int i = 0; i < 4; i++) one(28 + i, mud(i));
        for (int i = 0; i < 4; i++) one(32 + i, breath(i));
        one(36, ember(false));
        one(37, ember(true));
        one(38, ash(0));
        one(39, ash(1));
        one(40, mote(0));
        one(41, mote(1));
        one(42, mote(2));
        one(43, firefly());
        one(44, rainStreak());
        one(45, drizzle());
        one(46, blizzard());
        { Cell c = new Cell(); sandStreak(c); commit(47, c); }
        one(48, mist(0));
        one(49, mist(1));
        one(50, wisp(0));
        one(51, wisp(1));
        one(52, sparkle(4));
        one(53, sparkle(6));
        one(54, drip());
        one(55, droplet());
        one(56, grass(0));
        one(57, grass(1));
        one(58, seedFluff());
        one(59, endMote());
        one(60, smoke(0));
        one(61, smoke(1));
        one(62, bubble());
        one(63, seaSpray());
    }

    // ------------------------------------------------------------------------------------------
    // 0-3 dust puff
    // ------------------------------------------------------------------------------------------
    private static Shade dust(int seed) {
        Random rn = new Random(seed);
        final int n = 5;
        final double[] bx = new double[n], by = new double[n], br = new double[n], bw = new double[n];
        double oa = rn.nextDouble() * M.TAU;
        bx[0] = 16 + M.cos(oa) * 3.0; by[0] = 16 + M.sin(oa) * 3.0; br[0] = 2.9; bw[0] = 1.25; // dense off-centre core
        for (int i = 1; i < n; i++) {
            double an = oa + M.PI * (0.45 + rn.nextDouble() * 1.1);
            double di = 1.5 + rn.nextDouble() * 5.0;
            bx[i] = 16 + M.cos(an) * di; by[i] = 16 + M.sin(an) * di;
            br[i] = 3.0 + rn.nextDouble() * 2.2;
            bw[i] = 0.3 + rn.nextDouble() * 0.3;
        }
        final int s = seed;
        return (x, y, o) -> {
            double wx = x + (Nz.fbm(x * 0.13 + 31.7, y * 0.13, 2, s + 1) - 0.5) * 9.0;
            double wy = y + (Nz.fbm(x * 0.13, y * 0.13 + 12.9, 2, s + 2) - 0.5) * 9.0;
            double d = 0;
            for (int i = 0; i < n; i++) d += bw[i] * M.gauss(M.len(wx - bx[i], wy - by[i]), br[i]);
            double n1 = Nz.fbm(x * 0.2, y * 0.2, 3, s + 5);
            double dens = d * (0.3 + 1.4 * n1);
            double al = 1.1 * (1 - M.exp(-0.9 * dens));                       // thick core, thin veil around it
            // curling wisps: where the veil is thin it breaks up into streaks
            double n2 = Nz.fbm(wx * 0.3 + 4.0, wy * 0.3, 3, s + 9);
            al *= M.lerp(M.smooth(0.3, 0.68, n2), 1.0, M.smooth(0.3, 1.0, dens));
            al *= M.smooth(13.5, 9.0, M.len(x - 16, y - 16));
            double v = M.clamp(0.9 + 0.2 * (n1 - 0.5) + 0.07 * M.sat(dens) - 0.005 * ((x - 16) + (y - 16)), 0.78, 1.0);
            g(o, v, 0.8 * M.sat(al));
        };
    }

    // ------------------------------------------------------------------------------------------
    // 4-7 sand grain cluster (pixel exact)
    // ------------------------------------------------------------------------------------------
    private static void sandGrains(Cell c, int seed) {
        Random rn = new Random(seed);
        int n = 7 + rn.nextInt(5); // 7..11
        double[] val = new double[CELL * CELL];   // 0 = empty
        int placed = 0, guard = 0;
        while (placed < n && guard++ < 2000) {
            double an = rn.nextDouble() * M.TAU, rad = M.pow(rn.nextDouble(), 0.7) * 9.0;
            int gx = (int) Math.round(15.5 + M.cos(an) * rad), gy = (int) Math.round(15.5 + M.sin(an) * rad);
            int shape = rn.nextInt(10);
            int gw = (shape >= 4 && shape <= 6) || shape == 9 ? 2 : 1;
            int gh = shape >= 7 ? 2 : 1;
            if (gx < 6 || gy < 6 || gx + gw > 26 || gy + gh > 26) continue;
            boolean clash = false;
            for (int y = gy - 1; y <= gy + gh && !clash; y++)
                for (int x = gx - 1; x <= gx + gw; x++)
                    if (val[y * CELL + x] > 0) { clash = true; break; }
            if (clash) continue;
            double v = 0.6 + rn.nextDouble() * 0.4;
            int k = 0;
            for (int y = gy; y < gy + gh; y++) {
                for (int x = gx; x < gx + gw; x++) {
                    if (gw == 2 && gh == 2 && k == 3) { k++; continue; } // chipped corner, never a square
                    val[y * CELL + x] = M.sat(k == 0 ? v : v * 0.82 + 0.04);
                    k++;
                }
            }
            placed++;
        }
        // centre the cluster on the cell (whole pixels, so the grains stay crisp)
        double sx = 0, sy = 0, sn = 0;
        for (int y = 0; y < CELL; y++)
            for (int x = 0; x < CELL; x++)
                if (val[y * CELL + x] > 0) { sx += x + 0.5; sy += y + 0.5; sn++; }
        int ox = (int) Math.round(16 - sx / sn), oy = (int) Math.round(16 - sy / sn);
        for (int y = 0; y < CELL; y++)
            for (int x = 0; x < CELL; x++)
                if (val[y * CELL + x] > 0) c.px(x + ox, y + oy, val[y * CELL + x], 1.0);
    }

    // ------------------------------------------------------------------------------------------
    // 8-11 snowflakes
    // ------------------------------------------------------------------------------------------
    private static Shade flakeSoft() {
        return (x, y, o) -> {
            double dx = x - 16, dy = y - 16, an = M.atan2(dy, dx), d = M.len(dx, dy);
            double R = 6.3 * (1 + 0.09 * M.sin(3 * an + 0.7) + 0.06 * M.sin(5 * an + 2.1) + 0.04 * M.sin(7 * an + 4.0));
            double t = d / R;
            double core = M.smooth(1.0, 0.25, t);
            double halo = 0.22 * M.smooth(1.6, 0.8, t);
            g(o, 1.0, M.sat(core + halo * (1 - core)));
        };
    }

    private static Shade flakeCrystal() {
        ArrayList<double[]> segs = new ArrayList<>();
        final double L = 8.2;
        for (int k = 0; k < 6; k++) {
            double an = -M.PI / 2 + k * M.PI / 3;
            double ux = M.cos(an), uy = M.sin(an);
            segs.add(new double[]{16, 16, 16 + ux * L, 16 + uy * L});
            for (int j = 0; j < 2; j++) {
                double at = j == 0 ? 0.52 : 0.78, bl = j == 0 ? 2.3 : 1.5;
                double px = 16 + ux * L * at, py = 16 + uy * L * at;
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double bn = an + sgn * M.PI / 3;
                    segs.add(new double[]{px, py, px + M.cos(bn) * bl, py + M.sin(bn) * bl});
                }
            }
        }
        final double[][] sg = segs.toArray(new double[0][]);
        return (x, y, o) -> {
            double dm = 1e9;
            for (double[] q : sg) dm = Math.min(dm, M.sdSegment(x, y, q[0], q[1], q[2], q[3]));
            double dc = M.len(x - 16, y - 16);
            double line = M.smooth(0.85, 0.3, dm) * (1 - 0.28 * dc / L);
            double hub = M.smooth(2.3, 1.2, dc);
            double glow = 0.2 * M.smooth(2.4, 0.4, dm) * M.smooth(11, 6, dc);
            g(o, 1.0, M.sat(Math.max(Math.max(line, hub), glow)));
        };
    }

    private static Shade flakeDot() {
        return (x, y, o) -> {
            double dx = x - 16, dy = y - 16, an = M.atan2(dy, dx);
            double d = M.len(dx, dy) * (1 + 0.1 * M.sin(3 * an + 1.1));
            double core = M.smooth(2.7, 0.9, d);
            double halo = 0.16 * M.smooth(5.2, 2.0, d);
            g(o, 1.0, M.sat(core + halo * (1 - core)));
        };
    }

    private static Shade flakeClump() {
        return (x, y, o) -> {
            double wx = x + (Nz.value(x * 0.33, y * 0.33, 71) - 0.5) * 1.6;
            double wy = y + (Nz.value(x * 0.33 + 9, y * 0.33, 72) - 0.5) * 1.6;
            double d = M.smin(M.sdCircle(wx, wy, 13.4, 17.6, 4.7), M.sdCircle(wx, wy, 19.6, 13.4, 3.5), 2.2);
            d = M.smin(d, M.sdCircle(wx, wy, 19.0, 19.9, 2.2), 1.8);
            double core = M.smooth(0.7, -2.7, d);
            double halo = 0.2 * M.smooth(3.2, 0.0, d);
            g(o, 1.0, M.sat(core + halo * (1 - core)));
        };
    }

    // ------------------------------------------------------------------------------------------
    // 12-15 snow powder puff (one sample per pixel -> grainy)
    // ------------------------------------------------------------------------------------------
    private static Shade powder(int seed) {
        Random rn = new Random(seed);
        final int n = 5;
        final double[] bx = new double[n], by = new double[n], br = new double[n], bw = new double[n];
        double oa = rn.nextDouble() * M.TAU;
        bx[0] = 16 + M.cos(oa) * 2.2; by[0] = 16 + M.sin(oa) * 2.2; br[0] = 3.6; bw[0] = 1.0;
        for (int i = 1; i < n; i++) {
            double an = oa + M.PI * (0.4 + rn.nextDouble() * 1.2);
            double di = 1.5 + rn.nextDouble() * 4.2;
            bx[i] = 16 + M.cos(an) * di; by[i] = 16 + M.sin(an) * di;
            br[i] = 2.8 + rn.nextDouble() * 1.8;
            bw[i] = 0.5 + rn.nextDouble() * 0.3;
        }
        final int s = seed;
        return (x, y, o) -> {
            int ix = (int) x, iy = (int) y;
            double wx = x + (Nz.fbm(x * 0.15 + 3.1, y * 0.15, 2, s + 1) - 0.5) * 6.0;
            double wy = y + (Nz.fbm(x * 0.15, y * 0.15 + 7.7, 2, s + 2) - 0.5) * 6.0;
            double d = 0;
            for (int i = 0; i < n; i++) d += bw[i] * M.gauss(M.len(wx - bx[i], wy - by[i]), br[i]);
            double n1 = Nz.fbm(x * 0.22, y * 0.22, 3, s + 5);
            double dens = d * (0.4 + 1.2 * n1);
            double al = M.sat(1.1 * (1 - M.exp(-2.1 * dens))) * M.smooth(13.4, 10.0, M.len(x - 16, y - 16));
            double gr = Nz.rnd(ix, iy, s + 40), gr2 = Nz.rnd(ix, iy, s + 41);
            double out;
            if (al > 0.66) out = al * (0.7 + 0.3 * gr);                        // grainy body
            else if (al > 0.02) out = gr2 < M.pow(al / 0.66, 1.25) ? M.clamp(al * 1.4 + 0.3 * gr, 0.3, 0.95) : al * 0.12; // speckled rim
            else out = 0;
            g(o, M.lerp(0.9, 1.0, gr), M.sat(out) * 0.97);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 16-19 leaves
    // ------------------------------------------------------------------------------------------
    private static Shade leaf(final int type) {
        final double tilt = new double[]{0.44, -0.36, 0.62, -0.5}[type];
        final double Lh = new double[]{10.8, 11.0, 7.6, 10.2}[type];
        final double W = new double[]{6.3, 5.9, 5.7, 5.6}[type];
        final double stem = new double[]{3.0, 3.2, 2.4, 2.6}[type];
        final double ax = M.sin(tilt), ay = -M.cos(tilt); // axis, towards the tip
        final double qx = M.cos(tilt), qy = M.sin(tilt);  // perpendicular
        final double shift = stem * 0.5;
        return (x, y, o) -> {
            double rx = x - 16, ry = y - 16;
            double s = rx * ax + ry * ay - shift;             // blade + petiole centred on the cell
            double t = rx * qx + ry * qy;
            double u = s / Lh;
            if (type == 3) t -= 2.4 * (1 - M.sat(u * u)) - 1.0; // bent midrib of the dry leaf
            double side = t >= 0 ? 1 : -1;
            double w = 0;
            if (u > -1 && u < 1) {
                switch (type) {
                    case 0: { // oak-like, lobed
                        double base = W * M.pow(1 - u * u, 0.5) * (1 - 0.15 * u);
                        w = base * (0.79 + 0.21 * M.cos(u * 9.4 + (side > 0 ? 0.5 : 1.4)));
                        break;
                    }
                    case 1: { // birch-like, pointed with fine teeth
                        double f = M.pow(1 - u, 0.85) * M.pow(1 + u, 0.42) / 1.077;
                        w = W * f * (1 + 0.06 * (2 * M.fract(u * 7 + (side > 0 ? 0 : 0.5)) - 1));
                        break;
                    }
                    case 2: { // small round jungle leaf with a drip tip
                        double wb = W * M.sqrt(Math.max(0, 1 - M.sq((u + 0.14) / 0.86)));
                        double wt = u > 0.4 ? 1.5 * M.sat((1 - u) / 0.5) : 0;
                        w = Math.max(wb, wt);
                        break;
                    }
                    default: { // dry, ragged, one half rolled in
                        double base = W * M.pow(1 - u * u, 0.6) * (1 + 0.1 * u);
                        double rag = 1 + 0.34 * (Nz.value(u * 5.0 + 3, side * 7.0 + 10, 911) - 0.5) + 0.1 * M.sin(u * 17 + side);
                        w = base * rag * (side > 0 ? 0.6 : 1.0);
                    }
                }
            }
            double at = Math.abs(t);
            boolean in = at < w;
            boolean st = !in && s <= -Lh * 0.9 && s > -Lh - stem && at < 0.55;
            if (!in && !st) { g(o, 0.85, 0); return; }
            if (!in) { g(o, 0.66, 1); return; }
            double v = 0.9 + 0.035 * u + (side > 0 ? -0.055 : 0.045);
            // side veins, running outwards and towards the tip
            double q = (s - at * 0.85) / 3.3;
            double dq = Math.abs(q - Math.floor(q) - 0.5) * 3.3;
            v -= 0.07 * M.smooth(0.7, 0.15, dq) * M.smooth(0.3, 1.3, at);
            v -= 0.05 * M.smooth(1.3, 0.0, w - at);                       // slightly darker margin
            double mr = M.smooth(0.9, 0.3, at) * M.smooth(1.0, 0.7, u);   // midrib
            v = M.lerp(v, 0.6, mr * 0.9);
            v += (Nz.value(x * 0.5, y * 0.5, 900 + type) - 0.5) * 0.05;
            if (type == 3 && side > 0) {
                v -= 0.1;                                              // shaded, rolled half
                v += 0.2 * M.smooth(1.5, 0.3, w - at);                 // light lip of the curl
            }
            g(o, M.clamp(v, 0.6, 1.0), 1);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 20-23 water splash (side view)
    // ------------------------------------------------------------------------------------------
    private static Shade splash(int f) {
        // spikes {baseX, baseY, tipX, tipY, baseR, tipR}; drops {x1, y1, x2, y2, r}
        final double[][] spikes, drops;
        final double baseY, baseRx, baseRy, baseA, bodyA, dropA;
        final double sheetRx, sheetRy, sheetY;
        switch (f) {
            case 0:
                spikes = new double[][]{{12.6, 25.6, 10.9, 16.2, 1.5, 0.5}, {15.0, 25.6, 14.5, 10.6, 1.6, 0.5},
                        {17.4, 25.6, 18.0, 12.2, 1.6, 0.5}, {19.6, 25.6, 21.4, 16.8, 1.5, 0.5}};
                drops = new double[][]{{10.3, 13.2, 10.3, 13.2, 0.95}, {14.3, 7.6, 14.3, 7.6, 1.0}, {18.5, 9.2, 18.5, 9.2, 0.9}};
                baseY = 26.2; baseRx = 5.6; baseRy = 1.4; baseA = 0.8; bodyA = 0.92; dropA = 0.92;
                sheetRx = 4.6; sheetRy = 3.0; sheetY = 23.8;
                break;
            case 1:
                spikes = new double[][]{{9.8, 26, 5.9, 17.2, 1.5, 0.45}, {13, 26, 10.6, 12.2, 1.5, 0.45},
                        {16, 26, 16.0, 10.2, 1.5, 0.45}, {19, 26, 21.5, 12.4, 1.5, 0.45}, {22.2, 26, 26.3, 17.4, 1.5, 0.45}};
                drops = new double[][]{{4.7, 13.4, 4.7, 13.4, 1.1}, {9.6, 8.0, 9.6, 8.0, 1.2}, {16.2, 5.6, 16.2, 5.6, 1.05},
                        {22.6, 8.0, 22.6, 8.0, 1.2}, {27.3, 13.6, 27.3, 13.6, 1.05}};
                baseY = 26.6; baseRx = 8.6; baseRy = 1.5; baseA = 0.75; bodyA = 0.88; dropA = 0.9;
                sheetRx = 7.6; sheetRy = 3.0; sheetY = 24.4;
                break;
            case 2:
                spikes = new double[0][];
                drops = new double[][]{{4.6, 14.6, 5.0, 15.6, 1.15}, {7.4, 8.8, 7.9, 9.9, 1.25}, {12.0, 5.3, 12.3, 6.4, 1.1},
                        {16.6, 4.4, 16.6, 5.4, 1.0}, {21.2, 5.9, 20.9, 7.0, 1.15}, {25.2, 9.4, 24.7, 10.5, 1.25},
                        {27.5, 15.2, 27.1, 16.2, 1.05}, {10.6, 13.2, 10.9, 13.9, 0.85}, {20.6, 13.6, 20.3, 14.3, 0.85},
                        {15.6, 10.4, 15.6, 11.0, 0.8}};
                baseY = 27.0; baseRx = 9.8; baseRy = 1.0; baseA = 0.4; bodyA = 0; dropA = 0.8;
                sheetRx = 0; sheetRy = 0; sheetY = 0;
                break;
            default:
                spikes = new double[0][];
                drops = new double[][]{{4.4, 18.6, 4.4, 20.0, 0.95}, {9.2, 11.8, 9.2, 13.0, 1.0}, {16.2, 9.2, 16.2, 10.2, 0.9},
                        {23.4, 12.4, 23.4, 13.6, 1.0}, {27.6, 19.4, 27.6, 20.8, 0.9}};
                baseY = 0; baseRx = 0; baseRy = 0; baseA = 0; bodyA = 0; dropA = 0.42;
                sheetRx = 0; sheetRy = 0; sheetY = 0;
        }
        return (x, y, o) -> {
            double al = 0;
            if (spikes.length > 0) {
                double d = 1e9;
                for (double[] s : spikes) d = M.smin(d, M.sdTaper(x, y, s[0], s[1], s[4], s[2], s[3], s[5]), 1.3);
                if (d < 0) al = bodyA * (0.72 + 0.28 * M.smooth(-1.1, -0.1, d)); // glassy: rim denser than core
            }
            if (sheetRx > 0 && y < baseY && M.sdEllipse(x, y, 16, sheetY, sheetRx, sheetRy, 0) < 0) al = Math.max(al, 0.5);
            if (baseRx > 0 && M.sdEllipse(x, y, 16, baseY, baseRx, baseRy, 0) < 0) al = Math.max(al, baseA);
            for (double[] p : drops) {
                if (M.sdSegment(x, y, p[0], p[1], p[2], p[3]) < p[4]) { al = Math.max(al, dropA); break; }
            }
            g(o, 1.0, al);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 24-27 ripple ring (top view)
    // ------------------------------------------------------------------------------------------
    private static Shade ripple(final int f) {
        final double R = new double[]{4.0, 6.8, 9.4, 11.7}[f];
        final double th = new double[]{1.7, 1.45, 1.2, 1.0}[f];
        final double A = new double[]{0.95, 0.74, 0.5, 0.28}[f];
        return (x, y, o) -> {
            double dx = x - 16, dy = y - 16, an = M.atan2(dy, dx), d = M.len(dx, dy);
            double rr = R * (1 + 0.035 * M.sin(2 * an + 1.3 + f) + 0.022 * M.sin(3 * an + 4.1));
            double hw = th * 0.5 * (1 + 0.38 * M.sin(2 * an + 0.5 + f * 1.7) + 0.22 * M.sin(5 * an + 2.0));
            double al = M.smooth(hw + 0.45, hw - 0.35, Math.abs(d - rr)) * A * (0.84 + 0.16 * M.sin(an - 2.2));
            if (f >= 1) { // faint trailing inner ring
                double r2 = rr * 0.52, hw2 = hw * 0.7;
                al = Math.max(al, M.smooth(hw2 + 0.45, hw2 - 0.3, Math.abs(d - r2)) * A * 0.28);
            }
            g(o, 1.0, al);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 28-31 mud blobs
    // ------------------------------------------------------------------------------------------
    private static double blobField(double[][] c, double k, double x, double y) {
        double d = 1e9;
        for (double[] q : c) {
            double e = q.length == 3 ? M.sdCircle(x, y, q[0], q[1], q[2]) : M.sdTaper(x, y, q[0], q[1], q[2], q[3], q[4], q[5]);
            d = M.smin(d, e, k);
        }
        return d;
    }

    private static Shade mud(final int v) {
        final double[][] c, hl;
        final double k;
        switch (v) {
            case 0:
                c = new double[][]{{15.5, 16.6, 6.8}, {19.6, 13.0, 4.4}, {12.0, 19.8, 4.4}};
                hl = new double[][]{{13.0, 13.6, 1.4}, {19.0, 10.9, 0.75}};
                k = 3.0;
                break;
            case 1:
                c = new double[][]{{14.0, 15.2, 5.4}, {17.6, 18.2, 4.2}, {23.6, 10.4, 2.2}, {9.0, 23.8, 1.6}};
                hl = new double[][]{{12.2, 13.0, 1.2}, {23.1, 9.8, 0.6}};
                k = 2.2;
                break;
            case 2:
                c = new double[][]{{11.6, 20.4, 5.0, 21.6, 10.4, 1.7}, {24.6, 7.4, 1.15}};
                hl = new double[][]{{10.0, 18.4, 1.25}};
                k = 1.0;
                break;
            default:
                c = new double[][]{{10.4, 12.0, 3.4}, {20.2, 10.6, 2.6}, {16.4, 19.6, 4.0}, {23.6, 20.6, 2.2}, {9.0, 22.6, 1.8}};
                hl = new double[][]{{9.4, 10.9, 0.85}, {15.2, 18.0, 1.0}, {19.7, 9.9, 0.6}};
                k = 1.2;
        }
        return (x, y, o) -> {
            double wx = x + (Nz.value(x * 0.3, y * 0.3, 500 + v) - 0.5) * 1.8;
            double wy = y + (Nz.value(x * 0.3 + 40, y * 0.3, 510 + v) - 0.5) * 1.8;
            double d = blobField(c, k, wx, wy);
            if (d > 0) { g(o, 0.55, 0); return; }
            double e = 0.6;
            double gx = blobField(c, k, wx + e, wy) - blobField(c, k, wx - e, wy);
            double gy = blobField(c, k, wx, wy + e) - blobField(c, k, wx, wy - e);
            double gl = M.len(gx, gy) + 1e-6;
            gx /= gl; gy /= gl;
            double depth = M.sat(-d / 3.2);
            double light = (-gx * 0.6 - gy * 0.75) * (1 - depth * 0.8);      // dome lit from the top-left
            double val = 0.6 + 0.2 * light + 0.06 * depth;
            val *= M.lerp(0.8, 1.0, M.smooth(0.0, 1.4, -d));                 // dark wet outline
            for (double[] h : hl) val = M.lerp(val, 1.0, M.smooth(h[2], h[2] * 0.35, M.len(x - h[0], y - h[1])));
            g(o, M.clamp(val, 0.36, 1.0), 1);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 32-35 breath / steam puff
    // ------------------------------------------------------------------------------------------
    private static Shade breath(final int f) {
        final double R = new double[]{5.4, 7.8, 10.4, 13.0}[f];
        final double peak = new double[]{0.55, 0.43, 0.31, 0.2}[f];
        final double warp = 1.1 + f * 0.75;
        final double cy = 16.4 - f * 0.25;
        return (x, y, o) -> {
            double fq = 0.17 - f * 0.02;
            double wx = x + (Nz.fbm(x * fq + 5.3, y * fq, 2, 801) - 0.5) * warp * 2;
            double wy = y + (Nz.fbm(x * fq, y * fq + 8.1, 2, 802) - 0.5) * warp * 2;
            double t = M.len(wx - 16, wy - cy) / R;
            double al = M.pow(M.smooth(1.0, 0.0, t), 1.2) * (0.84 + 0.32 * Nz.fbm(x * fq * 1.2, y * fq * 1.2, 3, 803));
            al *= M.smooth(13.6, 10.5, M.len(x - 16, y - 16));
            g(o, 1.0, peak * M.sat(al));
        };
    }

    // ------------------------------------------------------------------------------------------
    // 36-37 ember / spark (coloured)
    // ------------------------------------------------------------------------------------------
    private static final double[][] EMBER = {
            {0.00, 1.00, 1.00, 0.92}, {0.20, 1.00, 0.94, 0.58}, {0.42, 1.00, 0.64, 0.15},
            {0.68, 0.93, 0.31, 0.05}, {1.00, 0.50, 0.07, 0.03}};

    private static Shade ember(final boolean elong) {
        return (x, y, o) -> {
            double dx = x - 16, dy = y - (elong ? 18.0 : 16.0);
            if (elong) { dx *= 2.3; dy *= dy > 0 ? 1.45 : 0.67; }
            double an = M.atan2(dy, dx);
            double d = M.len(dx, dy) * (1 + 0.04 * M.sin(3 * an + 1.0) + 0.035 * M.sin(5 * an + 2.6));
            double t = d / 9.0;
            M.ramp(t, EMBER, o);
            o[3] = t < 0.26 ? 1 : M.pow(M.smooth(1.0, 0.26, t), 1.15);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 38-39 ash flakes
    // ------------------------------------------------------------------------------------------
    private static Shade ash(final int v) {
        final double R0 = v == 0 ? 5.6 : 4.3, rot = v == 0 ? 0.5 : -0.9;
        final double sx = v == 0 ? 1.28 : 1.12, sy = v == 0 ? 0.78 : 0.72;
        Random rn = new Random(380 + v);
        final double p1 = rn.nextDouble() * 6.28, p2 = rn.nextDouble() * 6.28, p3 = rn.nextDouble() * 6.28;
        final double p4 = rn.nextDouble() * 6.28, p5 = rn.nextDouble() * 6.28;
        final double cs = M.cos(rot), sn = M.sin(rot);
        return (x, y, o) -> {
            double dx = x - 16, dy = y - 16;
            double ex = (dx * cs + dy * sn) / sx, ey = (-dx * sn + dy * cs) / sy;
            double an = M.atan2(ey, ex), d = M.len(ex, ey);
            double R = R0 * (1 + 0.2 * M.sin(2 * an + p1) + 0.13 * M.sin(3 * an + p2) + 0.1 * M.sin(5 * an + p3)
                    + 0.07 * M.sin(8 * an + p4) + 0.05 * M.sin(13 * an + p5));
            double e = R - d;
            if (e <= 0) { g(o, 0.8, 0); return; }
            double rag = Nz.value(x * 1.15, y * 1.15, 600 + v);
            double al = (e < 1.2 && rag < 0.4) ? 0.3 : 0.92;                 // ragged, moth-eaten rim
            double val = 0.78 + 0.26 * (Nz.fbm(x * 0.36, y * 0.36, 2, 610 + v) - 0.5) - 0.07 * M.smooth(1.6, 0.0, e);
            g(o, M.clamp(val, 0.6, 0.94), al);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 40-42 motes, 43 firefly
    // ------------------------------------------------------------------------------------------
    private static Shade mote(final int v) {
        return (x, y, o) -> {
            double dx = x - 16, dy = y - 16, an = M.atan2(dy, dx), d = M.len(dx, dy);
            double al;
            if (v == 0) {            // pollen: soft tiny dot
                double dd = d * (1 + 0.08 * M.sin(3 * an + 0.4));
                al = M.smooth(2.7, 0.5, dd) * 0.95;
            } else if (v == 1) {     // spore: dot + faint halo
                double core = M.smooth(2.1, 1.0, d);
                double halo = 0.24 * M.pow(M.smooth(7.5, 1.5, d), 1.4);
                al = core + halo * (1 - core);
            } else {                 // dust mote in a sunbeam: pin-point core, wide faint halo
                double core = M.smooth(2.3, 0.3, d);
                double halo = d < 11.5 ? 0.17 * M.sq(1 - d / 11.5) : 0;
                al = core + halo * (1 - core);
            }
            g(o, 1.0, M.sat(al));
        };
    }

    private static Shade firefly() {
        return (x, y, o) -> {
            double d = M.len(x - 16, y - 16);
            double core = M.smooth(3.3, 1.1, d);
            double halo = d < 12.8 ? 0.6 * M.pow(1 - d / 12.8, 2.1) : 0;
            double k = M.smooth(5.0, 1.0, d);
            o[0] = M.lerp(0.46, 0.97, k);
            o[1] = M.lerp(0.95, 1.00, k);
            o[2] = M.lerp(0.20, 0.60, k);
            o[3] = M.sat(core + halo * (1 - core));
        };
    }

    // ------------------------------------------------------------------------------------------
    // 44-47 streaks
    // ------------------------------------------------------------------------------------------
    private static Shade rainStreak() {
        return (x, y, o) -> {
            double ty = (y - 2.0) / 28.0;                 // 0 at the top margin, 1 at the bottom margin
            if (ty <= 0 || ty >= 1) { g(o, 1, 0); return; }
            double head = y > 28.5 ? M.sqrt(Math.max(0, 1 - M.sq((y - 28.5) / 1.5))) : 1; // rounded leading end
            double dx = Math.abs(x - 16);
            double prof = dx < 1.0 * head ? 1.0 : 0.24 * M.smooth(2.0, 1.0, dx) * head;  // 2px core + faint skirt
            g(o, 1.0, prof * (0.03 + 0.93 * M.pow(ty, 1.5)));
        };
    }

    private static Shade drizzle() {
        return (x, y, o) -> {
            double ty = (y - 9.0) / 14.0;
            if (ty <= 0 || ty >= 1 || Math.abs(x - 16.5) >= 0.5) { g(o, 1, 0); return; }
            g(o, 1.0, 0.06 + 0.54 * M.pow(ty, 1.3));
        };
    }

    private static Shade blizzard() {
        return (x, y, o) -> {
            double dx = x - 16;
            double cross = M.gauss(dx, 1.25);
            double along;
            if (y < 22.5) along = 0.72 * M.pow(M.sat((y - 4.0) / 18.5), 1.3);
            else along = 0.72 * M.smooth(27.6, 22.5, y);
            double al = cross * along;
            double headD = M.len(dx * 1.05, (y - 22.5) * 0.8);
            al = Math.max(al, 0.95 * M.smooth(3.0, 0.7, headD));
            g(o, 1.0, M.sat(al));
        };
    }

    private static void sandStreak(Cell c) {
        Random rn = new Random(4711);
        int y = 4;
        while (y < 28) {
            int gx = 15 + (int) Math.round((rn.nextDouble() - 0.5) * 3.4);
            int len = 1 + rn.nextInt(3);
            double v = 0.65 + rn.nextDouble() * 0.35;
            double base = 0.45 + 0.55 * (y - 4) / 24.0;                       // fainter toward the top
            for (int j = 0; j < len && y + j < 29; j++) {
                double al = (j == len - 1) ? 1.0 : 0.3 + 0.22 * j;           // bright grain at the leading end
                c.px(gx, y + j, v, al * base);
            }
            if (rn.nextDouble() < 0.4 && y + len - 1 < 29) {
                c.px(gx + (rn.nextBoolean() ? 1 : -1), y + len - 1, v * 0.85, 0.85 * base);
            }
            y += len + rn.nextInt(3);
        }
        for (int i = 0; i < 4; i++) {
            c.px(12 + rn.nextInt(8), 6 + rn.nextInt(22), 0.7 + rn.nextDouble() * 0.3, 0.7);
        }
    }

    // ------------------------------------------------------------------------------------------
    // 48-51 mist and fog
    // ------------------------------------------------------------------------------------------
    private static Shade mist(final int v) {
        final int s = 480 + v * 37;
        return (x, y, o) -> {
            double wx = x + (Nz.fbm(x * 0.1 + 2.2, y * 0.1, 2, s + 1) - 0.5) * 7.0;
            double wy = y + (Nz.fbm(x * 0.1, y * 0.1 + 6.6, 2, s + 2) - 0.5) * 7.0;
            double d = M.len(wx - 16, wy - 16) / 13.6;
            double win = d < 1 ? M.pow(1 - d * d, 1.25) : 0;                  // one soft dome, no plateau
            double n = Nz.fbm(x * 0.12, y * 0.12, 3, s + 3);
            double al = win * (0.56 + 0.84 * n);
            al *= M.smooth(13.6, 10.8, M.len(x - 16, y - 16));
            g(o, 1.0, 0.35 * M.sat(al));
        };
    }

    private static Shade wisp(final int v) {
        final int s = 520 + v * 41;
        return (x, y, o) -> {
            double yy = y + 2.2 * M.sin(x * 0.2 + (v == 0 ? 0.6 : 2.4));
            double al;
            if (v == 0) {
                double e = M.sq((x - 16) / 13.4) + M.sq((yy - 16) / 6.4);
                double win = e < 1 ? M.pow(1 - e, 1.2) : 0;
                double n = Nz.fbm(x * 0.065 + 3.0, yy * 0.46, 3, s);
                al = win * (0.22 + 1.15 * M.smooth(0.3, 0.7, n));
            } else {
                // two thinner strands, slightly offset
                double e1 = M.sq((x - 14.0) / 11.2) + M.sq((yy - 12.4) / 3.6);
                double e2 = M.sq((x - 18.0) / 11.2) + M.sq((yy - 20.0) / 4.2);
                double w1 = e1 < 1 ? M.pow(1 - e1, 1.1) : 0, w2 = e2 < 1 ? M.pow(1 - e2, 1.1) : 0;
                double n = Nz.fbm(x * 0.07 + 9.0, yy * 0.5, 3, s);
                al = Math.max(w1, w2 * 0.9) * (0.3 + 1.1 * M.smooth(0.28, 0.7, n));
            }
            al *= M.smooth(13.6, 11.0, Math.abs(x - 16)) * M.smooth(13.6, 11.0, Math.abs(y - 16));
            g(o, 1.0, 0.4 * M.sat(al));
        };
    }

    // ------------------------------------------------------------------------------------------
    // 52-53 ice crystal sparkle, 59 End mote
    // ------------------------------------------------------------------------------------------
    private static double ray(double dx, double dy, double ang, double length, double width) {
        double c = M.cos(ang), s = M.sin(ang);
        double along = Math.abs(dx * c + dy * s), perp = Math.abs(-dx * s + dy * c);
        double f = 1 - along / length;
        if (f <= 0) return 0;
        double w = width * f + 0.12;
        return M.pow(f, 1.1) * M.exp(-M.sq(perp / w));
    }

    private static Shade sparkle(final int points) {
        return (x, y, o) -> {
            double dx = x - 16, dy = y - 16, d = M.len(dx, dy);
            double v;
            if (points == 4) {
                v = Math.max(ray(dx, dy, M.PI / 2, 12.0, 0.95), ray(dx, dy, 0, 9.5, 0.9));
                v = Math.max(v, 0.5 * Math.max(ray(dx, dy, M.PI / 4, 4.5, 0.7), ray(dx, dy, -M.PI / 4, 4.5, 0.7)));
            } else {
                v = Math.max(ray(dx, dy, M.PI / 2, 11.5, 0.85),
                        Math.max(ray(dx, dy, M.PI / 6, 9.0, 0.8), ray(dx, dy, -M.PI / 6, 9.0, 0.8)));
            }
            double core = M.gauss(d, 1.25);
            double halo = 0.14 * M.smooth(6.5, 1.0, d);
            g(o, 1.0, M.sat(Math.max(Math.max(v * 1.15, core), halo)));
        };
    }

    private static Shade endMote() {
        return (x, y, o) -> {
            double dx = x - 16, dy = y - 16, d = M.len(dx, dy);
            double dm = Math.abs(dx) + Math.abs(dy) * 0.78;                   // tall diamond metric
            double core = M.smooth(4.4, 1.3, dm);
            double rays = Math.max(ray(dx, dy, M.PI / 2, 11.0, 0.8), ray(dx, dy, 0, 7.5, 0.75));
            double halo = d < 12.6 ? 0.42 * M.pow(1 - d / 12.6, 2.0) : 0;
            double solid = Math.max(core, rays);
            double k = M.sat(Math.max(M.smooth(4.6, 0.8, dm), rays * 0.8));   // 1 = hot centre
            o[0] = M.lerp(0.66, 1.00, k);
            o[1] = M.lerp(0.38, 0.90, k);
            o[2] = M.lerp(0.96, 1.00, k);
            o[3] = M.sat(solid + halo * (1 - solid));
        };
    }

    // ------------------------------------------------------------------------------------------
    // 54 drip, 55 droplet, 62 bubble, 63 sea spray
    // ------------------------------------------------------------------------------------------
    private static Shade drip() {
        return (x, y, o) -> {
            double d = M.sdTaper(x, y, 16, 21.2, 4.8, 16, 7.4, 0.35);
            if (d > 0) { g(o, 1, 0); return; }
            double rim = M.smooth(-1.6, -0.2, d);
            double al = M.lerp(0.48, 0.95, rim), v = M.lerp(0.84, 1.0, rim);
            double ca = M.smooth(2.2, 0.8, M.len(x - 17.8, y - 23.2));        // light pooled at the lower right
            al = M.lerp(al, 0.82, ca * 0.6);
            double hl = M.smooth(1.4, 0.5, M.len(x - 14.3, y - 19.4));        // specular
            al = M.lerp(al, 1.0, hl); v = M.lerp(v, 1.0, hl);
            g(o, v, al);
        };
    }

    private static Shade droplet() {
        return (x, y, o) -> {
            double d = M.sdEllipse(x, y, 16, 16.2, 4.1, 3.9, 0.3);
            if (d > 0) { g(o, 1, 0); return; }
            double rim = M.smooth(-1.4, -0.15, d);
            double al = M.lerp(0.5, 0.94, rim), v = M.lerp(0.84, 1.0, rim);
            double ca = M.smooth(1.8, 0.6, M.len(x - 17.4, y - 17.8));
            al = M.lerp(al, 0.82, ca * 0.6);
            double hl = M.smooth(1.15, 0.4, M.len(x - 14.6, y - 14.7));
            al = M.lerp(al, 1.0, hl); v = M.lerp(v, 1.0, hl);
            g(o, v, al);
        };
    }

    private static Shade bubble() {
        return (x, y, o) -> {
            double dx = x - 16, dy = y - 16, an = M.atan2(dy, dx), d = M.len(dx, dy);
            double R = 8.6 * (1 + 0.018 * M.sin(2 * an + 0.6) + 0.012 * M.sin(3 * an + 2.0));
            double ring = 0.88 * M.smooth(1.0, 0.3, Math.abs(d - R)) * (0.8 + 0.2 * M.sin(an - 2.3));
            double inside = d < R ? 0.07 + 0.07 * M.sat(d / R) : 0;
            // crescent highlight, upper left (image y is down, so that is around -135 degrees)
            double da = Math.abs(M.atan2(M.sin(an + 2.356), M.cos(an + 2.356)));
            double cres = M.smooth(0.62, 0.3, da) * M.smooth(0.85, 0.3, Math.abs(d - (R - 2.5)));
            double dot = 0.55 * M.smooth(1.0, 0.3, M.len(dx - 3.6, dy - 4.0));
            g(o, 1.0, M.sat(Math.max(Math.max(ring, inside), Math.max(cres * 0.96, dot))));
        };
    }

    private static Shade seaSpray() {
        Random rn = new Random(6363);
        final int n = 15;
        final double[][] dr = new double[n][4];
        for (int i = 0; i < n; i++) {
            double an = rn.nextDouble() * M.TAU, rad = M.pow(rn.nextDouble(), 0.75) * 10.2;
            dr[i][0] = 16 + M.cos(an) * rad;
            dr[i][1] = 16.5 + M.sin(an) * rad * 0.9;
            dr[i][2] = 0.55 + rn.nextDouble() * 0.75;
            dr[i][3] = 0.6 + rn.nextDouble() * 0.4;
        }
        return (x, y, o) -> {
            double wx = x + (Nz.fbm(x * 0.14 + 1.5, y * 0.14, 2, 631) - 0.5) * 6.0;
            double wy = y + (Nz.fbm(x * 0.14, y * 0.14 + 4.5, 2, 632) - 0.5) * 6.0;
            double d = M.len(wx - 16, wy - 16) / 11.5;
            double mistA = d < 1 ? 0.26 * M.pow(1 - d * d, 1.3) * (0.5 + Nz.fbm(x * 0.2, y * 0.2, 2, 633)) : 0;
            mistA *= M.smooth(13.6, 10.5, M.len(x - 16, y - 16));
            double al = M.sat(mistA);
            for (double[] p : dr) {
                if (M.len(x - p[0], y - p[1]) < p[2]) { al = Math.max(al, p[3]); break; }
            }
            g(o, 1.0, al);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 56-57 grass bits, 58 seed fluff
    // ------------------------------------------------------------------------------------------
    private static Shade grass(int v) {
        // {x0, y0, cx, cy, x1, y1, baseWidth, tipWidth}
        final double[][] blades = v == 0
                ? new double[][]{{10.6, 27.0, 13.4, 15.0, 22.6, 5.6, 2.3, 0.5}, {15.6, 27.4, 19.4, 21.4, 25.0, 17.4, 1.8, 0.45}}
                : new double[][]{{9.6, 26.6, 8.4, 16.0, 12.8, 6.2, 2.1, 0.5}, {15.2, 27.4, 17.8, 17.0, 16.6, 7.6, 2.2, 0.5},
                {20.2, 26.6, 23.6, 19.4, 26.6, 11.8, 1.9, 0.45}};
        final int N = 20;
        final double[][][] pts = new double[blades.length][N + 1][2];
        for (int b = 0; b < blades.length; b++) {
            double[] q = blades[b];
            for (int i = 0; i <= N; i++) {
                double t = (double) i / N, mt = 1 - t;
                pts[b][i][0] = mt * mt * q[0] + 2 * mt * t * q[2] + t * t * q[4];
                pts[b][i][1] = mt * mt * q[1] + 2 * mt * t * q[3] + t * t * q[5];
            }
        }
        return (x, y, o) -> {
            double al = 0, val = 1;
            for (int b = 0; b < blades.length; b++) {
                for (int i = 0; i < N; i++) {
                    double[] p = pts[b][i], q = pts[b][i + 1];
                    double t = M.segT(x, y, p[0], p[1], q[0], q[1]);
                    double d = M.len(x - (p[0] + (q[0] - p[0]) * t), y - (p[1] + (q[1] - p[1]) * t));
                    double tt = (i + t) / N;
                    double hw = M.lerp(blades[b][6], blades[b][7], M.pow(tt, 1.3)) * 0.5;
                    if (d < hw) {
                        al = 1;
                        val = 0.76 + 0.24 * tt - (b % 2) * 0.06 + 0.06 * M.smooth(hw, 0, d);
                    }
                }
            }
            g(o, M.clamp(val, 0.65, 1.0), al);
        };
    }

    private static Shade seedFluff() {
        final double hx = 16, hy = 15.2;
        final int n = 11;
        final double[][] hairs = new double[n][4];
        Random rn = new Random(58);
        for (int i = 0; i < n; i++) {
            double an = -M.PI / 2 + (i - (n - 1) / 2.0) * (M.PI * 0.96 / (n - 1)) + (rn.nextDouble() - 0.5) * 0.08;
            double L = 8.0 + rn.nextDouble() * 1.3;
            hairs[i][0] = hx; hairs[i][1] = hy;
            hairs[i][2] = hx + M.cos(an) * L;
            hairs[i][3] = hy + M.sin(an) * L * 0.95 + M.sq(M.cos(an)) * 1.2; // outer hairs droop a little
        }
        return (x, y, o) -> {
            if (M.sdEllipse(x, y, 16, 25.6, 1.2, 2.2, 0) < 0) { g(o, 0.58, 1); return; }       // the seed
            double al = 0, v = 0.92;
            if (M.sdSegment(x, y, 16, 23.6, hx, hy) < 0.36) { al = 0.85; v = 0.8; }            // stalk
            for (double[] h : hairs) {
                double t = M.segT(x, y, h[0], h[1], h[2], h[3]);
                double d = M.len(x - (h[0] + (h[2] - h[0]) * t), y - (h[1] + (h[3] - h[1]) * t));
                if (d < 0.36) al = Math.max(al, 0.9 - 0.3 * t);
                if (M.len(x - h[2], y - h[3]) < 0.7) al = Math.max(al, 0.8);                   // soft tip tuft
            }
            if (M.len(x - hx, y - hy) < 1.0) { al = 1; v = 0.95; }
            g(o, v, al);
        };
    }

    // ------------------------------------------------------------------------------------------
    // 60-61 smoke
    // ------------------------------------------------------------------------------------------
    private static Shade smoke(final int v) {
        final double[][] lobes = v == 0
                ? new double[][]{{13.0, 18.0, 6.4}, {19.4, 14.6, 5.9}, {15.4, 11.6, 4.4}, {21.0, 20.4, 4.3}, {10.0, 13.0, 3.5}}
                : new double[][]{{14.4, 20.0, 6.2}, {19.6, 17.0, 5.6}, {13.0, 13.4, 5.2}, {18.6, 10.4, 4.4}, {9.4, 17.4, 3.6}, {22.6, 21.8, 3.2}};
        return (x, y, o) -> {
            double wx = x + (Nz.fbm(x * 0.22 + 4.0, y * 0.22, 2, 660 + v) - 0.5) * 3.0;
            double wy = y + (Nz.fbm(x * 0.22, y * 0.22 + 2.0, 2, 670 + v) - 0.5) * 3.0;
            double d = 1e9, tl = 0, dirl = 0;
            for (double[] l : lobes) {
                double dd = M.len(wx - l[0], wy - l[1]);
                d = M.smin(d, dd - l[2], 2.0);
                double k = 1 - dd / l[2];                        // 1 at the lobe centre, 0 on its edge
                if (k > tl) { tl = k; dirl = (-(wx - l[0]) - (wy - l[1])) / l[2]; }
            }
            double al = 0.9 * M.smooth(1.3, -2.2, d) * (0.8 + 0.4 * Nz.fbm(x * 0.3, y * 0.3, 2, 680 + v));
            al *= M.smooth(13.6, 11.0, M.len(x - 16, y - 16));
            double val = 0.36 + 0.17 * M.sqrt(M.sat(tl)) + 0.09 * dirl + 0.05 * (Nz.fbm(x * 0.4, y * 0.4, 2, 690 + v) - 0.5);
            g(o, M.clamp(val, 0.35, 0.6), M.sat(al));
        };
    }
}
