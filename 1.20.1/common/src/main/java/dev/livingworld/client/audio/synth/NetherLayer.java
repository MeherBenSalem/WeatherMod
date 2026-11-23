package dev.livingworld.client.audio.synth;

/**
 * Nether bed: deep volcanic sub rumble (two low resonances), a faint wide mid "roar" and sparse viscous lava
 * blubs (slow rising low sine with a soft pop at the end).
 */
final class NetherLayer {
    private static final int N = Dsp.BLOCK;
    private static final int BLUBS = 4;
    private static final float GAIN = 0.10f;
    private static final float BLUB_GAIN = 0.16f;

    private final Rng rng;
    private final float[] na = new float[N], nb = new float[N], nc = new float[N], nd = new float[N];
    private final Svf sub = new Svf(), res = new Svf(), resR = new Svf(), roarL = new Svf(), roarR = new Svf();
    private final Pink pl = new Pink(), pr = new Pink();
    private final Wander subAmp, resAmp, resTone, roarAmp, roarTone;
    private final BlipVoice[] blubs = new BlipVoice[BLUBS];
    private float prev, acc, thr = 1f;
    private int next;

    NetherLayer(Rng rng) {
        this.rng = rng;
        subAmp = new Wander(rng, 3.5f);
        resAmp = new Wander(rng, 5f);
        resTone = new Wander(rng, 9f);
        roarAmp = new Wander(rng, 4f);
        roarTone = new Wander(rng, 6f);
        for (int i = 0; i < BLUBS; i++) {
            blubs[i] = new BlipVoice(new Rng(rng.nextInt() * 13L + i));
        }
    }

    /** A lava blub; also used for the LAVA_POP event (bigger, louder). */
    void blub(float pan, float gain, float size) {
        BlipVoice v = blubs[next];
        for (int k = 0; k < BLUBS; k++) {
            BlipVoice c = blubs[(next + k) % BLUBS];
            if (!c.active) {
                v = c;
                break;
            }
        }
        next = (next + 1) % BLUBS;
        float f0 = (130f - 55f * size) * (0.85f + 0.3f * rng.uni());
        float rise = 1.7f + 0.7f * rng.uni();
        float len = 0.09f + 0.07f * size + 0.03f * rng.uni();
        v.begin(pan, gain * BLUB_GAIN);
        v.tone(0, f0, f0 * rise, len, 1f, 0.03f + 0.02f * size, 0.07f + 0.05f * size, 0f);
        v.tone(1, f0 * 2.02f, f0 * rise * 2.02f, len, 0.22f, 0.03f, 0.06f, 0f);
        v.noise(BlipVoice.BP, 500f + 400f * rng.uni(), 1f, 2f, 0.9f, 0.002f, 0.02f, len * 1.3f);
    }

    boolean render(float nether, float[] outL, float[] outR) {
        float g1 = nether * GAIN;
        float g0 = prev;
        prev = g1;
        boolean any = false;
        if (g0 != 0f || g1 != 0f) {
            any = true;
            rng.fill4(na, nb, nc, nd, N);
            float fr = 47f * Dsp.exp2(0.3f * resTone.next(rng));
            sub.set(44f, 1.2f);
            res.set(fr, 6f);
            // normalised band-pass on white noise: rms = sqrt(pi*fc/(3*Q*SR)); bring both to comparable level
            float cs = (0.75f + 0.25f * subAmp.next(rng)) * 0.55f / Dsp.sqrt(0.3333f * Dsp.PI * 44f / (1.2f * Dsp.SR));
            float cr = (0.6f + 0.4f * resAmp.next(rng)) * 0.4f / Dsp.sqrt(0.3333f * Dsp.PI * fr / (6f * Dsp.SR));
            float froar = 200f * Dsp.exp2(0.35f * roarTone.next(rng));
            roarL.set(froar * 0.96f, 1.1f);
            roarR.set(froar * 1.04f, 1.1f);
            float ca = 0.20f * (0.7f + 0.3f * roarAmp.next(rng));
            // the sub stays mono; the wandering resonance is partly decorrelated so the rumble has some width
            resR.set(fr * 1.03f, 6f);
            float g = g0, gs = (g1 - g0) * (1f / N);
            for (int i = 0; i < N; i++) {
                float a = na[i], b = nb[i], c = nc[i];
                float d = sub.bpn(a) * cs;
                float m = nd[i] * 0.5f;
                float al = roarL.bp(pl.next(m + c * 0.85f)) * ca;
                float ar = roarR.bp(pr.next(m + a * 0.85f)) * ca;
                outL[i] += (d + res.bpn(b + c * 0.6f) * cr * 0.8f + al) * g;
                outR[i] += (d + resR.bpn(b + a * 0.6f) * cr * 0.8f + ar) * g;
                g += gs;
            }
            // sparse lava blubs
            acc += 0.45f * nether * Dsp.BLOCK_SEC;
            if (acc >= thr) {
                acc -= thr;
                thr = rng.exp1();
                blub(rng.bi() * 0.8f, nether * (0.4f + 0.6f * rng.uni()), rng.uni());
            }
        }
        for (int i = 0; i < BLUBS; i++) {
            BlipVoice v = blubs[i];
            if (v.active) {
                v.render(outL, outR, null, null, 0f);
                any = true;
            }
        }
        return any;
    }
}
