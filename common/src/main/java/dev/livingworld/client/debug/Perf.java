package dev.livingworld.client.debug;

/**
 * Lightweight section timers for the mod's own work. Each is an exponentially smoothed average in
 * microseconds, cheap enough to leave on permanently; the debug overlay and the benchmark read them.
 */
public enum Perf {
    ENVIRONMENT("tick: environment"),
    FOOTPRINTS("tick: footprints"),
    PARTICLES("tick: particles"),
    AUDIO("tick: audio"),
    RENDER_DECALS("frame: decals"),
    RENDER_AIR("frame: precipitation+particles"),
    RENDER_POST("frame: atmosphere pass"),
    RENDER_OVERLAY("frame: lens overlay");

    public final String label;
    private double smoothedMicros;
    private double peakMicros;
    private long samples;
    private double totalMicros;

    Perf(String label) {
        this.label = label;
    }

    public void add(long nanos) {
        double micros = nanos / 1000.0;
        smoothedMicros += (micros - smoothedMicros) * 0.05;
        peakMicros = Math.max(peakMicros * 0.999, micros);
        samples++;
        totalMicros += micros;
    }

    public double micros() {
        return smoothedMicros;
    }

    public double peakMicros() {
        return peakMicros;
    }

    /** Mean since the last {@link #resetAll()}. */
    public double meanMicros() {
        return samples == 0 ? 0.0 : totalMicros / samples;
    }

    public static void resetAll() {
        for (Perf p : values()) {
            p.samples = 0;
            p.totalMicros = 0.0;
            p.peakMicros = 0.0;
        }
    }
}
