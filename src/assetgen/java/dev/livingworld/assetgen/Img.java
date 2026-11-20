package dev.livingworld.assetgen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Float RGBA image with straight (non-premultiplied) alpha, written as 8-bit RGBA PNG. */
final class Img {
    final int w, h;
    final float[] r, g, b, a;

    Img(int w, int h) {
        this.w = w;
        this.h = h;
        r = new float[w * h];
        g = new float[w * h];
        b = new float[w * h];
        a = new float[w * h];
    }

    void set(int x, int y, double R, double G, double B, double A) {
        int i = y * w + x;
        r[i] = (float) R; g[i] = (float) G; b[i] = (float) B; a[i] = (float) A;
    }

    void fill(double R, double G, double B, double A) {
        java.util.Arrays.fill(r, (float) R);
        java.util.Arrays.fill(g, (float) G);
        java.util.Arrays.fill(b, (float) B);
        java.util.Arrays.fill(a, (float) A);
    }

    static int q(float v) {
        if (!(v > 0)) return 0;
        int i = Math.round(v * 255f);
        return i > 255 ? 255 : i;
    }

    /**
     * Gives every fully transparent pixel in the rectangle the colour of the nearest visible pixel, so linear
     * filtering never drags a dark fringe in from "empty" texels. Falls back to the given colour if the
     * rectangle has no visible pixel at all.
     */
    void bleed(int x0, int y0, int rw, int rh, double dr, double dg, double db) {
        boolean[] known = new boolean[rw * rh];
        int unknown = 0;
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int i = (y0 + y) * w + (x0 + x);
                boolean k = q(a[i]) > 0;
                known[y * rw + x] = k;
                if (!k) { a[i] = 0; unknown++; }
            }
        }
        if (unknown == rw * rh) {
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < rw; x++) {
                    int i = (y0 + y) * w + (x0 + x);
                    r[i] = (float) dr; g[i] = (float) dg; b[i] = (float) db;
                }
            }
            return;
        }
        int[] list = new int[rw * rh];
        while (unknown > 0) {
            int n = 0;
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < rw; x++) {
                    if (known[y * rw + x]) continue;
                    double sr = 0, sg = 0, sb = 0, sw = 0;
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            int nx = x + ox, ny = y + oy;
                            if (nx < 0 || ny < 0 || nx >= rw || ny >= rh || !known[ny * rw + nx]) continue;
                            double wt = (ox == 0 || oy == 0) ? 1.0 : 0.7;
                            int j = (y0 + ny) * w + (x0 + nx);
                            sr += r[j] * wt; sg += g[j] * wt; sb += b[j] * wt; sw += wt;
                        }
                    }
                    if (sw > 0) {
                        int i = (y0 + y) * w + (x0 + x);
                        // written immediately but only marked known after the pass, so a pass grows one ring
                        r[i] = (float) (sr / sw); g[i] = (float) (sg / sw); b[i] = (float) (sb / sw);
                        list[n++] = y * rw + x;
                    }
                }
            }
            if (n == 0) break;
            for (int k = 0; k < n; k++) known[list[k]] = true;
            unknown -= n;
        }
    }

    BufferedImage toBuffered() {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            px[i] = (q(a[i]) << 24) | (q(r[i]) << 16) | (q(g[i]) << 8) | q(b[i]);
        }
        bi.setRGB(0, 0, w, h, px, 0, w);
        return bi;
    }

    void save(File f) throws IOException {
        File dir = f.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        if (!ImageIO.write(toBuffered(), "png", f)) throw new IOException("no PNG writer for " + f);
    }
}
