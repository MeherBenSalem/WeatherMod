package dev.livingworld.client.mixin;

import dev.livingworld.client.ClientHooks;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the game's flat clouds while the mod draws its own cloud layer in the atmosphere pass.
 *
 * <p>Applied late (a higher priority value) so that this check sits ahead of other mods' injections at
 * the head of the method: a renderer that draws its own clouds from there is skipped too. Optional: if
 * the method is not where it is expected, the game's clouds simply stay.
 */
@Mixin(value = WorldRenderer.class, priority = 1200)
public abstract class CloudRenderMixin {
    @Inject(method = "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void livingworld$replaceClouds(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, double cameraX,
                                           double cameraY, double cameraZ, CallbackInfo ci) {
        if (ClientHooks.replacesVanillaClouds()) {
            ci.cancel();
        }
    }
}
