package dev.livingworld.client.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a sound's base volume and pitch before the engine has resolved it (needed to schedule echoes). */
@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceAccessor {
    @Accessor("volume")
    float livingworld$getVolume();

    @Accessor("pitch")
    float livingworld$getPitch();
}
