package dev.livingworld.client.mixin;

import dev.livingworld.client.audio.EnvironmentAudio;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    /** Lets the environment audio delay thunder by its distance and add cave echoes. */
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void livingworld$onPlay(SoundInstance sound, CallbackInfo ci) {
        if (EnvironmentAudio.INSTANCE.intercept(sound)) {
            ci.cancel();
        }
    }
}
