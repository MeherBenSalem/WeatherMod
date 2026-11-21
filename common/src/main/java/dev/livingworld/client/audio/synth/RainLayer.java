package dev.livingworld.client.audio.synth;

import java.util.Arrays;

/**
 * Rain: a three-band shaped-noise bed (the far field) plus a pool of discrete droplet grains (the near field)
 * whose rate grows steeply with intensity, so drizzle is sparse individual drops and torrential rain is a roar.
 * Each drop takes the colour of a surface picked from the surface mix. {@code roofed} cross-fades to a muffled
 * low thrum (dull roof taps over a low rumble, all behind a low-pass) with occasional distinct eave drips.
 */
final class RainLayer {
    private static final int N = Dsp.BLOCK;
    private static final int DROPS = 48;
    private static final float BED_GAIN = 0.34f;
    private static final float DROP_GAIN = 0.12f;
    private static final float THRUM_GAIN = 0.21f;
    private static final float TAP_GAIN = 0.14f;
    private static final float DRIP_GAIN = 0.10f;

    private static final int LEAVES = 0, STONE = 1, WATER = 2, WOOD = 3, SOFT = 4, ROOF_TAP = 5, EAVE_DRIP = 6;
    /** Where a drop is mixed: open air (outdoor sub-mix), behind the roof low-pass, or direct (eave drips). */
    private static final int OPEN = 0, ROOF = 1, EAVE = 2;

    private final Rng rng;
    private final float[] na = new float[N], nb = new float[N], nc = new float[N], nd = new float[N];
    private final float[] roofL = new float[N], roofR = new float[N];

    // bed
    private final Pink pkL = new Pink(), pkR = new Pink();
    private final Svf lowL = new Svf(), lowR = new Svf(), midL = new Svf(), midR = new Svf(), hiL = new Svf(),
            hiR = new Svf();
    private final Wander sheet, patter;
    private float bedPrev;

    // droplets
    private final Drop[] drops = new Drop[DROPS];
    private int next;
    private float acc, thr = 1f, tapAcc, tapThr = 1f, dripAcc, dripThr = 1f;

    // roof thrum
    private final Svf resL = new Svf(), resR = new Svf(), roofLpL = new Svf(), roofLpR = new Svf();
    private final Wander thrumAmp, thrumTone;
    private float brL, brR, thrumPrev;

    RainLayer(Rng rng) {
        this.rng = rng;
        for (int i = 0; i < DROPS; i++) {
            drops[i] = new Drop();
        }
        sheet = new Wander(rng, 1.6f);
        patter = new Wander(rng, 0.7f);
        thrumAmp = new Wander(rng, 0.5f);
        thrumTone = new Wander(rng, 4f);
        lowL.set(470f, 0.6f);
        lowR.set(495f, 0.6f);
        midL.set(1500f, 0.5f);
        midR.set(1580f, 0.5f);
        hiL.set(5400f, 0.6f);
        hiR.set(5750f, 0.6f);
        roofLpL.set(600f, 0.6f);
        roofLpR.set(640f, 0.6f);
    }

