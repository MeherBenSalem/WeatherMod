package dev.livingworld.client.mixin;

import dev.livingworld.client.ClientHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    /** The mod draws its own wind-driven precipitation, so the vanilla rain/snow sheets are skipped. */
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void livingworld$replaceWeather(LightTexture manager, float tickDelta, double cameraX, double cameraY,
                                            double cameraZ, CallbackInfo ci) {
        if (ClientHooks.replacesVanillaPrecipitation()) {
            ci.cancel();
        }
    }

    /** Vanilla rain splash particles and the looping rain sound are replaced by the mod's own. */
    @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void livingworld$replaceRainSplashing(Camera camera, CallbackInfo ci) {
        if (ClientHooks.replacesVanillaRainAmbience()) {
            ci.cancel();
        }
    }
}
