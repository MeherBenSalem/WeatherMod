package dev.livingworld.client.audio.synth;

/**
 * Thunder voices. A strike is a rolling rumble (brown noise through a low-pass that closes as the roll moves away,
 * with an amplitude made of irregular random lumps), and for near strikes an initial crackling tear, a few
 * follow-up claps and a low boom. Distance removes the crack, lowers the cutoff and softens the onset.
 */
final class ThunderLayer {
    private static final int N = Dsp.BLOCK;
    private static final int VOICES = 4;
    private static final float RUMBLE_GAIN = 0.40f, CRACK_GAIN = 1.0f, BOOM_GAIN = 0.32f;

    private final Rng rng;
    private final Voice[] voices = new Voice[VOICES];
    private final float[] na = new float[N], nb = new float[N];

    ThunderLayer(Rng rng) {
        this.rng = rng;
        for (int i = 0; i < VOICES; i++) {
            voices[i] = new Voice(new Rng(rng.nextInt() * 101L + i));
        }
    }

    void trigger(float distance, float pan, float gain) {
        distance = Dsp.clamp01(distance);
        pan = Dsp.clamp(pan, -1f, 1f);
        gain = Dsp.clamp01(gain);
        if (gain <= 0f) {
            return;
        }
        Voice v = null;
        for (int i = 0; i < VOICES; i++) {
            if (!voices[i].active) {
                v = voices[i];
                break;
            }
        }
        if (v == null) { // all busy: restart the oldest (its rumble simply carries on into the new one)
            v = voices[0];
            for (int i = 1; i < VOICES; i++) {
                if (voices[i].age > v.age) {
                    v = voices[i];
                }
            }
        }
        v.start(distance, pan, gain);
    }

    /**
     * The crack goes to the outdoor buffers (so it is muffled indoors); rumble and boom go to the direct buffers
     * scaled by {@code rumbleScale}, because low frequencies pass through walls.
     */
    boolean render(float[] outL, float[] outR, float[] dirL, float[] dirR, float rumbleScale) {
        boolean any = false;
        for (int i = 0; i < VOICES; i++) {
            Voice v = voices[i];
            if (!v.active) {
                continue;
            }
            if (!any) {
                rng.fill2(na, nb, N);
                any = true;
            }
            v.render(na, nb, outL, outR, dirL, dirR, rumbleScale);
        }
        return any;
    }

    private static final class Voice {
        boolean active;
        int age;
        private final Rng rng;
        private final Svf lpL = new Svf(), lpR = new Svf(), crL = new Svf(), crR = new Svf();
        private float brownL, brownR, hpL, hpR, brK = 0.0025f, brC = 0.05f;
        private float gain, gl, gr, dl, dr, nearAmt;
        private float total, fc0, ka, lumpTarget, env1, env2, envPrev;
        private int nextLump;
        private boolean firstLump;
        private float crackAmt, crackEnv, crackDec, crackFc0;
        private int crackLen, nextCrackle, clapsLeft;
        private boolean tearing;
        private float boomPh, boomEnv, boomAtt;

        Voice(Rng rng) {
            this.rng = rng;
        }

        void start(float dist, float pan, float g) {
            float near = 1f - dist;
            nearAmt = near;
            active = true;
            age = 0;
            gain = g * (0.55f + 0.45f * near * Dsp.sqrt(near));
            gl = Dsp.panL(pan);
            gr = Dsp.panR(pan);
            dl = Dsp.panL(pan * 0.45f) * 1.3f; // the rumble is diffuse: only a gentle bias towards the strike
            dr = Dsp.panR(pan * 0.45f) * 1.3f;
            total = 3.5f + 2.5f * rng.uni() + 2.5f * near;
            fc0 = 110f + 320f * near * near;
            // noise colour: brown from ~18 Hz for far strikes, flatter (more body) up to ~60 Hz for near ones
            brK = Dsp.TWO_PI * (18f + 45f * near * near) * Dsp.INV_SR;
            brC = 0.05f * Dsp.sqrt(brK / 0.0025f);
            ka = Dsp.kBlock(0.03f + 0.22f * dist);
            nextLump = 0;
            firstLump = true;
            crackAmt = dist < 0.5f ? Dsp.pow(1f - 2f * dist, 1.2f) : 0f;
            if (crackAmt > 0f) {
                crackLen = (int) (Dsp.SR * (0.08f + 0.12f * rng.uni()));
                nextCrackle = 0;
                tearing = true;
                clapsLeft = 1 + rng.below(3);
                crackFc0 = 2500f + 9000f * (1f - 2f * dist);
                boomEnv = 1f;
                boomAtt = 0f;
                boomPh = 0f;
            } else {
                crackLen = 0;
                clapsLeft = 0;
                tearing = false;
                nextCrackle = Integer.MAX_VALUE;
            }
        }

