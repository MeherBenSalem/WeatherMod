package dev.livingworld.client.mixin;

import dev.livingworld.client.ClientHooks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    /** The mod draws its own wind-driven precipitation, so the vanilla rain/snow sheets are skipped. */
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void livingworld$replaceWeather(LightmapTextureManager manager, float tickDelta, double cameraX, double cameraY,
                                            double cameraZ, CallbackInfo ci) {
        if (ClientHooks.replacesVanillaPrecipitation()) {
            ci.cancel();
        }
    }

    /** Vanilla rain splash particles and the looping rain sound are replaced by the mod's own. */
    @Inject(method = "tickRainSplashing", at = @At("HEAD"), cancellable = true)
    private void livingworld$replaceRainSplashing(Camera camera, CallbackInfo ci) {
        if (ClientHooks.replacesVanillaRainAmbience()) {
            ci.cancel();
        }
    }
}
