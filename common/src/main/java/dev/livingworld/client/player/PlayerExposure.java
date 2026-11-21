package dev.livingworld.client.player;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.particle.Fx;
import dev.livingworld.climate.ClimateMath;
import dev.livingworld.util.LWMath;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What the environment does to the player's body, expressed only as feedback: visible breath in the
 * cold, shivering, frost creeping over the lens, heat haze of exhaustion, water running off after a
 * swim, rain on the lens. It never changes health, hunger or movement — the optional survival rules
 * live on the server.
 */
public final class PlayerExposure {
    public static final PlayerExposure INSTANCE = new PlayerExposure();
    private static final float DT = 0.05f;

    /** How soaked the player is, 0..1. */
    public float wet;
    /** Cold and heat strain on the body, 0..1 (visual only). */
    public float cold;
    public float heat;
    /** Frost on the lens, 0..1. */
    public float frost;
    /** Water droplets on the lens, 0..1. */
    public float lensDrops;
    /** Temperature the player's skin feels, including being soaked. */
    public float feelsLikeC = 15f;

    private boolean wasInWater;
    private int breathTimer;
    private int exhale;
    private int sweatTimer;

    private PlayerExposure() {
    }

    private static float rf() {
        return ThreadLocalRandom.current().nextFloat();
    }

    private static float rs() {
        return ThreadLocalRandom.current().nextFloat() * 2f - 1f;
    }

    public void reset() {
        wet = cold = heat = frost = lensDrops = 0f;
        wasInWater = false;
    }

    public void tick(Minecraft client, ClientEnvironment env) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            reset();
            return;
        }
        ClientConfig config = ClientConfig.get();
        boolean spectator = player.isSpectator();
        boolean inWater = player.isInWater();
        boolean firstPerson = client.options.getCameraType().isFirstPerson();

        // ---- wetness -------------------------------------------------------------------------
        float rainOnPlayer = env.rain * env.rainExposure;
        if (inWater) {
            wet = 1f;
        } else {
            // Rain soaks you up to a point; out of it you dry, faster in warm, moving air.
            float rainTarget = Math.min(0.75f, rainOnPlayer * 1.2f);
            float drying = 0.03f + 0.05f * LWMath.smoothstep(10f, 36f, env.ambientC) + 0.02f * env.windLocal;
            wet = rainTarget > wet
                    ? Math.min(rainTarget, wet + 0.25f * rainOnPlayer * DT)
                    : Math.max(rainTarget, wet - drying * DT);
        }

        // ---- lens droplets -------------------------------------------------------------------
        if (wasInWater && !inWater && !player.isUnderWater()) {
            lensDrops = Math.max(lensDrops, 0.9f);
        }
        float lookUp = LWMath.clamp01(-player.getXRot() / 55f);
        float rainLens = LWMath.clamp01(rainOnPlayer) * (0.12f + 0.75f * lookUp) * LWMath.smoothstep(0.2f, 0.6f, env.rain);
        float lensTarget = config.lensDroplets && firstPerson && !player.isUnderWater() ? rainLens : 0f;
        lensDrops = lensTarget > lensDrops
                ? LWMath.approach(lensDrops, lensTarget, 0.8f, DT)
                : LWMath.approach(lensDrops, lensTarget, 0.22f, DT);
        if (!config.lensDroplets || player.isUnderWater()) {
            lensDrops = 0f;
        }
        wasInWater = inWater;

        // ---- body temperature strain ---------------------------------------------------------
        feelsLikeC = ClimateMath.feelsLike(env.ambientC, env.wind.speed, env.windExposure, wet);
        float coldTarget = spectator ? 0f : ClimateMath.coldStress(feelsLikeC);
        float heatTarget = inWater ? 0f : ClimateMath.heatStress(feelsLikeC);
        cold = LWMath.approach(cold, coldTarget, 0.12f, 0.25f, DT);
        heat = LWMath.approach(heat, heatTarget, 0.1f, 0.2f, DT);

        float frostTarget = config.frostOverlay && firstPerson ? ClimateMath.frost(feelsLikeC) * (0.4f + 0.6f * env.openness) : 0f;
        frost = LWMath.approach(frost, frostTarget, 0.045f, 0.14f, DT);

        if (spectator) {
            return;
        }
        float scale = Math.min(1.5f, QualityManager.spawnScale());

        // ---- breath --------------------------------------------------------------------------
        float visibility = config.coldBreath && !player.isUnderWater() ? ClimateMath.breathVisibility(env.ambientC) : 0f;
        if (visibility > 0.05f) {
            if (exhale > 0) {
                exhale--;
                if ((exhale & 1) == 0) {
                    exhalePuff(player, visibility, firstPerson);
                }
            } else if (--breathTimer <= 0) {
                // Breathing quickens with exertion.
                boolean exerted = player.isSprinting() || player.isSwimming();
                breathTimer = (exerted ? 24 : 58) + ThreadLocalRandom.current().nextInt(exerted ? 10 : 22);
                exhale = exerted ? 8 : 12;
            }
            // Other players nearby breathe too (cheaper: a puff every few ticks on a staggered cycle).
            for (AbstractClientPlayer other : client.level.players()) {
                if (other == player || other.isSpectator() || other.isInvisible() || other.isUnderWater()) {
                    continue;
                }
                if (other.distanceToSqr(player) > 28.0 * 28.0) {
                    continue;
                }
                long phase = (env.ticks + other.getId() * 17L) % 70L;
                if (phase < 12 && (phase & 3) == 0) {
                    exhalePuff(other, visibility, false);
                }
            }
        } else {
            exhale = 0;
        }

        // ---- water running off a soaked body ---------------------------------------------------
        if (config.wetDrips && wet > 0.35f && !inWater && !(rainOnPlayer > 0.2f) && rf() < wet * 0.35f * scale) {
            Fx.droplet(player.getX() + rs() * 0.28, player.getY() + 0.5 + rf() * 1.1, player.getZ() + rs() * 0.28,
                    rs() * 0.01f, -0.02f, rs() * 0.01f, 0.025f + rf() * 0.015f);
        }

        // ---- sweat in punishing heat (seen in third person and on other players' screens) --------
        if (heat > 0.5f && !inWater && --sweatTimer <= 0) {
            sweatTimer = 30 + ThreadLocalRandom.current().nextInt(40);
            if (!firstPerson) {
                Fx.droplet(player.getX() + rs() * 0.2, player.getEyeY() - 0.1, player.getZ() + rs() * 0.2, 0f, -0.01f, 0f, 0.02f);
            }
        }
    }

    private static void exhalePuff(Player player, float visibility, boolean firstPerson) {
        Vec3 look = player.getViewVector(1f);
        Vec3 velocity = player.getDeltaMovement();
        double forward = firstPerson ? 0.42 : 0.3;
        double x = player.getX() + look.x * forward;
        double y = player.getEyeY() - 0.14 + look.y * forward;
        double z = player.getZ() + look.z * forward;
        Fx.breath(x + rs() * 0.03, y + rs() * 0.02, z + rs() * 0.03,
                (float) (look.x * 0.04 + velocity.x * 0.6), (float) (look.y * 0.04 + 0.006 + velocity.y * 0.3),
                (float) (look.z * 0.04 + velocity.z * 0.6), 0.22f + 0.4f * visibility);
    }
}
