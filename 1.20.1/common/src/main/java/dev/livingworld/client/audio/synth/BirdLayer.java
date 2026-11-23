package dev.livingworld.client.audio.synth;

/**
 * Daytime birdsong: a handful of individual birds, each of one of five "species" (two-note call, descending
 * sweeps, trill, melodic whistle, cheeps) built from sine chirps with frequency envelopes. Each bird sings a few
 * phrases from one perch, with long random pauses, then moves (new species, pan and distance).
 */
final class BirdLayer {
    private static final int N = Dsp.BLOCK;
    private static final int COUNT = 5;
    private static final float GAIN = 0.10f;
    private static final float SEND = 0.26f;
    private static final float[] MELODY = {2100f, 2360f, 2800f, 3150f, 3540f, 3980f};

    private final Bird[] birds = new Bird[COUNT];

    BirdLayer(Rng rng) {
        for (int i = 0; i < COUNT; i++) {
            birds[i] = new Bird(new Rng(rng.nextInt() * 31L + i));
        }
    }

    /** @return true if any bird wrote signal (and reverb send) this block. */
    boolean render(float activity, float[] outL, float[] outR, float[] sendL, float[] sendR) {
        boolean any = false;
        for (int i = 0; i < COUNT; i++) {
            float presence = Dsp.clamp01((activity - i * 0.17f) * 6f);
            any |= birds[i].render(activity, presence, outL, outR, sendL, sendR);
        }
        return any;
    }

    private static final class Bird {
        private final Rng rng;
        private int species, phrasesLeft;
        private float gl, gr, basePitch, tempo;
        private boolean sounding;
        private int wait, notesLeft, noteIdx, noteTotal, gap;
        private float phase, p, pInc, f0, f1, fb, amp, phraseGain, vibPhase, vibInc, vibDepth;

        Bird(Rng rng) {
            this.rng = rng;
            wait = (int) (Dsp.SR * (0.3f + 6f * rng.uni()));
        }

        private void relocate() {
            species = rng.below(5);
            float pan = rng.bi() * 0.9f;
            float dist = 0.35f + 0.65f * rng.uni();
            gl = Dsp.panL(pan) * dist * GAIN;
            gr = Dsp.panR(pan) * dist * GAIN;
            basePitch = 0.9f + 0.2f * rng.uni();
            if (species == 1 && basePitch > 1.05f) {
                basePitch = 1.05f;
            }
            phrasesLeft = 2 + rng.below(5);
        }

        private void beginPhrase() {
            if (phrasesLeft <= 0) {
                relocate();
            }
            phrasesLeft--;
            noteIdx = 0;
            tempo = 0.92f + 0.16f * rng.uni();
            switch (species) {
                case 0:
                    noteTotal = 2 * (2 + rng.below(3));
                    break;
                case 1:
                    noteTotal = 5 + rng.below(5);
                    break;
                case 2:
                    noteTotal = 12 + rng.below(15);
                    break;
                case 3:
                    noteTotal = 3 + rng.below(4);
                    break;
                default:
                    noteTotal = 1 + rng.below(4);
                    break;
            }
            notesLeft = noteTotal;
        }

        private void note(float fStart, float fEnd, float bump, float durSec, float gapSec, float a) {
            f0 = fStart * Dsp.INV_SR;
            f1 = fEnd * Dsp.INV_SR;
            fb = bump * Dsp.INV_SR;
            pInc = 1f / (durSec * tempo * Dsp.SR);
            gap = (int) (gapSec * tempo * Dsp.SR);
            amp = a;
            p = 0f;
            vibDepth = 0f;
        }

        private void beginNote() {
            int i = noteIdx++;
            notesLeft--;
            float bp = basePitch;
            switch (species) {
                case 0: { // two-note "tee-too"
                    boolean hi = (i & 1) == 0;
                    float f = (hi ? 4300f : 3350f) * bp;
                    note(f, f * 0.975f, 0f, hi ? 0.13f : 0.17f, hi ? 0.03f : 0.095f, hi ? 1f : 0.85f);
                    break;
                }
                case 1: { // accelerating descending sweeps
                    float f = 5600f * bp * (1f - 0.03f * i);
                    note(f, f * 0.6f, 0f, 0.07f, 0.065f * (1f - 0.04f * i), 0.9f);
                    break;
                }
                case 2: { // fast trill of small up-chirps, swelling then fading
                    float f = 4500f * bp;
                    float hump = Dsp.sin(0.5f * (i + 0.5f) / noteTotal);
                    note(f, f * 1.15f, 0f, 0.028f, 0.02f, 0.35f + 0.65f * hump);
                    break;
                }
                case 3: { // slow melodic whistle with a little vibrato
                    float f = MELODY[rng.below(MELODY.length)] * bp;
                    note(f, f * (1f + 0.09f * rng.bi()), f * 0.04f * rng.bi(), 0.12f + 0.16f * rng.uni(),
                            0.04f + 0.08f * rng.uni(), 0.8f + 0.2f * rng.uni());
                    if (rng.uni() < 0.5f) {
                        vibDepth = 0.012f;
                        vibInc = (26f + 12f * rng.uni()) * Dsp.INV_SR;
                    }
                    break;
                }
                default: { // short arched cheeps, irregularly spaced
                    float f = 3600f * bp;
                    note(f, f * 1.1f, f * 0.32f, 0.065f + 0.02f * rng.uni(), 0.11f + 0.19f * rng.uni(), 0.9f);
                    break;
                }
            }
            sounding = true;
        }

        boolean render(float activity, float presence, float[] outL, float[] outR, float[] sendL, float[] sendR) {
            if (!sounding && wait >= N) {
                wait -= N;
                return false;
            }
            boolean wrote = false;
            for (int i = 0; i < N; i++) {
                if (!sounding) {
                    if (wait > 0) {
                        wait--;
                        continue;
                    }
                    if (notesLeft == 0) {
                        if (presence <= 0f) {
                            // not wanted right now: check again in a moment so we start promptly when birds return
                            wait = (int) (Dsp.SR * (0.4f + 2.2f * rng.uni()));
                            break;
                        }
                        beginPhrase();
                        phraseGain = presence;
                    }
                    beginNote();
                }
                float w = Dsp.sin(p * 0.5f);
                float f = f0 + (f1 - f0) * p + fb * 4f * p * (1f - p);
                if (vibDepth != 0f) {
                    vibPhase += vibInc;
                    if (vibPhase >= 1f) {
                        vibPhase -= 1f;
                    }
                    f *= 1f + vibDepth * Dsp.sin(vibPhase);
                }
                phase += f;
                if (phase >= 1f) {
                    phase -= 1f;
                }
                float s = Dsp.sin(phase) * w * w * amp * phraseGain;
                float l = s * gl, r = s * gr;
                outL[i] += l;
                outR[i] += r;
                sendL[i] += l * SEND;
                sendR[i] += r * SEND;
                wrote = true;
                p += pInc;
                if (p >= 1f) {
                    sounding = false;
                    if (notesLeft == 0) {
                        wait = (int) (Dsp.SR * (3f + 9f * rng.uni()) / (0.35f + activity));
                    } else {
                        wait = gap;
                    }
                }
            }
            return wrote;
        }
    }
}