    /**
     * Open-air rain goes to the outdoor buffers, the roofed component to the direct buffers.
     *
     * @param roofed    cross-fade position: 0 = all open-air rain, 1 = only the under-a-roof sound
     * @param roofLevel level of the under-a-roof sound (roofed, reduced by enclosure and by cave depth)
     * @return true if anything was written
     */
    boolean render(float rain, float leaves, float stone, float water, float wood, float soft, float roofed,
                   float roofLevel, float[] outL, float[] outR, float[] dirL, float[] dirR) {
        // normalise the surface mix; with no surface given fall back to a neutral ground mix
        float inv = 1f / (leaves + stone + water + wood + soft + 0.05f);
        leaves = (leaves + 0.015f) * inv;
        stone = (stone + 0.015f) * inv;
        water *= inv;
        wood *= inv;
        soft = (soft + 0.02f) * inv;

        float rc = rain > 1.3f ? 1.3f : rain;
        float open = Dsp.sq(1f - roofed);

        // ---- spawn droplets ----
        if (rain > 0f) {
            float pat = patter.next(rng);
            if (open > 0f) {
                // grows steeply at first, then flattens: in a downpour the quiet drops are masked by the bed anyway
                float rate = 420f * rc * Dsp.sqrt(rc) / (1f + 0.6f * rc * rc) * (1f + 0.3f * pat * (leaves + 0.3f));
                acc += rate * Dsp.BLOCK_SEC;
                if (acc > Rng.EXP1_CAP) {
                    acc = Rng.EXP1_CAP;
                }
                while (acc >= thr) {
                    acc -= thr;
                    thr = rng.exp1();
                    float u = rng.uni();
                    int surface = u < leaves ? LEAVES : (u < leaves + stone ? STONE
                            : (u < leaves + stone + water ? WATER : (u < leaves + stone + water + wood ? WOOD : SOFT)));
                    spawn(surface, rc, open);
                }
            }
            if (roofLevel > 0f) {
                tapAcc += 120f * Dsp.pow(rc, 1.3f) * Dsp.BLOCK_SEC;
                if (tapAcc > Rng.EXP1_CAP) {
                    tapAcc = Rng.EXP1_CAP;
                }
                while (tapAcc >= tapThr) {
                    tapAcc -= tapThr;
                    tapThr = rng.exp1();
                    spawn(ROOF_TAP, rc, roofLevel);
                }
                dripAcc += (0.25f + 1.5f * rc) * roofLevel * Dsp.BLOCK_SEC;
                if (dripAcc >= dripThr) {
                    dripAcc -= dripThr;
                    dripThr = rng.exp1();
                    spawn(EAVE_DRIP, rc, roofLevel);
                }
            }
        }

        float bed1 = rc * Dsp.sqrt(rc) * (1f + 0.12f * sheet.next(rng)) * open * BED_GAIN;
        float bed0 = bedPrev;
        bedPrev = bed1;
        boolean bedOn = bed0 != 0f || bed1 != 0f;
        float thrum1 = roofLevel * Dsp.pow(rc, 1.1f) * (1f + 0.15f * thrumAmp.next(rng)) * THRUM_GAIN;
        float thrum0 = thrumPrev;
        thrumPrev = thrum1;
        boolean thrumOn = thrum0 != 0f || thrum1 != 0f;

        // ---- droplets (they ring out even if the rain has just stopped) ----
        boolean any = false, roof = thrumOn;
        for (int i = 0; i < DROPS; i++) {
            Drop d = drops[i];
            if (!d.active) {
                continue;
            }
            if (!any) {
                rng.fill4(na, nb, nc, nd, N);
                Arrays.fill(roofL, 0f);
                Arrays.fill(roofR, 0f);
                any = true;
            }
            float[] noise = (i & 1) == 0 ? nc : nd;
            if (d.route == OPEN) {
                d.render(noise, outL, outR, N);
            } else if (d.route == ROOF) {
                d.render(noise, roofL, roofR, N);
                roof = true;
            } else {
                d.render(noise, dirL, dirR, N);
            }
        }
        if (!any && (bedOn || thrumOn)) {
            rng.fill4(na, nb, nc, nd, N);
            Arrays.fill(roofL, 0f);
            Arrays.fill(roofR, 0f);
            any = true;
        }

        // ---- broadband bed ----
        if (bedOn) {
            // surface colour of the far field: soft ground dark, wood hollow low-mid, water mid, leaves mid-high,
            // stone bright; the low band also grows with intensity (the "roar" of a downpour)
            float wl = (0.06f + 1.00f * soft + 0.80f * wood + 0.35f * water + 0.10f * leaves + 0.08f * stone)
                    * (0.6f + 0.6f * rc);
            float wm = 0.10f + 0.90f * leaves + 0.90f * water + 0.45f * stone + 0.50f * wood + 0.30f * soft;
            float wh = (0.05f + 1.00f * stone + 0.55f * leaves + 0.30f * water + 0.12f * wood + 0.06f * soft) * 0.6f;
            float g = bed0, gs = (bed1 - bed0) * (1f / N);
            for (int i = 0; i < N; i++) {
                float m = nc[i] * 0.5f;
                float xl = m + na[i] * 0.87f, xr = m + nb[i] * 0.87f;
                outL[i] += (lowL.bpn(pkL.next(xl)) * wl + midL.bpn(xl) * wm + hiL.bpn(xl) * wh) * g;
                outR[i] += (lowR.bpn(pkR.next(xr)) * wl + midR.bpn(xr) * wm + hiR.bpn(xr) * wh) * g;
                g += gs;
            }
        }

        // ---- roof: low thrum plus the dull taps, all behind a low-pass ----
        if (thrumOn) {
            float fr = 110f * Dsp.exp2(0.25f * thrumTone.next(rng));
            resL.set(fr, 3f);
            resR.set(fr * 1.31f, 3.5f);
            float bl = brL, br = brR;
            float g = thrum0, gs = (thrum1 - thrum0) * (1f / N);
            for (int i = 0; i < N; i++) {
                float m = nd[i] * 0.5f;
                float xl = m + na[i] * 0.87f, xr = m + nb[i] * 0.87f;
                bl += 0.021f * (xl - bl);
                br += 0.021f * (xr - br);
                roofL[i] += (bl * 5.5f + resL.bpn(xl) * 8f) * g;
                roofR[i] += (br * 5.5f + resR.bpn(xr) * 8f) * g;
                g += gs;
            }
            brL = bl;
            brR = br;
        }
        if (roof) {
            for (int i = 0; i < N; i++) {
                dirL[i] += roofLpL.lp(roofL[i]);
                dirR[i] += roofLpR.lp(roofR[i]);
            }
            roofLpL.flush();
            roofLpR.flush();
        }
        return any;
    }

