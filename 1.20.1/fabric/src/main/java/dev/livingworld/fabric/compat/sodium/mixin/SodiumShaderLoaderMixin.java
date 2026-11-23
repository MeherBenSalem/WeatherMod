package dev.livingworld.fabric.compat.sodium.mixin;

import dev.livingworld.client.compat.sodium.SodiumShaders;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium reads its shader sources from its own jar, where no resource pack reaches. This passes each
 * one through {@link SodiumShaders} as it is read, which adds the mod's code to the terrain shader and
 * leaves everything else exactly as it was.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumShaderLoaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0)
    private static void livingworld$patch(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        String source = cir.getReturnValue();
        String patched = SodiumShaders.patch(name.getNamespace(), name.getPath(), source);
        if (patched != null && !patched.equals(source)) {
            cir.setReturnValue(patched);
        }
    }
}
