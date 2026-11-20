package dev.livingworld.client.audio.synth;

import java.util.Arrays;

/**
 * Small 4-line feedback delay network (Householder feedback, one-pole damping) used as the light outdoor reverb
 * and as the cave echo. Goes fully idle (and costs nothing) once its tail has died away.
 */
final class Fdn {
    private final float[] d0, d1, d2, d3;
    private int i0, i1, i2, i3;
    private float s0, s1, s2, s3;
    private final float g0, g1, g2, g3, damp;
    private final int tailBlocks;
    private int tail;
    private boolean dirty;

    Fdn(int l0, int l1, int l2, int l3, float rt60Sec, float dampHz) {
        d0 = new float[l0];
        d1 = new float[l1];
        d2 = new float[l2];
        d3 = new float[l3];
        g0 = lineGain(l0, rt60Sec);
        g1 = lineGain(l1, rt60Sec);
        g2 = lineGain(l2, rt60Sec);
        g3 = lineGain(l3, rt60Sec);
        damp = 1f - (float) Math.exp(-2.0 * Math.PI * dampHz / Dsp.SR);
        tailBlocks = Dsp.blocks(rt60Sec * 1.6f);
    }

    private static float lineGain(int len, float rt60) {
        return (float) Math.pow(10.0, -3.0 * len / (rt60 * Dsp.SR));
    }

    /**
     * Feeds {@code inL/inR} (ignored unless {@code hasInput}) and adds the wet signal to {@code outL/outR}.
     *
     * @return true while the reverb is producing output
     */
    boolean process(float[] inL, float[] inR, boolean hasInput, float[] outL, float[] outR, int n) {
        if (hasInput) {
            tail = tailBlocks;
        } else if (tail > 0) {
            tail--;
        }
        if (tail == 0) {
            if (dirty) {
                Arrays.fill(d0, 0f);
                Arrays.fill(d1, 0f);
                Arrays.fill(d2, 0f);
                Arrays.fill(d3, 0f);
                s0 = s1 = s2 = s3 = 0f;
                dirty = false;
            }
            return false;
        }
        dirty = true;
        float[] b0 = d0, b1 = d1, b2 = d2, b3 = d3;
        int p0 = i0, p1 = i1, p2 = i2, p3 = i3;
        float f0 = s0, f1 = s1, f2 = s2, f3 = s3;
        float dm = damp;
        for (int i = 0; i < n; i++) {
            float a = b0[p0], b = b1[p1], c = b2[p2], d = b3[p3];
            f0 += dm * (a - f0);
            f1 += dm * (b - f1);
            f2 += dm * (c - f2);
            f3 += dm * (d - f3);
            float h = 0.5f * (f0 + f1 + f2 + f3);
            float xl = hasInput ? inL[i] : 0f;
            float xr = hasInput ? inR[i] : 0f;
            b0[p0] = xl + (f0 - h) * g0;
            b1[p1] = xr + (f1 - h) * g1;
            b2[p2] = xl + (f2 - h) * g2;
            b3[p3] = xr + (f3 - h) * g3;
            outL[i] += a + c;
            outR[i] += b + d;
            if (++p0 == b0.length) {
                p0 = 0;
            }
            if (++p1 == b1.length) {
                p1 = 0;
            }
            if (++p2 == b2.length) {
                p2 = 0;
            }
            if (++p3 == b3.length) {
                p3 = 0;
            }
        }
        i0 = p0;
        i1 = p1;
        i2 = p2;
        i3 = p3;
        s0 = f0;
        s1 = f1;
        s2 = f2;
        s3 = f3;
        return true;
    }
}
