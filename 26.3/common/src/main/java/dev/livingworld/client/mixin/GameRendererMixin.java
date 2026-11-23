package dev.livingworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingworld.client.player.CameraEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Shiver, heat sway and wind buffeting ride along with the vanilla hurt-tilt, for both world and hand. */
    @Inject(method = "bobHurt", at = @At("RETURN"), require = 0)
    private void livingworld$cameraFeedback(CameraRenderState cameraState, PoseStack matrices, CallbackInfo ci) {
        CameraEffects.apply(matrices, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
