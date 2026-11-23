package dev.livingworld.client.audio.synth;

import java.util.Arrays;

/**
 * Procedural ambience synthesiser. Everything is generated at runtime as PCM by pure Java DSP; there are no
 * recorded assets. Two independent stereo buses are rendered on demand:
 * <ul>
 *   <li>{@link #BUS_AMBIENT}: wind, foliage, sand hiss, cave, drips, life, surf, nether, end, ambient events</li>
 *   <li>{@link #BUS_WEATHER}: rain (all surfaces), snowfall hiss, thunder</li>
 * </ul>
 * The game thread steers the sound by writing {@link #params()}; all parameters are smoothed internally. The two
 * buses share nothing but that parameter block, so each may be pulled by its own stream (even from different
 * threads, as long as any one bus is only ever rendered by one thread at a time).
 */
public final class AmbienceSynth {
    public static final int SAMPLE_RATE = 44100;
    /** Wind, foliage, sand hiss, cave, drips, life, surf, nether, end, AmbientEvents. */
    public static final int BUS_AMBIENT = 0;
    /** Rain (all surfaces), snowfall hiss, thunder. */
    public static final int BUS_WEATHER = 1;

    private final AmbienceParams params = new AmbienceParams();
    private final AmbientBus ambient;
    private final WeatherBus weather;

    public AmbienceSynth(long seed) {
        ambient = new AmbientBus(params, seed);
        weather = new WeatherBus(params, seed);
    }

    public AmbienceParams params() {
        return params;
    }

    /**
     * Render {@code frames} stereo frames, interleaved L,R signed 16-bit, into {@code out[0 .. frames*2)}.
     * Called ONLY from the audio thread, each bus by its own stream, in arbitrary interleaving. Does not allocate,
     * holds a lock only for the instant it takes to pop a queued event, and never throws.
     */
    public void render(int bus, short[] out, int frames) {
        if (out == null || frames <= 0) {
            return;
        }
        if (frames > out.length >> 1) {
            frames = out.length >> 1;
        }
        Bus b = bus == BUS_AMBIENT ? ambient : (bus == BUS_WEATHER ? weather : null);
        if (b == null) {
            Arrays.fill(out, 0, frames * 2, (short) 0);
            return;
        }
        try {
            b.render(out, frames);
        } catch (RuntimeException e) {
            // cannot happen by construction; if it ever does, silence beats a dead sound thread
            Arrays.fill(out, 0, frames * 2, (short) 0);
        }
    }

    /**
     * Thread-safe. Queue a thunder event into the weather bus.
     *
     * @param distance01 0 = overhead (sharp crack then long rolling rumble) .. 1 = very far (soft low rumble only)
     * @param pan        -1..1
     * @param gain       0..1
     */
    public void triggerThunder(float distance01, float pan, float gain) {
        weather.queue.offer(0, distance01, pan, gain);
    }

    /** Thread-safe. Queue a one-off event into the ambient bus. */
    public void triggerEvent(AmbientEvent event, float pan, float gain) {
        if (event == null) {
            return;
        }
        ambient.queue.offer(event.ordinal(), pan, gain, 0f);
    }
}
