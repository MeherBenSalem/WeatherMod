package dev.livingworld.client.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.livingworld.client.ClientHooks;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import dev.livingworld.util.Noise;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Small, physically motivated camera responses: a shiver in bitter cold, a slow heavy sway in extreme
 * heat, and buffeting in strong wind. Deliberately tiny (all well under one degree), applied to world
 * and hand alike, scaled by the camera-effects slider and removed entirely by "reduce camera motion".
 */
public final class CameraEffects {
    private CameraEffects() {
    }

    public static void apply(PoseStack matrices, float tickDelta) {
        if (!ClientHooks.active()) {
            return;
        }
        ClientConfig config = ClientConfig.get();
        float motion = config.cameraMotion();
        if (motion <= 0f || Minecraft.getInstance().isPaused()) {
            return;
        }
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        PlayerExposure body = PlayerExposure.INSTANCE;
        float t = env.ticks + tickDelta;
        float roll = 0f, pitch = 0f, yaw = 0f;

        if (config.shiver && body.cold > 0.15f) {
            // Shivers come in bursts rather than as a constant vibration.
            float burst = LWMath.smoothstep(0.42f, 0.7f, Noise.value1(t * 0.035, 0x51)) * body.cold;
            roll += (Mth.sin(t * 2.9f) * 0.2f + Mth.sin(t * 4.3f) * 0.11f) * burst;
            pitch += Mth.sin(t * 3.7f + 1.3f) * 0.11f * burst;
        }
        if (config.heatSway && body.heat > 0.1f) {
            float h = body.heat;
            roll += Mth.sin(t * 0.045f) * 0.5f * h;
            pitch += (Mth.sin(t * 0.031f + 2f) * 0.3f + Mth.sin(t * 0.16f) * 0.08f) * h;
        }
        if (config.windBuffet) {
            float w = Math.max(0f, env.windLocal - 0.45f);
            if (w > 0f) {
                roll += (Noise.value1(t * 0.08, 0x77) - 0.5f) * 1.5f * w;
                yaw += (Noise.value1(t * 0.11 + 50.0, 0x78) - 0.5f) * 0.9f * w;
            }
        }
        if (roll == 0f && pitch == 0f && yaw == 0f) {
            return;
        }
        matrices.mulPose(Axis.ZP.rotationDegrees(roll * motion));
        matrices.mulPose(Axis.XP.rotationDegrees(pitch * motion));
        matrices.mulPose(Axis.YP.rotationDegrees(yaw * motion));
    }
}
