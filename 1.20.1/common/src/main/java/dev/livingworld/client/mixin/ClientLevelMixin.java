package dev.livingworld.client.mixin;

import dev.livingworld.client.render.SkyAndFog;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void livingworld$skyColor(Vec3 cameraPos, float tickDelta, CallbackInfoReturnable<Vec3> cir) {
        Vec3 tinted = SkyAndFog.skyColor(cir.getReturnValue());
        if (tinted != null) {
            cir.setReturnValue(tinted);
        }
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void livingworld$cloudColor(float tickDelta, CallbackInfoReturnable<Vec3> cir) {
        Vec3 tinted = SkyAndFog.cloudColor(cir.getReturnValue());
        if (tinted != null) {
            cir.setReturnValue(tinted);
        }
    }
}
