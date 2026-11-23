package dev.livingworld.fabric.client;

import dev.livingworld.client.audio.SynthSounds;
import dev.livingworld.client.audio.synth.AmbienceSynth;
import dev.livingworld.client.platform.IClientPlatformHelper;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;

/** The common client code's view of Fabric. Named in {@code META-INF/services} and found by {@code ClientServices}. */
public final class FabricClientPlatformHelper implements IClientPlatformHelper {
    @Override
    public SynthSounds.Bed createBed(SoundEvent event, SoundSource source, AmbienceSynth synth, int bus) {
        return new Bed(event, source, synth, bus);
    }

    @Override
    public SynthSounds.Clip createClip(short[] pcm, SoundSource source, float volume, float pitch, double x, double y, double z) {
        return new Clip(pcm, source, volume, pitch, x, y, z);
    }

    /** Hands the synthesiser's stream to the sound engine through Fabric API's custom audio stream hook. */
    private static final class Bed extends SynthSounds.Bed implements FabricSoundInstance {
        Bed(SoundEvent event, SoundSource source, AmbienceSynth synth, int bus) {
            super(event, source, synth, bus);
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, ResourceLocation id, boolean repeatInstantly) {
            return openStream();
        }
    }

    private static final class Clip extends SynthSounds.Clip implements FabricSoundInstance {
        Clip(short[] pcm, SoundSource source, float volume, float pitch, double x, double y, double z) {
            super(pcm, source, volume, pitch, x, y, z);
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, ResourceLocation id, boolean repeatInstantly) {
            return openStream();
        }
    }
}
