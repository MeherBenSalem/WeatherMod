package dev.livingworld.client.audio.synth;

/**
 * Slow smooth random wander in -1..1, advanced once per control block. Picks a new random target roughly every
 * {@code periodSec} and glides to it through two cascaded one-poles, so the result has no corners.
 */
final class Wander {
    private float target, y1, y2;
    private int count;
    private final float k;
    private final int minBlocks, spanBlocks;

    Wander(Rng rng, float periodSec) {
        float blocks = periodSec / Dsp.BLOCK_SEC;
        minBlocks = Math.max(1, (int) (blocks * 0.5f));
        spanBlocks = Math.max(1, (int) blocks);
        k = 1f - (float) Math.exp(-1.0 / (blocks * 0.3));
        target = rng.bi();
        y1 = rng.bi() * 0.5f;
        y2 = y1;
        count = 1 + rng.below(spanBlocks);
    }

    float next(Rng rng) {
        if (--count <= 0) {
            target = rng.bi();
            count = minBlocks + rng.below(spanBlocks);
        }
        y1 += k * (target - y1);
        y2 += k * (y1 - y2);
        float v = y2 * 1.35f;
        return v > 1f ? 1f : (v < -1f ? -1f : v);
    }
}
