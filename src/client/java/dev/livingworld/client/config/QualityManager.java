package dev.livingworld.client.config;

import dev.livingworld.util.LWMath;

/**
 * Turns the chosen preset, the user's density sliders and the measured frame time into the budgets the
 * effect systems actually use. When the frame rate falls under the target, spawn rates and draw counts
 * are scaled down smoothly (and restored when there is headroom again) instead of letting a storm tank
 * performance.
 */
public final class QualityManager {
    private static float adaptive = 1f;
    private static float smoothedFrameMs = 16f;
    private static long lastFrameNanos;

    private QualityManager() {
    }

    /** Call once per rendered frame. */
    public static void frame() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            float ms = (now - lastFrameNanos) / 1.0e6f;
            if (ms < 500f) {
                smoothedFrameMs += (ms - smoothedFrameMs) * 0.05f;
                ClientConfig c = ClientConfig.get();
                if (c.adaptiveQuality) {
                    float budget = 1000f / Math.max(20f, c.targetFps);
                    // Shed load quickly when over budget, recover slowly when comfortably under it.
                    if (smoothedFrameMs > budget * 1.12f) {
                        adaptive = Math.max(0.3f, adaptive - 0.0025f * Math.min(4f, smoothedFrameMs / budget));
                    } else if (smoothedFrameMs < budget * 0.9f) {
                        adaptive = Math.min(1f, adaptive + 0.0008f);
                    }
                } else {
                    adaptive = 1f;
                }
            }
        }
        lastFrameNanos = now;
    }

    public static QualityPreset preset() {
        return ClientConfig.get().preset;
    }

    /** Current adaptive scale, 0.3..1. */
    public static float adaptive() {
        return adaptive;
    }

    public static float smoothedFrameMs() {
        return smoothedFrameMs;
    }

    /** Scale for per-tick spawn rates. */
    public static float spawnScale() {
        return preset().spawnScale * ClientConfig.get().particleScale() * adaptive;
    }

    public static int maxParticles() {
        return (int) (preset().maxParticles * LWMath.clamp(ClientConfig.get().particleScale(), 0.2f, 1f));
    }

    public static int maxDrops() {
        return (int) (preset().maxDrops * adaptive);
    }

    public static int maxDecals() {
        return preset().maxDecals;
    }

    /** Radius (blocks) in which positional effects are simulated. */
    public static float effectDistance() {
        return ClientConfig.get().effectDistance;
    }

    public static void reset() {
        adaptive = 1f;
        lastFrameNanos = 0L;
    }
}