        void render(float[] na, float[] nb, float[] outL, float[] outR, float[] dirL, float[] dirR, float rumbleScale) {
            float t = age * Dsp.INV_SR;
            // ---- rolling rumble: irregular amplitude lumps under an overall fade ----
            float ov = t < total ? 1f - Dsp.sq(t / total) : 0f;
            nextLump -= N;
            if (nextLump <= 0 && t < total) {
                float u = rng.uni();
                // a near strike keeps booming for a second or so right after the crack before it starts to roll
                float early = t < 1.5f ? nearAmt : 0f;
                lumpTarget += (firstLump ? 1f : 0.15f + 0.2f * nearAmt + 0.25f * early + 0.85f * u * u) * ov;
                firstLump = false;
                float v = rng.uni();
                nextLump = (int) (Dsp.SR * (0.12f + 0.7f * v * v) * (1f + 0.15f * t) * (1f - 0.5f * early));
            }
            lumpTarget *= 0.9968f;
            env1 += ka * (lumpTarget - env1);
            env2 += ka * (env1 - env2);
            float e1 = env2 * gain * RUMBLE_GAIN * rumbleScale;
            float e = envPrev, es = (e1 - envPrev) * (1f / N);
            envPrev = e1;
            float fc = 85f + (fc0 - 85f) * (float) Math.exp(-t / 1.6f);
            lpL.set(fc, 0.6f);
            lpR.set(fc * 1.06f, 0.6f);
            float bL = brownL, bR = brownR;
            final float bk = 1f - brK, bc = brC;
            for (int i = 0; i < N; i++) {
                bL = bL * bk + na[i] * bc;
                bR = bR * bk + nb[i] * bc;
                dirL[i] += lpL.lp(bL * 0.8f + bR * 0.3f) * e * dl;
                dirR[i] += lpR.lp(bR * 0.8f + bL * 0.3f) * e * dr;
                e += es;
            }
            brownL = bL;
            brownR = bR;

            // ---- crack: a crackling tear of sharp noise bursts, then a few duller claps ----
            if (crackAmt > 0f && (tearing || clapsLeft > 0 || crackEnv > 0f)) {
                float cf = 900f + (crackFc0 - 900f) * (float) Math.exp(-t / 0.22f);
                crL.set(cf, 0.6f);
                crR.set(cf, 0.6f);
                float cg = crackAmt * gain * CRACK_GAIN;
                float cl = cg * gl * 1.2f, cr = cg * gr * 1.2f;
                float ce = crackEnv, cd = crackDec, hl = hpL, hr = hpR;
                int nc = nextCrackle;
                for (int i = 0; i < N; i++) {
                    if (--nc <= 0) {
                        if (tearing) {
                            if (age + i < crackLen) {
                                float a = 0.45f + 0.55f * rng.uni();
                                if (a > ce) {
                                    ce = a;
                                }
                                cd = 1f - 1f / (Dsp.SR * (0.004f + 0.012f * rng.uni())); // ~exp(-1/n), no call
                                nc = (int) (Dsp.SR * (0.002f + 0.011f * rng.uni())) + 1;
                            } else {
                                tearing = false;
                                nc = (int) (Dsp.SR * (0.08f + 0.28f * rng.uni()));
                            }
                        } else if (clapsLeft > 0) {
                            clapsLeft--;
                            float a = 0.25f + 0.35f * rng.uni();
                            if (a > ce) {
                                ce = a;
                            }
                            cd = 1f - 1f / (Dsp.SR * (0.03f + 0.07f * rng.uni()));
                            nc = (int) (Dsp.SR * (0.09f + 0.3f * rng.uni()));
                        } else {
                            nc = Integer.MAX_VALUE;
                        }
                    }
                    float a = na[i] * ce, b = nb[i] * ce;
                    ce *= cd;
                    float l = crL.lp(a * 0.8f + b * 0.3f), r = crR.lp(b * 0.8f + a * 0.3f);
                    hl += 0.03f * (l - hl);
                    hr += 0.03f * (r - hr);
                    outL[i] += (l - hl) * cl;
                    outR[i] += (r - hr) * cr;
                }
                crackEnv = ce < 1e-5f ? 0f : ce;
                crackDec = cd;
                hpL = hl;
                hpR = hr;
                nextCrackle = nc;
            }

            // ---- boom: a low sine thump under the crack ----
            if (boomEnv > 0f) {
                float bg = crackAmt * gain * BOOM_GAIN * rumbleScale;
                float ph = boomPh, be = boomEnv, ba = boomAtt;
                for (int i = 0; i < N; i++) {
                    ph += (38f + 26f * be) * Dsp.INV_SR;
                    if (ph >= 1f) {
                        ph -= 1f;
                    }
                    if (ba < 1f) {
                        ba += 1f / (0.008f * Dsp.SR);
                    }
                    float y = Dsp.sin(ph) * be * (ba > 1f ? 1f : ba) * bg;
                    be *= 0.999924f; // ~0.3 s
                    dirL[i] += y * gl;
                    dirR[i] += y * gr;
                }
                boomPh = ph;
                boomEnv = be < 1e-4f ? 0f : be;
                boomAtt = ba;
            }

            age += N;
            if (t >= total && env2 < 1e-4f && crackEnv == 0f && boomEnv == 0f && clapsLeft == 0) {
                active = false;
                envPrev = 0f;
            }
        }
    }
}
