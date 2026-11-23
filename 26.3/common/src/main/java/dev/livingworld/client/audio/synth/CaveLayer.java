package dev.livingworld.client.audio.synth;

/**
 * Deep cave bed: a 35-90 Hz drone (three narrow noise resonances with very slow amplitude wander, kept mono so the
 * sub-bass stays solid) plus a faint, wide airy band drifting around 300-600 Hz.
 */
final class CaveLayer {
    private static final int N = Dsp.BLOCK;
    private static final float GAIN = 0.12f;
    private static final float[] DRONE_F = {41f, 57f, 83f};
    private static final float[] DRONE_G = {1f, 0.85f, 0.6f};
    private static final float DRONE_Q = 10f;

    private final Rng rng;
    private final float[] na = new float[N], nb = new float[N], nc = new float[N], nd = new float[N];
    private final Svf[] drone = {new Svf(), new Svf(), new Svf()};
    private final Svf drone2R = new Svf();
    private final Wander[] droneAmp = new Wander[3];
    private final Wander droneTune, airTone, airAmp;
    private final Pink pl = new Pink(), pr = new Pink();
    private final Svf airL = new Svf(), airR = new Svf();
    private float prev;

    CaveLayer(Rng rng) {
        this.rng = rng;
        for (int i = 0; i < 3; i++) {
            droneAmp[i] = new Wander(rng, 7f + 3f * i);
        }
        droneTune = new Wander(rng, 12f);
        airTone = new Wander(rng, 6f);
        airAmp = new Wander(rng, 4f);
    }

    boolean render(float cave, float[] outL, float[] outR) {
        float g1 = cave * GAIN;
        float g0 = prev;
        prev = g1;
        if (g0 == 0f && g1 == 0f) {
            return false;
        }
        rng.fill4(na, nb, nc, nd, N);
        float tune = 1f + 0.04f * droneTune.next(rng);
        float c0, c1, c2;
        // white noise through a normalised band-pass of bandwidth fc/Q is very quiet: scale back up to ~unit RMS
        float norm = 1f / Dsp.sqrt(0.3333f * Dsp.PI / (DRONE_Q * Dsp.SR));
        drone[0].set(DRONE_F[0] * tune, DRONE_Q);
        drone[1].set(DRONE_F[1] * tune, DRONE_Q);
        drone[2].set(DRONE_F[2] * tune, DRONE_Q);
        c0 = DRONE_G[0] * (0.6f + 0.4f * droneAmp[0].next(rng)) * norm / Dsp.sqrt(DRONE_F[0]) * 0.33f;
        c1 = DRONE_G[1] * (0.6f + 0.4f * droneAmp[1].next(rng)) * norm / Dsp.sqrt(DRONE_F[1]) * 0.33f;
        c2 = DRONE_G[2] * (0.6f + 0.4f * droneAmp[2].next(rng)) * norm / Dsp.sqrt(DRONE_F[2]) * 0.33f;
        float fa = 420f * Dsp.exp2(0.5f * airTone.next(rng));
        airL.set(fa * 0.97f, 2.5f);
        airR.set(fa * 1.03f, 2.5f);
        float ca = 0.18f * (0.65f + 0.35f * airAmp.next(rng));
        // the two lowest bands stay mono; the top band is partly decorrelated so the drone has some width
        drone2R.set(DRONE_F[2] * tune * 1.02f, DRONE_Q);
        Svf d0 = drone[0], d1 = drone[1], d2 = drone[2], d2r = drone2R;
        float g = g0, gs = (g1 - g0) * (1f / N);
        for (int i = 0; i < N; i++) {
            float a = na[i], b = nb[i], c = nc[i] * 0.6f;
            float d = d0.bpn(a) * c0 + d1.bpn(b) * c1;
            float m = nd[i] * 0.5f;
            float al = airL.bp(pl.next(m + a * 0.85f)) * ca;
            float ar = airR.bp(pr.next(m + b * 0.85f)) * ca;
            outL[i] += (d + d2.bpn(c + a * 0.8f) * c2 + al) * g;
            outR[i] += (d + d2r.bpn(c + b * 0.8f) * c2 + ar) * g;
            g += gs;
        }
        return true;
    }
}
