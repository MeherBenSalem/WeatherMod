package dev.livingworld.client.audio;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.audio.synth.AmbienceSynth;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Bridges the procedural synthesiser into Minecraft's sound engine. Each sound is an ordinary
 * {@link SoundInstance} (so the game's volume sliders, pausing and device switching all apply) whose
 * audio data comes from code instead of a file, through Fabric's custom audio stream hook.
 */
final class SynthSounds {
    static final SoundEvent AMBIENT_BED = SoundEvent.of(LivingWorld.id("ambient_bed"));
    static final SoundEvent WEATHER_BED = SoundEvent.of(LivingWorld.id("weather_bed"));
    static final Identifier ONE_SHOT = LivingWorld.id("one_shot");

    private SynthSounds() {
    }

    /** Endless stereo stream rendered on demand from one bus of the synthesiser. */
    private static final class BedStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(AmbienceSynth.SAMPLE_RATE, 16, 2, true, false);
        /** 150 ms per buffer: the engine keeps four queued, so parameter changes are heard within about half a second. */
        private static final int FRAMES = AmbienceSynth.SAMPLE_RATE * 15 / 100;

        private final AmbienceSynth synth;
        private final int bus;
        private final short[] pcm = new short[FRAMES * 2];
        private final ByteBuffer bytes = BufferUtils.createByteBuffer(FRAMES * 4);

        BedStream(AmbienceSynth synth, int bus) {
            this.synth = synth;
            this.bus = bus;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer getBuffer(int size) {
            synth.render(bus, pcm, FRAMES);
            // OpenAL copies the data when it is queued, so one buffer is reused for the life of the stream.
            bytes.clear();
            bytes.asShortBuffer().put(pcm);
            bytes.position(0).limit(FRAMES * 4);
            return bytes;
        }

        @Override
        public void close() {
        }
    }

    /** A continuously playing, non-positional bed. */
    static final class Bed extends MovingSoundInstance implements FabricSoundInstance {
        private final AmbienceSynth synth;
        private final int bus;

        Bed(SoundEvent event, SoundCategory category, AmbienceSynth synth, int bus) {
            super(event, category, SoundInstance.createRandom());
            this.synth = synth;
            this.bus = bus;
            this.repeat = false;
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
            this.volume = 1f;
        }

        void setLevel(float level) {
            this.volume = level;
        }

        void stop() {
            setDone();
        }

        @Override
        public void tick() {
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
            return CompletableFuture.completedFuture(new BedStream(synth, bus));
        }
    }

    /** Hands a finished mono clip to the engine in one piece. */
    private static final class ClipStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(AmbienceSynth.SAMPLE_RATE, 16, 1, true, false);
        private ByteBuffer data;

        ClipStream(short[] pcm) {
            data = BufferUtils.createByteBuffer(pcm.length * 2);
            data.asShortBuffer().put(pcm);
            data.position(0).limit(pcm.length * 2);
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer getBuffer(int size) {
            ByteBuffer out = data;
            data = null;
            return out;
        }

        @Override
        public void close() {
        }
    }

    /** A short positional clip of synthesised sound. */
    static final class Clip extends AbstractSoundInstance implements FabricSoundInstance {
        private final short[] pcm;

        Clip(short[] pcm, SoundCategory category, float volume, float pitch, double x, double y, double z) {
            super(ONE_SHOT, category, SoundInstance.createRandom());
            this.pcm = pcm;
            this.volume = volume;
            this.pitch = pitch;
            this.x = x;
            this.y = y;
            this.z = z;
            this.attenuationType = AttenuationType.LINEAR;
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
            return CompletableFuture.completedFuture(new ClipStream(pcm));
        }
    }
}
