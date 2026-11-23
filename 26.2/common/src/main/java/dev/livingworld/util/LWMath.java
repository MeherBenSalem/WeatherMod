package dev.livingworld.util;

/** Small allocation-free maths helpers shared by simulation and rendering code. */
public final class LWMath {
    public static final float TAU = (float) (Math.PI * 2.0);

    private LWMath() {
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Hermite step. Works for descending edges too ({@code e0 > e1}). */
    public static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    /** Frame-rate independent exponential approach of {@code current} toward {@code target}. */
    public static float approach(float current, float target, float ratePerSecond, float dtSeconds) {
        return target + (current - target) * (float) Math.exp(-ratePerSecond * dtSeconds);
    }

    /** Approach with different speeds for rising and falling. */
    public static float approach(float current, float target, float upRate, float downRate, float dtSeconds) {
        return approach(current, target, target > current ? upRate : downRate, dtSeconds);
    }

    public static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static float fract(float v) {
        return v - fastFloor(v);
    }

    /** Linear interpolation through a sorted table of (x, y) pairs laid out as x0,y0,x1,y1,... */
    public static float table(float x, float... xy) {
        int n = xy.length / 2;
        if (x <= xy[0]) {
            return xy[1];
        }
        for (int i = 1; i < n; i++) {
            float x1 = xy[i * 2];
            if (x <= x1) {
                float x0 = xy[i * 2 - 2];
                float t = (x - x0) / (x1 - x0);
                return lerp(xy[i * 2 - 1], xy[i * 2 + 1], t);
            }
        }
        return xy[n * 2 - 1];
    }
}
