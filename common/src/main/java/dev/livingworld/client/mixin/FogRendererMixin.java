package dev.livingworld.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.client.render.SkyAndFog;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void livingworld$tintFog(Camera camera, float tickDelta, ClientLevel world, int viewDistance, float skyDarkness,
                                            CallbackInfo ci) {
        float[] rgb = SkyAndFog.fogColor(camera, fogRed, fogGreen, fogBlue);
        if (rgb != null) {
            fogRed = rgb[0];
            fogGreen = rgb[1];
            fogBlue = rgb[2];
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
        }
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void livingworld$capFog(Camera camera, FogRenderer.FogMode fogType, float viewDistance, boolean thickFog,
                                           float tickDelta, CallbackInfo ci) {
        SkyAndFog.capFog(camera, fogType);
    }
}
