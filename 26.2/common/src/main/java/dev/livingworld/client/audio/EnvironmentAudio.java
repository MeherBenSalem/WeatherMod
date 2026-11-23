package dev.livingworld.client.audio;

import dev.livingworld.client.platform.ClientServices;
import dev.livingworld.client.audio.synth.AmbienceParams;
import dev.livingworld.client.audio.synth.AmbienceSynth;
import dev.livingworld.client.audio.synth.AmbientEvent;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.mixin.AbstractSoundInstanceAccessor;
import dev.livingworld.client.particle.AmbientSpawner;
import dev.livingworld.climate.BiomeProfile.Kind;
import dev.livingworld.util.LWMath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The environmental soundscape. Two continuously synthesised beds (ambience and weather) are steered
 * every tick from the shared environment state, so the sound follows the same wind, rain, shelter and
 * biome blend the visuals use: rain changes timbre with the surfaces it lands on and muffles when you
 * step indoors, wind thins out in a forest and howls on a peak, birds stop when the storm arrives.
 *
 * <p>It also delays thunder by its distance and gives sounds a short echo underground. Footsteps are
 * left to the game: the mod adds no sound of its own to walking.
 */
public final class EnvironmentAudio implements AmbientSpawner.EventListener {
    public static final EnvironmentAudio INSTANCE = new EnvironmentAudio();
    private static final Identifier THUNDER = SoundEvents.LIGHTNING_BOLT_THUNDER.location();

    private record Delayed(long dueTick, Runnable action) {
    }

    private AmbienceSynth synth;
    private SynthSounds.Bed ambientBed;
    private SynthSounds.Bed weatherBed;
    private int restartCooldown;
    private boolean running;
    private long tick;
    private final List<Delayed> delayed = new ArrayList<>();
    private boolean replaying;
    private int echoBudget;
    private int dripCooldown;

    private EnvironmentAudio() {
    }

    public boolean running() {
        return running;
    }

    /** Current synthesiser parameters, for the debug overlay. Null until audio has started. */
    public AmbienceParams params() {
        return synth == null ? null : synth.params();
    }

    public void stop() {
        if (ambientBed != null) {
            ambientBed.end();
            ambientBed = null;
        }
        if (weatherBed != null) {
            weatherBed.end();
            weatherBed = null;
        }
        delayed.clear();
        running = false;
    }

    public void tick(Minecraft client, ClientEnvironment env) {
        tick++;
        ClientConfig config = ClientConfig.get();
        if (!config.enabled || !config.ambience || !env.inWorld || client.level == null) {
            if (running || ambientBed != null) {
                stop();
            }
            return;
        }
        if (synth == null) {
            synth = new AmbienceSynth(System.nanoTime());
        }
        SoundManager sounds = client.getSoundManager();
        boolean ambientPlaying = ambientBed != null && sounds.isActive(ambientBed);
        boolean weatherPlaying = weatherBed != null && sounds.isActive(weatherBed);
        if ((!ambientPlaying || !weatherPlaying) && --restartCooldown <= 0) {
            // Covers first start, device changes and the rare stream underrun after a long freeze.
            restartCooldown = 30;
            if (!ambientPlaying) {
                ambientBed = ClientServices.PLATFORM.createBed(SynthSounds.AMBIENT_BED, SoundSource.AMBIENT, synth, AmbienceSynth.BUS_AMBIENT);
                sounds.play(ambientBed);
            }
            if (!weatherPlaying) {
                weatherBed = ClientServices.PLATFORM.createBed(SynthSounds.WEATHER_BED, SoundSource.WEATHER, synth, AmbienceSynth.BUS_WEATHER);
                sounds.play(weatherBed);
            }
        }
        running = ambientBed != null && weatherBed != null && sounds.isActive(ambientBed) && sounds.isActive(weatherBed);
        if (ambientBed != null) {
            ambientBed.setLevel(config.ambienceVolume);
        }
        if (weatherBed != null) {
            weatherBed.setLevel(config.weatherVolume);
        }

        updateParams(env, config);
        recordedWildlife(client, env, config);
        randomEvents(env);

        if (env.distantThunderDistance >= 0f) {
            float distance = env.distantThunderDistance;
            float pan = env.distantThunderPan;
            schedule((int) (20 + distance * 70), () -> synth.triggerThunder(distance, pan, 0.55f));
        }
        if (echoBudget < 6 && tick % 4 == 0) {
            echoBudget++;
        }
        if (dripCooldown > 0) {
            dripCooldown--;
        }
        for (int i = delayed.size() - 1; i >= 0; i--) {
            Delayed d = delayed.get(i);
            if (tick >= d.dueTick) {
                delayed.remove(i);
                d.action.run();
            }
        }
    }

