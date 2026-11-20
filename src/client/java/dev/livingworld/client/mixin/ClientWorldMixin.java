package dev.livingworld.client.mixin;

import dev.livingworld.client.render.SkyAndFog;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void livingworld$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        Vec3d tinted = SkyAndFog.skyColor(cir.getReturnValue());
        if (tinted != null) {
            cir.setReturnValue(tinted);
        }
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void livingworld$cloudColor(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        Vec3d tinted = SkyAndFog.cloudColor(cir.getReturnValue());
        if (tinted != null) {
            cir.setReturnValue(tinted);
        }
    }
}
