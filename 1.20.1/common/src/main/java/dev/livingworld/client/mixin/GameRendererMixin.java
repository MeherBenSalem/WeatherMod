package dev.livingworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingworld.client.player.CameraEffects;
import dev.livingworld.client.render.OverlayRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Shiver, heat sway and wind buffeting ride along with the vanilla hurt-tilt, for both world and hand. */
    @Inject(method = "bobHurt", at = @At("RETURN"))
    private void livingworld$cameraFeedback(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        CameraEffects.apply(matrices, tickDelta);
    }

    /** Lens overlay: after the world and the hand, before the HUD. */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            shift = At.Shift.AFTER))
    private void livingworld$lensOverlay(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        OverlayRenderer.render(tickDelta);
    }
}