    // ---- recorded wildlife --------------------------------------------------------------------------
    // The mod ships no audio files: birds and frogs are synthesised. A resource pack may add recordings
    // to the sound events livingworld:wildlife.birds and livingworld:wildlife.frogs (see
    // docs/SAMPLE_PACK.md). Where it does, the recordings are played, placed in the world around the
    // listener, as often and as loudly as the synthesised animals would have called, and the synthesised
    // layer for that animal falls silent.

    private static final Identifier RECORDED_BIRDS = dev.livingworld.LivingWorld.id("wildlife.birds");
    private static final Identifier RECORDED_FROGS = dev.livingworld.LivingWorld.id("wildlife.frogs");
    /** Whether a pack provides recordings (checked every few seconds; packs can be changed in-game). */
    public boolean recordedBirds, recordedFrogs;
    private int birdPause, frogPause;

    private static boolean hasRecordings(Minecraft client, Identifier event) {
        net.minecraft.client.sounds.WeighedSoundEvents set = client.getSoundManager().getSoundEvent(event);
        return set != null && set.getSound(SoundInstance.createUnseededRandom()) != SoundManager.EMPTY_SOUND;
    }

    private void recordedWildlife(Minecraft client, ClientEnvironment env, ClientConfig config) {
        AmbienceParams p = params();
        if (p == null) {
            return;
        }
        if (tick % 100 == 1) {
            recordedBirds = config.recordedWildlife && hasRecordings(client, RECORDED_BIRDS);
            recordedFrogs = config.recordedWildlife && hasRecordings(client, RECORDED_FROGS);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (recordedBirds) {
            float level = p.birds;
            p.birds = 0f;
            if (--birdPause <= 0 && level > 0.04f && r.nextFloat() < 0.012f + 0.05f * level) {
                birdPause = 25;
                // Up in the trees, some way off.
                playRecorded(client, env, RECORDED_BIRDS, 0.35f + 0.65f * level, 7f + r.nextFloat() * 14f, 2f + r.nextFloat() * 7f);
            }
        }
        if (recordedFrogs) {
            float level = p.frogs;
            p.frogs = 0f;
            if (--frogPause <= 0 && level > 0.04f && r.nextFloat() < 0.015f + 0.06f * level) {
                frogPause = 18;
                // Down at the water's edge.
                playRecorded(client, env, RECORDED_FROGS, 0.35f + 0.65f * level, 5f + r.nextFloat() * 11f, -1.5f + r.nextFloat());
            }
        }
    }

    private void playRecorded(Minecraft client, ClientEnvironment env, Identifier event, float volume, float distance, float height) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float bearing = r.nextFloat() * LWMath.TAU;
        double x = env.camX + Mth.cos(bearing) * distance;
        double z = env.camZ + Mth.sin(bearing) * distance;
        float pitch = 0.94f + r.nextFloat() * 0.12f;
        // Farther calls are quieter, by the mod's own reckoning: the game's linear falloff would silence
        // anything past a pack's attenuation distance (16 blocks unless the pack says otherwise). The
        // sound is still placed in the world, so a mono recording is heard from its direction.
        float falloff = 1f - 0.55f * Mth.clamp(distance / 21f, 0f, 1f);
        client.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT,
                volume * falloff * ClientConfig.get().ambienceVolume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.NONE, x, env.camY + height, z, false));
    }

    private void schedule(int ticks, Runnable action) {
        if (delayed.size() < 64) {
            delayed.add(new Delayed(tick + ticks, action));
        }
    }

    private void updateParams(ClientEnvironment env, ClientConfig config) {
        AmbienceParams p = synth.params();
        boolean natural = env.natural;
        float open = env.openness;

        p.windSpeed = env.windLocal;
        p.windGust = env.wind.gust;
        p.foliage = natural ? env.foliage * (0.35f + 0.65f * Math.max(open, env.canopy)) : 0f;
        p.altitude = natural ? LWMath.clamp((float) (env.camY - 110.0) / 120f, 0f, 1f) * open : 0f;
        p.sandiness = natural ? Math.max(env.sand, env.dustiness * LWMath.smoothstep(0.3f, 0.85f, env.windLocal)) : 0f;
        p.snowfall = env.snow * Math.max(0.25f, env.rainExposure);

        p.rain = env.rain;
        float[] mix = env.columns.surfaceMix;
        p.rainOnLeaves = Math.max(mix[0], env.canopy * 0.8f);
        p.rainOnStone = mix[1];
        p.rainOnWater = mix[2];
        p.rainOnWood = mix[3];
        p.rainOnSoft = mix[4];
        p.roofed = LWMath.clamp01((1f - env.rainExposure) * (0.35f + 0.65f * env.roof) + env.cave);

        p.openness = open;
        p.cave = env.cave;
        float leafDrips = natural ? env.wetness * env.foliage * (1f - LWMath.clamp01(env.rain * 2f)) * 0.5f : 0f;
        p.drips = LWMath.clamp01(env.cave * (0.2f + 0.6f * env.humidity) + leafDrips);
        p.underwater = env.submerged;

        float birds = 0f, insects = 0f, crickets = 0f, frogs = 0f, surf = 0f;
        if (config.wildlife && natural) {
            float calm = (1f - LWMath.clamp01(env.precip * 2.5f)) * (1f - LWMath.smoothstep(0.6f, 1f, env.wind.speed));
            float outside = 0.25f + 0.75f * open;
            float night = 1f - env.daylight;
            // The dawn chorus: birds are loudest around sunrise.
            float dawn = 1f + 0.5f * LWMath.smoothstep(0.35f, 0.05f, Math.abs(env.sunElevation)) * (env.timeOfDay % 24000L < 6000L ? 1f : 0.3f);
            birds = env.daylight * calm * outside * dawn * LWMath.smoothstep(1f, 11f, env.ambientC)
                    * (env.kind(Kind.FOREST) + 0.75f * env.kind(Kind.JUNGLE) + 0.5f * env.kind(Kind.PLAINS) + 0.5f * env.kind(Kind.TAIGA)
                    + 0.35f * env.kind(Kind.SWAMP) + 0.35f * env.kind(Kind.SAVANNA) + 0.3f * env.kind(Kind.RIVER) + 0.25f * env.kind(Kind.BEACH));
            insects = env.daylight * calm * outside * LWMath.smoothstep(17f, 27f, env.ambientC)
                    * (env.kind(Kind.JUNGLE) + 0.5f * env.kind(Kind.SWAMP) + 0.3f * env.kind(Kind.SAVANNA) + 0.15f * env.kind(Kind.PLAINS));
            crickets = night * calm * outside * LWMath.smoothstep(7f, 16f, env.ambientC)
                    * (0.8f * env.kind(Kind.SAVANNA) + 0.8f * env.kind(Kind.JUNGLE) + 0.7f * env.kind(Kind.PLAINS) + 0.6f * env.kind(Kind.FOREST)
                    + 0.6f * env.kind(Kind.SWAMP) + 0.3f * env.kind(Kind.TAIGA) + 0.3f * env.kind(Kind.RIVER));
            frogs = (0.25f + 0.75f * night) * outside * LWMath.smoothstep(8f, 16f, env.ambientC) * env.kind(Kind.SWAMP)
                    * (1f - LWMath.smoothstep(0.6f, 1f, env.wind.speed));
            surf = (env.kind(Kind.OCEAN) + env.kind(Kind.BEACH)) * outside * LWMath.clamp01(mix[2] * 3f + 0.15f);
        }
        p.birds = LWMath.clamp01(birds);
        p.insects = LWMath.clamp01(insects);
        p.crickets = LWMath.clamp01(crickets);
        p.frogs = LWMath.clamp01(frogs);
        p.surf = LWMath.clamp01(surf);
        p.netherRumble = env.nether ? LWMath.clamp01(0.45f + env.heatSources / 30f) : 0f;
        p.endDrone = env.end ? 0.8f : 0f;
    }

    private void randomEvents(ClientEnvironment env) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float pan = r.nextFloat() * 2f - 1f;
        if (env.natural) {
            float frozen = (env.kind(Kind.SNOWY) + 0.5f * env.kind(Kind.MOUNTAIN) + 0.5f * env.kind(Kind.OCEAN))
                    * LWMath.smoothstep(-4f, -14f, env.ambientC);
            if (r.nextFloat() < 0.0012f * frozen) {
                synth.triggerEvent(AmbientEvent.ICE_CRACK, pan, 0.5f + r.nextFloat() * 0.4f);
            }
            if (r.nextFloat() < 0.002f * env.foliage * LWMath.smoothstep(0.5f, 1f, env.windLocal)) {
                synth.triggerEvent(AmbientEvent.BRANCH_CREAK, pan, 0.4f + r.nextFloat() * 0.4f);
            }
            if (r.nextFloat() < 0.002f * LWMath.smoothstep(0.45f, 0.9f, env.wind.speed) * env.openness) {
                synth.triggerEvent(AmbientEvent.DISTANT_GUST, pan, 0.4f + r.nextFloat() * 0.5f);
            }
        } else if (env.nether && r.nextFloat() < 0.004f * Math.min(1f, 0.2f + env.heatSources / 12f)) {
            synth.triggerEvent(AmbientEvent.LAVA_POP, pan, 0.5f + r.nextFloat() * 0.4f);
        }
    }

    @Override
    public void onDrip(double x, double y, double z) {
        if (!running || dripCooldown > 0) {
            return;
        }
        dripCooldown = 12;
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        float dx = (float) (x - env.camX), dz = (float) (z - env.camZ);
        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer == null) {
            return;
        }
        // Pan from the angle between the view direction and the drip.
        float yaw = (float) Math.toRadians(client.gameRenderer.mainCamera().yRot());
        float rightX = -Mth.cos(yaw), rightZ = -Mth.sin(yaw);
        float dist = (float) Math.sqrt(dx * dx + dz * dz) + 0.5f;
        float pan = LWMath.clamp((dx * rightX + dz * rightZ) / dist, -1f, 1f);
        float gain = LWMath.clamp(1.2f - dist / 10f, 0.2f, 1f);
        // Roughly the time the drop takes to reach the floor.
        schedule(14, () -> synth.triggerEvent(AmbientEvent.WATER_PLOP, pan, gain * 0.7f));
    }

    // ---- hooks on the vanilla sound engine --------------------------------------------------------

    /**
     * Called for every sound the game is about to play. Returns true to swallow it (it will be replayed
     * later by this class).
     */
    public boolean intercept(SoundInstance sound) {
        if (replaying || !running) {
            return false;
        }
        ClientConfig config = ClientConfig.get();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        Identifier id = sound.getIdentifier();

        if (config.thunderDelay && THUNDER.equals(id)) {
            double dx = sound.getX() - env.camX, dy = sound.getY() - env.camY, dz = sound.getZ() - env.camZ;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            int delay = (int) Math.min(110f, distance / 4.2f);
            if (delay < 3) {
                return false;
            }
            // Far strikes arrive later, quieter and duller; the synthesised rumble carries the tail.
            float far = LWMath.smoothstep(40f, 320f, distance);
            float volume = LWMath.lerp(1f, 0.3f, far) * (0.35f + 0.65f * Math.max(env.openness, 0.3f));
            float pitch = LWMath.lerp(1f, 0.72f, far) * (0.9f + ThreadLocalRandom.current().nextFloat() * 0.15f);
            Vec3 position = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            float pan = LWMath.clamp((float) (dx / (distance + 1f)), -1f, 1f);
            schedule(delay, () -> {
                Minecraft client = Minecraft.getInstance();
                replaying = true;
                try {
                    client.getSoundManager().play(new SimpleSoundInstance(THUNDER, SoundSource.WEATHER, volume, pitch,
                            SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, position.x, position.y, position.z, true));
                } finally {
                    replaying = false;
                }
                synth.triggerThunder(0.25f + 0.75f * far, pan, 0.45f);
            });
            return true;
        }

        if (config.caveEcho && env.cave > 0.45f && echoBudget > 0 && sound instanceof AbstractSoundInstanceAccessor accessor
                && !(sound instanceof TickableSoundInstance) && !sound.isRelative() && !sound.isLooping()
                && sound.getAttenuation() == SoundInstance.Attenuation.LINEAR) {
            SoundSource category = sound.getSource();
            if (category == SoundSource.PLAYERS || category == SoundSource.BLOCKS || category == SoundSource.HOSTILE
                    || category == SoundSource.NEUTRAL) {
                double dx = sound.getX() - env.camX, dy = sound.getY() - env.camY, dz = sound.getZ() - env.camZ;
                if (dx * dx + dy * dy + dz * dz < 18.0 * 18.0) {
                    echoBudget--;
                    float volume = accessor.livingworld$getVolume() * env.cave;
                    float pitch = accessor.livingworld$getPitch();
                    double x = sound.getX(), y = sound.getY(), z = sound.getZ();
                    echo(id, category, volume * 0.3f, pitch * 0.97f, x, y, z, 4);
                    echo(id, category, volume * 0.13f, pitch * 0.93f, x, y, z, 9);
                }
            }
        }
        return false;
    }

    private void echo(Identifier id, SoundSource category, float volume, float pitch, double x, double y, double z, int delay) {
        if (volume < 0.02f) {
            return;
        }
        schedule(delay, () -> {
            replaying = true;
            try {
                Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(id, category, volume, pitch,
                        SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false));
            } finally {
                replaying = false;
            }
        });
    }
}
