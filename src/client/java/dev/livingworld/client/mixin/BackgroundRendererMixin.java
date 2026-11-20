package dev.livingworld.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.client.render.SkyAndFog;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Shadow
    private static float red;
    @Shadow
    private static float green;
    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("RETURN"))
    private static void livingworld$tintFog(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness,
                                            CallbackInfo ci) {
        float[] rgb = SkyAndFog.fogColor(camera, red, green, blue);
        if (rgb != null) {
            red = rgb[0];
            green = rgb[1];
            blue = rgb[2];
            RenderSystem.clearColor(red, green, blue, 0f);
        }
    }

    @Inject(method = "applyFog", at = @At("RETURN"))
    private static void livingworld$capFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog,
                                           float tickDelta, CallbackInfo ci) {
        SkyAndFog.capFog(camera, fogType);
    }
}
