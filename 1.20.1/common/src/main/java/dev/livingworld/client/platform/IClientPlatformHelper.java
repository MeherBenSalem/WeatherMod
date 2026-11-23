package dev.livingworld.client.platform;

import dev.livingworld.client.audio.SynthSounds;
import dev.livingworld.client.audio.synth.AmbienceSynth;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * What the client half of the common code needs from the mod loader. Kept apart from
 * {@code IPlatformHelper} so that a dedicated server never loads a class that mentions rendering or audio.
 *
 * <p>Almost everything the client needs from the loader is an event, and those are simply forwarded by
 * the loader module to {@code LivingWorldClient}. What is left is here: things the common code has to
 * ask for.
 */
public interface IClientPlatformHelper {
    /**
     * A continuously playing, non-positional sound whose audio is rendered by the synthesiser. Both
     * loaders let a sound instance hand the engine its own audio stream, each through a hook of its own
     * with a different name, so the instance has to come from the loader module.
     */
    SynthSounds.Bed createBed(SoundEvent event, SoundSource source, AmbienceSynth synth, int bus);

    /** A short positional clip of synthesised sound, handed to the engine in one piece. */
    SynthSounds.Clip createClip(short[] pcm, SoundSource source, float volume, float pitch, double x, double y, double z);
}
