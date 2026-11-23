package dev.livingworld.client.audio.synth;

/**
 * Water drips: pitched "bloops" (sine with a fast upward glide and short decay, plus a tiny impact tick) falling
 * from a handful of slowly relocating drip sites. The bus adds a cave echo through the send buffers.
 */
final class DripLayer {
    private static final int VOICES = 8;
    private static final int SITES = 5;
    private static final float GAIN = 0.11f;

    private final Rng rng;
    private final BlipVoice[] voices = new BlipVoice[VOICES];
    private final float[] sitePan = new float[SITES], sitePitch = new float[SITES];
    private float acc, thr = 1f;
    private int next, doubletIn = -1, doubletSite, moveIn;

    DripLayer(Rng rng) {
        this.rng = rng;
        for (int i = 0; i < VOICES; i++) {
            voices[i] = new BlipVoice(new Rng(rng.nextInt() * 17L + i));
        }
        for (int i = 0; i < SITES; i++) {
            moveSite(i);
        }
        moveIn = Dsp.blocks(10f);
    }

    private void moveSite(int i) {
        sitePan[i] = rng.bi() * 0.9f;
        sitePitch[i] = 650f * Dsp.exp2(1.4f * rng.uni());
    }

    private void spawn(int site, float level) {
        BlipVoice v = voices[next];
        for (int k = 0; k < VOICES; k++) {
            BlipVoice c = voices[(next + k) % VOICES];
            if (!c.active) {
                v = c;
                break;
            }
        }
        next = (next + 1) % VOICES;
        float f0 = sitePitch[site] * (0.93f + 0.14f * rng.uni());
        float size = 0.55f + 0.45f * rng.uni();
        v.begin(Dsp.clamp(sitePan[site] + 0.08f * rng.bi(), -1f, 1f), GAIN * level * size);
        v.tone(0, f0, f0 * (1.7f + 0.6f * rng.uni()), 0.03f + 0.02f * rng.uni(), 1f, 0.0015f,
                0.02f + 0.02f * rng.uni(), 0f);
        if (rng.uni() < 0.4f) {
            // drop into a pool: a lower, slightly later "plunk" underneath
            v.tone(1, f0 * 0.5f, f0 * 0.85f, 0.04f, 0.45f, 0.004f, 0.035f, 0.012f);
        }
        v.noise(BlipVoice.BP, 3500f, 1f, 1.2f, 0.5f, 0.0003f, 0.003f, 0f);
    }

    /**
     * @param send cave echo send amount (0 = dry)
     * @return true if any drip voice is sounding
     */
    boolean render(float drips, float send, float[] outL, float[] outR, float[] sendL, float[] sendR) {
        if (drips > 0f) {
            float rate = 0.12f + 3.6f * drips * Dsp.sqrt(drips);
            acc += rate * Dsp.BLOCK_SEC;
            if (acc > Rng.EXP1_CAP) {
                acc = Rng.EXP1_CAP;
            }
            if (acc >= thr) {
                acc -= thr;
                thr = rng.exp1();
                int s = rng.below(SITES);
                spawn(s, 1f);
                if (rng.uni() < 0.22f) {
                    doubletIn = Dsp.blocks(0.09f + 0.12f * rng.uni());
                    doubletSite = s;
                }
            }
            if (--moveIn <= 0) {
                moveSite(rng.below(SITES));
                moveIn = Dsp.blocks(8f + 12f * rng.uni());
            }
        }
        if (doubletIn >= 0 && --doubletIn < 0) {
            spawn(doubletSite, 0.6f);
        }
        boolean any = false;
        for (int i = 0; i < VOICES; i++) {
            BlipVoice v = voices[i];
            if (v.active) {
                v.render(outL, outR, sendL, sendR, send);
                any = true;
            }
        }
        return any;
    }
}
