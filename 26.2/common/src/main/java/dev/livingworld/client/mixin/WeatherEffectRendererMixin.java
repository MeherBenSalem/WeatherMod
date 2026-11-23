package dev.livingworld.client.mixin;

import dev.livingworld.client.ClientHooks;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    /** The mod draws its own wind-driven precipitation, so the vanilla rain/snow columns are emptied. */
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void livingworld$replaceWeather(Level level, int ticks, float partialTicks, Vec3 cameraPos,
                                            WeatherRenderState renderState, CallbackInfo ci) {
        if (ClientHooks.replacesVanillaPrecipitation()) {
            renderState.rainColumns.clear();
            renderState.snowColumns.clear();
        }
    }

    /** Vanilla rain splash particles and the looping rain sound are replaced by the mod's own. */
    @Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void livingworld$replaceRainSplashing(CallbackInfo ci) {
        if (ClientHooks.replacesVanillaRainAmbience()) {
            ci.cancel();
        }
    }
}