    /** @param level open-air or roofed amount at spawn time (drops are far shorter than any parameter fade) */
    private void spawn(int surface, float rc, float level) {
        Drop d = drops[next];
        for (int k = 0; k < DROPS; k++) {
            Drop c = drops[(next + k) % DROPS];
            if (!c.active) {
                d = c;
                break;
            }
        }
        next = (next + 1) % DROPS;
        float u = rng.uni();
        // most drops are small and far, a few are close and loud; heavier rain means fatter drops
        float amp = (0.12f + 0.88f * u * u * u) * (0.55f + 0.7f * rc);
        d.active = true;
        d.route = OPEN;
        d.age = 0;
        d.delay = rng.below(N);
        float pan = rng.bi() * 0.95f;
        float g = DROP_GAIN;
        switch (surface) {
            case LEAVES:
                if (rng.uni() < 0.12f) { // a fat drop falling from the canopy
                    burst(d, 900f + 700f * rng.uni(), 1.6f, 0.008f + 0.006f * rng.uni(), amp * 1.3f);
                } else {
                    burst(d, 1700f * Dsp.exp2(1.6f * rng.uni()), 1.2f + rng.uni(), 0.0025f + 0.0065f * rng.uni(), amp);
                }
                break;
            case STONE:
                burst(d, 3800f * Dsp.exp2(1.3f * rng.uni()), 2.5f + 2.5f * rng.uni(), 0.00025f + 0.00055f * rng.uni(),
                        amp * 1.15f);
                break;
            case WATER: {
                float f0 = 600f * Dsp.exp2(2f * rng.uni());
                tone(d, f0, 1.25f + 0.55f * rng.uni(), 0.006f + 0.016f * rng.uni(), amp * 0.9f);
                break;
            }
            case WOOD: {
                float f0 = 200f + 300f * rng.uni();
                float ratio = 2.3f + 0.6f * rng.uni();
                burst(d, f0, 8f + 6f * rng.uni(), 0.0004f, amp * 1.2f);
                d.kind = Drop.BURST2;
                d.f2.reset();
                d.f2.set(f0 * ratio, 6f);
                d.gain2 = 0.4f / ratio; // an impulse rings the higher mode proportionally louder: compensate
                break;
            }
            case SOFT:
                burst(d, 220f + 430f * rng.uni(), 0.9f + 0.5f * rng.uni(), 0.003f + 0.005f * rng.uni(), amp * 1.1f);
                break;
            case ROOF_TAP:
                d.route = ROOF;
                g = TAP_GAIN;
                burst(d, 140f + 280f * rng.uni(), 2f + 1.5f * rng.uni(), 0.001f + 0.002f * rng.uni(), amp);
                break;
            default: // EAVE_DRIP: a distinct, closer drip
                d.route = EAVE;
                g = DRIP_GAIN;
                amp = 0.5f + 0.5f * rng.uni();
                pan *= 0.7f;
                if (rng.uni() < 0.5f) {
                    tone(d, 500f + 600f * rng.uni(), 1.5f, 0.03f + 0.02f * rng.uni(), amp);
                } else {
                    float f0 = 350f + 350f * rng.uni();
                    burst(d, f0, 8f, 0.0005f, amp);
                    d.kind = Drop.BURST2;
                    d.f2.reset();
                    d.f2.set(f0 * 2.7f, 6f);
                    d.gain2 = 0.15f;
                }
                break;
        }
        g *= level;
        d.gl = Dsp.panL(pan) * g;
        d.gr = Dsp.panR(pan) * g;
    }

