package dev.livingworld.client.audio;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.audio.synth.AmbienceSynth;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Bridges the procedural synthesiser into Minecraft's sound engine. Each sound is an ordinary
 * {@link SoundInstance} (so the game's volume sliders, pausing and device switching all apply) whose
 * audio data comes from code instead of a file.
 *
 * <p>The engine asks a sound instance for its audio through a hook that each mod loader adds under a
 * name of its own (Fabric API's {@code getAudioStream}, Forge's {@code getStream}). The two classes here
 * therefore stop one step short: they know how to open their stream, and the loader module subclasses
 * them to answer its hook with it (see {@code IClientPlatformHelper}).
 */
public final class SynthSounds {
    static final SoundEvent AMBIENT_BED = SoundEvent.createVariableRangeEvent(LivingWorld.id("ambient_bed"));
    static final SoundEvent WEATHER_BED = SoundEvent.createVariableRangeEvent(LivingWorld.id("weather_bed"));
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
        public ByteBuffer read(int size) {
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
    public abstract static class Bed extends AbstractTickableSoundInstance {
        private final AmbienceSynth synth;
        private final int bus;

        protected Bed(SoundEvent event, SoundSource category, AmbienceSynth synth, int bus) {
            super(event, category, SoundInstance.createUnseededRandom());
            this.synth = synth;
            this.bus = bus;
            this.looping = false;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = 1f;
        }

        void setLevel(float level) {
            this.volume = level;
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        /** The audio of this bed: what the loader's custom-stream hook returns. */
        protected final CompletableFuture<AudioStream> openStream() {
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
        public ByteBuffer read(int size) {
            ByteBuffer out = data;
            data = null;
            return out;
        }

        @Override
        public void close() {
        }
    }

    /** A short positional clip of synthesised sound. */
    public abstract static class Clip extends AbstractSoundInstance {
        private final short[] pcm;

        protected Clip(short[] pcm, SoundSource category, float volume, float pitch, double x, double y, double z) {
            super(ONE_SHOT, category, SoundInstance.createUnseededRandom());
            this.pcm = pcm;
            this.volume = volume;
            this.pitch = pitch;
            this.x = x;
            this.y = y;
            this.z = z;
            this.attenuation = Attenuation.LINEAR;
        }

        /** The audio of this clip: what the loader's custom-stream hook returns. */
        protected final CompletableFuture<AudioStream> openStream() {
            return CompletableFuture.completedFuture(new ClipStream(pcm));
        }
    }
}
