package dev.livingworld.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

/**
 * Hooks into Minecraft's own fog and sky colours (called from mixins). These work on every renderer,
 * including Sodium and when the full-screen pass is unavailable, so storms always close in and the
 * horizon always matches the haze.
 */
public final class SkyAndFog {
    private static final float[] RGB = new float[3];

    private SkyAndFog() {
    }

    private static boolean active() {
        ClientConfig c = ClientConfig.get();
        return c.enabled && c.atmosphericFog && ClientEnvironment.INSTANCE.inWorld;
    }

    /** Pulls the fog colour toward the haze colour. Returns null when nothing changes. */
    public static float[] fogColor(Camera camera, float red, float green, float blue) {
        if (!active() || camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return null;
        }
        Atmosphere a = Atmosphere.INSTANCE;
        float mix = a.fogColorMix;
        if (mix < 0.004f) {
            return null;
        }
        RGB[0] = LWMath.lerp(red, a.hazeR, mix);
        RGB[1] = LWMath.lerp(green, a.hazeG, mix);
        RGB[2] = LWMath.lerp(blue, a.hazeB, mix);
        return RGB;
    }

    /** Caps the fog distance during sandstorms, blizzards and downpours. Runs after vanilla set its values. */
    public static void capFog(Camera camera, BackgroundRenderer.FogType fogType) {
        if (!active() || camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return;
        }
        float visibility = Atmosphere.INSTANCE.visibility;
        if (visibility == Float.MAX_VALUE) {
            return;
        }
        if (camera.getFocusedEntity() instanceof LivingEntity living
                && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS))) {
            return;
        }
        if (visibility < RenderSystem.getShaderFogEnd()) {
            RenderSystem.setShaderFogEnd(visibility);
            float start = fogType == BackgroundRenderer.FogType.FOG_SKY ? 0f : visibility * 0.1f;
            RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), start));
        }
    }

    /** Sandstorms and blizzards swallow the sky. Returns null when nothing changes. */
    public static Vec3d skyColor(Vec3d vanilla) {
        if (!active() || !ClientConfig.get().overrideSkyColors) {
            return null;
        }
        Atmosphere a = Atmosphere.INSTANCE;
        float mix = a.skyColorMix;
        if (mix < 0.004f) {
            return null;
        }
        return new Vec3d(LWMath.lerp((float) vanilla.x, a.hazeR, mix), LWMath.lerp((float) vanilla.y, a.hazeG, mix),
                LWMath.lerp((float) vanilla.z, a.hazeB, mix));
    }

    /** Clouds darken toward the storm and take on the colour of airborne dust. Returns null when nothing changes. */
    public static Vec3d cloudColor(Vec3d vanilla) {
        if (!active() || !ClientConfig.get().overrideSkyColors) {
            return null;
        }
        Atmosphere a = Atmosphere.INSTANCE;
        ClientEnvironment e = ClientEnvironment.INSTANCE;
        float mix = a.skyColorMix;
        float dark = 1f - 0.3f * e.storm * e.cloudCover;
        if (mix < 0.004f && dark > 0.995f) {
            return null;
        }
        return new Vec3d(LWMath.lerp((float) vanilla.x * dark, a.hazeR, mix), LWMath.lerp((float) vanilla.y * dark, a.hazeG, mix),
                LWMath.lerp((float) vanilla.z * dark, a.hazeB, mix));
    }
}