    /** Noise burst ringing a band-pass; gain is normalised so the grain peaks at roughly {@code amp}. */
    private static void burst(Drop d, float fc, float q, float excSec, float amp) {
        d.kind = Drop.BURST;
        d.f1.reset();
        d.f1.set(fc, q);
        float w0 = Dsp.TWO_PI * fc * Dsp.INV_SR;
        float nExc = excSec * Dsp.SR, nRing = 2f * q / w0;
        float nEff = 1f / (1f / nExc + 1f / nRing);
        d.exc = 1f;
        d.excDec = (float) Math.exp(-1.0 / nExc);
        d.gain = amp / (w0 * Dsp.sqrt(nEff / 6f) * 1.6f);
        d.gain2 = 0f;
        d.life = (int) (5f * (nExc + nRing)) + 8; // ends about 43 dB down: inaudible, and keeps the pool small
    }

    /** Pitched "plip": sine with an upward chirp and a tiny noise tick at the onset. */
    private static void tone(Drop d, float f0, float rise, float decaySec, float amp) {
        d.kind = Drop.TONE;
        d.life = (int) (5f * decaySec * Dsp.SR);
        d.ph = 0f;
        d.freq = f0 * Dsp.INV_SR;
        d.dFreq = d.freq * (rise - 1f) / (3f * decaySec * Dsp.SR);
        d.env = amp;
        d.envDec = (float) Math.exp(-1.0 / (decaySec * Dsp.SR));
        d.exc = amp * 0.3f;
        d.excDec = (float) Math.exp(-1.0 / (0.0006 * Dsp.SR));
    }

    /** One droplet grain. */
    private static final class Drop {
        static final int BURST = 0, BURST2 = 1, TONE = 2;
        boolean active;
        int kind, route, delay, age, life;
        final Svf f1 = new Svf(), f2 = new Svf();
        float exc, excDec, gain, gain2, gl, gr;
        float ph, freq, dFreq, env, envDec;

        void render(float[] noise, float[] outL, float[] outR, int n) {
            int i0 = 0;
            if (delay > 0) {
                if (delay >= n) {
                    delay -= n;
                    return;
                }
                i0 = delay;
                delay = 0;
            }
            float l = gl, r = gr;
            if (kind == BURST) {
                float e = exc, d = excDec, g = gain;
                for (int i = i0; i < n; i++) {
                    float y = f1.bp(noise[i] * e) * g;
                    e *= d;
                    outL[i] += y * l;
                    outR[i] += y * r;
                }
                exc = e < 1e-9f ? 0f : e; // never let the excitation decay into denormals
            } else if (kind == BURST2) {
                float e = exc, d = excDec, g = gain, g2 = gain * gain2;
                for (int i = i0; i < n; i++) {
                    float x = noise[i] * e;
                    float y = f1.bp(x) * g + f2.bp(x) * g2;
                    e *= d;
                    outL[i] += y * l;
                    outR[i] += y * r;
                }
                exc = e < 1e-9f ? 0f : e;
            } else {
                float p = ph, f = freq, df = dFreq, e = env, d = envDec, x = exc, xd = excDec;
                for (int i = i0; i < n; i++) {
                    p += f;
                    if (p >= 1f) {
                        p -= 1f;
                    }
                    f += df;
                    float y = Dsp.sin(p) * e + noise[i] * x;
                    e *= d;
                    x *= xd;
                    outL[i] += y * l;
                    outR[i] += y * r;
                }
                ph = p;
                freq = f;
                env = e;
                exc = x < 1e-9f ? 0f : x;
            }
            age += n - i0;
            if (age >= life) {
                active = false;
            }
        }
    }
}
