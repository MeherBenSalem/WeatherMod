package dev.livingworld.forge.client;

import dev.livingworld.client.audio.SynthSounds;
import dev.livingworld.client.audio.synth.AmbienceSynth;
import dev.livingworld.client.platform.IClientPlatformHelper;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;

/** The common client code's view of Forge. Named in {@code META-INF/services} and found by {@code ClientServices}. */
public final class ForgeClientPlatformHelper implements IClientPlatformHelper {
    @Override
    public SynthSounds.Bed createBed(SoundEvent event, SoundSource source, AmbienceSynth synth, int bus) {
        return new Bed(event, source, synth, bus);
    }

    @Override
    public SynthSounds.Clip createClip(short[] pcm, SoundSource source, float volume, float pitch, double x, double y, double z) {
        return new Clip(pcm, source, volume, pitch, x, y, z);
    }

    /** Hands the synthesiser's stream to the sound engine through the hook Forge adds to every sound instance. */
    private static final class Bed extends SynthSounds.Bed {
        Bed(SoundEvent event, SoundSource source, AmbienceSynth synth, int bus) {
            super(event, source, synth, bus);
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            return openStream();
        }
    }

    private static final class Clip extends SynthSounds.Clip {
        Clip(short[] pcm, SoundSource source, float volume, float pitch, double x, double y, double z) {
            super(pcm, source, volume, pitch, x, y, z);
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            return openStream();
        }
    }
}
