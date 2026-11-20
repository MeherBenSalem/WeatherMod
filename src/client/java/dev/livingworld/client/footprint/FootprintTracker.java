package dev.livingworld.client.footprint;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.config.QualityPreset;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.particle.Fx;
import dev.livingworld.surface.SurfaceMaterial;
import dev.livingworld.surface.SurfaceMaterialResolver;
import dev.livingworld.util.LWMath;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermiteEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.SilverfishEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ZoglinEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SnifferEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns the movement of nearby creatures into physical responses from the ground and water: oriented
 * left/right footprints, landing craters, wet boot prints, kicked-up sand, snow, mud and dust, ripples
 * and drips.
 *
 * <p>Everything is derived locally from entity positions the client already has, so it needs no packets
 * and looks the same for every player watching the same creature. Tracking is limited to living entities
 * (and boats) within the effect radius.
 */
public final class FootprintTracker {
    public static final FootprintTracker INSTANCE = new FootprintTracker();

    /** Receives footsteps of players so the audio layer can add material-specific sound. */
    public interface StepListener {
        void onStep(SurfaceMaterial material, boolean muddy, boolean puddle, float intensity, double x, double y, double z,
                    boolean localPlayer);
    }

    private static final class Track {
        boolean initialised;
        double lastX, lastY, lastZ;
        float accumulated;
        boolean leftNext;
        boolean wasOnGround = true;
        double peakY;
        boolean wasInWater;
        /** Ticks of "wet feet" left; each wet print uses some up. */
        int wet;
        /** Ticks of dripping left after leaving water. */
        int drip;
        float rippleTimer;
        long seen;
    }

    /** Result of looking at the ground under an entity. */
    private SurfaceMaterial material = SurfaceMaterial.NONE;
    private double surfaceY;
    private boolean exposed;
    private int snowLayers;
    private int light;

    public final DecalStore decals = new DecalStore(QualityPreset.MAX_DECALS);
    public StepListener listener;
    private final Int2ObjectOpenHashMap<Track> tracks = new Int2ObjectOpenHashMap<>();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private long tick;
    /** Prints placed since the last debug read. */
    public int placed;

    private FootprintTracker() {
    }

    private static float rf() {
        return ThreadLocalRandom.current().nextFloat();
    }

    private static float rs() {
        return ThreadLocalRandom.current().nextFloat() * 2f - 1f;
    }

    public void clear() {
        decals.clear();
        tracks.clear();
    }

    public void tick(MinecraftClient client, ClientEnvironment env) {
        ClientWorld world = client.world;
        if (world == null || !env.inWorld) {
            if (decals.count > 0 || !tracks.isEmpty()) {
                clear();
            }
            return;
        }
        tick++;
        ClientConfig config = ClientConfig.get();
        float windRate = env.wind.speed * env.wind.speed * 0.05f + env.sand * 0.15f;
        decals.tick(0.05f, config.footprintLifetime, windRate, env.rain * 0.04f, env.snow * 0.035f);

        float range = QualityManager.effectDistance();
        double rangeSq = range * range;
        for (Entity entity : world.getEntities()) {
            boolean boat = entity instanceof BoatEntity;
            if (!boat && !(entity instanceof LivingEntity)) {
                continue;
            }
            boolean player = entity instanceof PlayerEntity;
            if (!player && !boat && !config.mobFootprints) {
                continue;
            }
            if (!entity.isAlive() || entity.isSpectator()) {
                continue;
            }
            double dx = entity.getX() - env.camX;
            double dy = entity.getY() - env.camY;
            double dz = entity.getZ() - env.camZ;
            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                continue;
            }
            Track track = tracks.get(entity.getId());
            if (track == null) {
                track = new Track();
                tracks.put(entity.getId(), track);
            }
            track.seen = tick;
            update(world, env, config, entity, track, boat, entity == client.player);
        }

        if (tick % 100 == 0) {
            ObjectIterator<Track> it = tracks.values().iterator();
            while (it.hasNext()) {
                if (tick - it.next().seen > 60) {
                    it.remove();
                }
            }
        }
    }

    private void update(ClientWorld world, ClientEnvironment env, ClientConfig config, Entity e, Track t, boolean boat,
                        boolean local) {
        double x = e.getX(), y = e.getY(), z = e.getZ();
        double dx = x - t.lastX, dz = z - t.lastZ;
        float moved = (float) Math.sqrt(dx * dx + dz * dz);
        if (!t.initialised || moved > 4f) {
            t.initialised = true;
            t.lastX = x;
            t.lastY = y;
            t.lastZ = z;
            t.peakY = y;
            t.wasOnGround = e.isOnGround();
            t.wasInWater = e.isTouchingWater();
            t.accumulated = 0f;
            return;
        }

        boolean inWater = e.isTouchingWater();
        float width = e.getWidth();
        if (inWater) {
            t.wet = 200;
            t.drip = 0;
            if (config.waterRipples && moved > 0.012f && !e.isSubmergedInWater()) {
                t.rippleTimer -= 1f + moved * 9f;
                if (t.rippleTimer <= 0f) {
                    t.rippleTimer = 5f;
                    float surface = waterSurface(world, x, y, z);
                    if (!Float.isNaN(surface)) {
                        Fx.ripple(x + rs() * width * 0.3, surface + 0.02, z + rs() * width * 0.3,
                                0.3f + width * 0.7f + moved * 2f, 0.5f);
                        if (config.entitySplashes && moved > 0.13f) {
                            Fx.droplet(x + rs() * width * 0.4, surface + 0.1, z + rs() * width * 0.4,
                                    (float) -dx * 0.25f + rs() * 0.04f, 0.1f + rf() * 0.1f, (float) -dz * 0.25f + rs() * 0.04f, 0.035f);
                            Fx.spray(x, surface + 0.15, z, rs() * 0.02f, 0.03f, rs() * 0.02f, 0.2f + width * 0.2f, 0.35f);
                        }
                    }
                }
            }
            if (!t.wasInWater && config.entitySplashes && !boat) {
                float surface = waterSurface(world, x, y, z);
                if (!Float.isNaN(surface)) {
                    Fx.ripple(x, surface + 0.02, z, 0.6f + width, 0.6f);
                    int n = Math.min(10, 3 + (int) (width * 6f));
                    for (int i = 0; i < n; i++) {
                        Fx.droplet(x + rs() * width * 0.5, surface + 0.1, z + rs() * width * 0.5,
                                rs() * 0.08f, 0.12f + rf() * 0.14f, rs() * 0.08f, 0.03f + rf() * 0.02f);
                    }
                }
            }
        } else {
            if (t.wasInWater && !boat) {
                t.drip = 90;
            }
            if (t.drip > 0) {
                t.drip--;
                if (config.wetDrips && rf() < 0.4f * t.drip / 90f) {
                    Fx.droplet(x + rs() * width * 0.45, y + e.getHeight() * (0.25f + 0.55f * rf()), z + rs() * width * 0.45,
                            rs() * 0.01f, -0.02f, rs() * 0.01f, 0.025f + rf() * 0.015f);
                }
            }
            if (t.wet > 0) {
                t.wet--;
            }
        }
        t.wasInWater = inWater;

        if (!boat && !e.hasVehicle()) {
            boolean onGround = e.isOnGround();
            if (!onGround) {
                t.peakY = t.wasOnGround ? Math.max(y, t.lastY) : Math.max(t.peakY, y);
            } else if (!t.wasOnGround) {
                float fall = (float) (t.peakY - y);
                if (fall > 0.75f && !inWater) {
                    land(world, env, config, e, t, fall, local);
                }
                t.accumulated = 0f;
            }
            if (onGround && !inWater && moved > 0.004f) {
                t.accumulated += moved;
                float stride = stride(e);
                if (t.accumulated >= stride) {
                    t.accumulated = Math.min(t.accumulated - stride, stride * 0.5f);
                    step(world, env, config, e, t, (float) (dx / moved), (float) (dz / moved), moved, local);
                }
            }
            t.wasOnGround = onGround;
        }
        t.lastX = x;
        t.lastY = y;
        t.lastZ = z;
    }

    // ---- classification ------------------------------------------------------------------------

    private static int printSprite(Entity e) {
        if (e instanceof PlayerEntity) {
            return DecalStore.BOOT;
        }
        if (e instanceof AbstractHorseEntity) {
            return DecalStore.HORSESHOE;
        }
        if (e instanceof IronGolemEntity || e instanceof RavagerEntity || e instanceof HoglinEntity
                || e instanceof ZoglinEntity || e instanceof WardenEntity || e instanceof SnifferEntity) {
            return DecalStore.HEAVY;
        }
        if (e instanceof ChickenEntity || e instanceof ParrotEntity) {
            return DecalStore.BIRD;
        }
        if (e instanceof WolfEntity || e instanceof CatEntity || e instanceof OcelotEntity || e instanceof FoxEntity
                || e instanceof PolarBearEntity || e instanceof PandaEntity || e instanceof RabbitEntity) {
            return DecalStore.PAW;
        }
        if (e instanceof SpiderEntity || e instanceof SilverfishEntity || e instanceof EndermiteEntity) {
            return DecalStore.DOTS;
        }
        if (e instanceof SlimeEntity) {
            return -1;
        }
        if (e instanceof AnimalEntity) {
            return e.getWidth() < 0.5f ? DecalStore.DOTS : DecalStore.HOOF;
        }
        if (e.getHeight() > 1.4f && e.getWidth() < 0.95f) {
            return DecalStore.BOOT;
        }
        return e.getWidth() < 0.5f ? DecalStore.DOTS : -1;
    }

    private static float stride(Entity e) {
        if (e instanceof PlayerEntity) {
            return e.isSneaking() ? 0.4f : e.isSprinting() ? 1.05f : 0.66f;
        }
        return LWMath.clamp(0.3f + e.getWidth() * 0.5f, 0.3f, 1.25f);
    }

    private static float intensity(Entity e) {
        if (e.isSneaking()) {
            return 0.25f;
        }
        return e.isSprinting() ? 0.85f : 0.5f;
    }

    // ---- surface -------------------------------------------------------------------------------

    private void resolveSurface(ClientWorld world, ClientEnvironment env, double x, double y, double z) {
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        pos.set(bx, MathHelper.floor(y + 0.02), bz);
        BlockState feet = world.getBlockState(pos);
        snowLayers = 0;
        if (feet.getBlock() == Blocks.SNOW) {
            material = SurfaceMaterial.SNOW;
            snowLayers = feet.get(SnowBlock.LAYERS);
            surfaceY = pos.getY() + snowLayers / 8.0;
        } else if (!feet.getFluidState().isEmpty()) {
            material = SurfaceMaterial.NONE;
            surfaceY = y;
        } else {
            pos.set(bx, MathHelper.floor(y - 0.08), bz);
            BlockState below = world.getBlockState(pos);
            material = SurfaceMaterialResolver.resolve(below);
            surfaceY = y;
            // These sink under the feet but are drawn as full blocks.
            if (material == SurfaceMaterial.MUD || material == SurfaceMaterial.SOUL) {
                surfaceY = pos.getY() + 1.0;
            }
            if (material == SurfaceMaterial.SNOW) {
                snowLayers = 8;
            }
        }
        exposed = env.columns.top(bx, bz) <= surfaceY + 0.4;
        pos.set(bx, MathHelper.floor(surfaceY + 0.1), bz);
        light = WorldRenderer.getLightmapCoordinates(world, pos);
    }

    private float waterSurface(ClientWorld world, double x, double y, double z) {
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        int by = MathHelper.floor(y + 0.4);
        for (int i = 0; i < 3; i++) {
            pos.set(bx, by + i, bz);
            FluidState fluid = world.getFluidState(pos);
            if (!fluid.isIn(FluidTags.WATER)) {
                if (i == 0) {
                    pos.set(bx, by - 1, bz);
                    fluid = world.getFluidState(pos);
                    if (fluid.isIn(FluidTags.WATER)) {
                        return pos.getY() + fluid.getHeight(world, pos);
                    }
                }
                return Float.NaN;
            }
            pos.set(bx, by + i + 1, bz);
            if (!world.getFluidState(pos).isIn(FluidTags.WATER)) {
                pos.set(bx, by + i, bz);
                return pos.getY() + fluid.getHeight(world, pos);
            }
        }
        return Float.NaN;
    }

    private boolean nearWater(ClientWorld world, double x, double y, double z) {
        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y - 0.5);
        int bz = MathHelper.floor(z);
        for (int d = 1; d <= 3; d += 2) {
            if (isWater(world, bx + d, by, bz) || isWater(world, bx - d, by, bz)
                    || isWater(world, bx, by, bz + d) || isWater(world, bx, by, bz - d)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWater(ClientWorld world, int x, int y, int z) {
        pos.set(x, y, z);
        return world.getFluidState(pos).isIn(FluidTags.WATER);
    }

    // ---- steps ---------------------------------------------------------------------------------

    private void step(ClientWorld world, ClientEnvironment env, ClientConfig config, Entity e, Track t, float dirX, float dirZ,
                      float moved, boolean local) {
        resolveSurface(world, env, e.getX(), e.getY(), e.getZ());
        SurfaceMaterial m = material;
        if (m == SurfaceMaterial.NONE || m == SurfaceMaterial.WATER) {
            return;
        }
        float intensity = e instanceof PlayerEntity ? intensity(e) : 0.5f;
        float scale = e instanceof PlayerEntity ? 1f : LWMath.clamp(e.getWidth() / 0.6f, 0.45f, 2.4f);
        boolean left = t.leftNext;
        t.leftNext = !left;
        int sprite = printSprite(e);

        // Right-hand vector of the walker; the left foot lands on the negative side.
        float rightX = -dirZ, rightZ = dirX;
        float lateral = (sprite == DecalStore.BOOT ? 0.13f : 0.2f) * scale * (left ? -1f : 1f);
        double px = e.getX() + rightX * lateral;
        double pz = e.getZ() + rightZ * lateral;

        boolean muddy = m.turnsToMud() && exposed && env.wetness > 0.35f;
        boolean wetGround = exposed && env.wetness > 0.3f;
        if (wetGround) {
            t.wet = Math.max(t.wet, 70);
        }
        boolean puddle = !m.holdsPrints && wetGround && env.wetness > 0.5f;

        if (config.footprints && sprite >= 0) {
            float halfW, halfL;
            switch (sprite) {
                case DecalStore.BOOT -> {
                    halfW = 0.18f * scale;
                    halfL = 0.335f * scale;
                    if (intensity > 0.7f) {
                        sprite = DecalStore.BOOT_SPRINT;
                        halfL *= 1.08f;
                    }
                }
                case DecalStore.HEAVY -> halfW = halfL = 0.2f * scale;
                case DecalStore.HORSESHOE -> halfW = halfL = 0.11f * scale;
                case DecalStore.BIRD -> halfW = halfL = 0.2f * scale;
                case DecalStore.DOTS -> halfW = halfL = 0.16f * scale;
                default -> halfW = halfL = 0.13f * scale;
            }
            float r = m.red(), g = m.green(), b = m.blue();
            if (m.holdsPrints || muddy) {
                float depth = m.printDepth;
                float life = m.printLifetime;
                float windE = m.windErosion, rainE = m.rainErosion, snowE = m.snowErosion;
                if (muddy) {
                    float k = LWMath.clamp01((env.wetness - 0.35f) * 2.5f);
                    SurfaceMaterial mud = SurfaceMaterial.MUD;
                    r = LWMath.lerp(r, mud.red(), k);
                    g = LWMath.lerp(g, mud.green(), k);
                    b = LWMath.lerp(b, mud.blue(), k);
                    depth = LWMath.lerp(depth, mud.printDepth, k);
                    life = LWMath.lerp(life, mud.printLifetime, k);
                    windE *= 1f - k;
                }
                if (m == SurfaceMaterial.SNOW) {
                    depth *= 0.72f + 0.06f * snowLayers;
                } else if (m.isSandy() && nearWater(world, e.getX(), surfaceY, e.getZ())) {
                    // Damp beach sand holds a crisper, darker, longer-lived print.
                    depth *= 1.15f;
                    r *= 0.8f;
                    g *= 0.8f;
                    b *= 0.8f;
                    windE *= 0.3f;
                    life *= 1.5f;
                }
                float weight = sprite == DecalStore.HEAVY ? 1.25f : scale < 0.7f ? 0.7f : 1f;
                float strength = Math.min(1f, depth * (0.55f + 0.6f * intensity) * weight);
                decals.add(QualityManager.maxDecals(), px, surfaceY, pz, dirX, dirZ, halfW, halfL, sprite, !left, strength,
                        life, exposed, false, r, g, b, light, windE, rainE, snowE);
                placed++;
            } else if (config.wetFootprints && t.wet > 16 && !(exposed && env.rain > 0.1f)) {
                // Wet feet on hard, dry ground: a damp print that dries out, fainter with every step.
                float wetAmount = Math.min(1f, t.wet / 200f + 0.2f);
                float dry = LWMath.lerp(34f, 9f, LWMath.smoothstep(8f, 36f, env.ambientC));
                int stainSprite = sprite == DecalStore.BOOT || sprite == DecalStore.BOOT_SPRINT ? DecalStore.SOLE : sprite;
                decals.add(QualityManager.maxDecals(), px, surfaceY, pz, dirX, dirZ, halfW, halfL, stainSprite, !left,
                        0.45f + 0.5f * wetAmount, dry, exposed, true, 0.1f, 0.11f, 0.13f, light, 0.15f, 1f, 1f);
                t.wet -= 14;
                placed++;
            }
        }

        if (config.stepParticles) {
            int n = intensity < 0.3f ? 1 : intensity < 0.7f ? 2 : 5;
            kick(world, env, m, muddy, puddle, px, surfaceY, pz, dirX, dirZ, moved, intensity, n, scale);
        }
        if (listener != null && e instanceof PlayerEntity) {
            listener.onStep(m, muddy, puddle, intensity, px, surfaceY, pz, local);
        }
    }

    private void land(ClientWorld world, ClientEnvironment env, ClientConfig config, Entity e, Track t, float fall, boolean local) {
        resolveSurface(world, env, e.getX(), e.getY(), e.getZ());
        SurfaceMaterial m = material;
        if (m == SurfaceMaterial.NONE || m == SurfaceMaterial.WATER) {
            return;
        }
        boolean muddy = m.turnsToMud() && exposed && env.wetness > 0.35f;
        boolean puddle = !m.holdsPrints && exposed && env.wetness > 0.5f;
        float scale = LWMath.clamp(e.getWidth() / 0.6f, 0.5f, 2.6f);
        float yaw = rf() * LWMath.TAU;
        float dirX = MathHelper.cos(yaw), dirZ = MathHelper.sin(yaw);
        if (config.footprints) {
            if (e instanceof SlimeEntity) {
                decals.add(QualityManager.maxDecals(), e.getX(), surfaceY, e.getZ(), dirX, dirZ, 0.35f * scale, 0.35f * scale,
                        DecalStore.SPLAT, false, 0.7f, 30f, exposed, true, 0.22f, 0.4f, 0.2f, light, 0.1f, 1f, 1f);
                placed++;
            } else if (m.holdsPrints || muddy) {
                float size = LWMath.clamp(0.26f + fall * 0.05f, 0.26f, 0.55f) * scale;
                float r = m.red(), g = m.green(), b = m.blue();
                if (muddy) {
                    r = SurfaceMaterial.MUD.red();
                    g = SurfaceMaterial.MUD.green();
                    b = SurfaceMaterial.MUD.blue();
                }
                float strength = Math.min(1f, (muddy ? 0.9f : m.printDepth) * (0.7f + fall * 0.08f));
                decals.add(QualityManager.maxDecals(), e.getX(), surfaceY, e.getZ(), dirX, dirZ, size, size, DecalStore.IMPACT, false,
                        strength, m.printLifetime, exposed, false, r, g, b, light, m.windErosion, m.rainErosion, m.snowErosion);
                placed++;
            }
        }
        if (config.stepParticles) {
            int n = Math.min(22, 7 + (int) (fall * 3f));
            for (int i = 0; i < 4; i++) {
                float a = rf() * LWMath.TAU;
                kick(world, env, m, muddy, puddle, e.getX() + MathHelper.cos(a) * 0.2 * scale, surfaceY,
                        e.getZ() + MathHelper.sin(a) * 0.2 * scale, -MathHelper.cos(a), -MathHelper.sin(a), 0.22f, 1f, n / 4, scale);
            }
        }
        if (listener != null && e instanceof PlayerEntity) {
            listener.onStep(m, muddy, puddle, 1f, e.getX(), surfaceY, e.getZ(), local);
        }
    }

    /** Throws up whatever the ground is made of. {@code dir} is the direction of travel; debris flies backward. */
    private void kick(ClientWorld world, ClientEnvironment env, SurfaceMaterial m, boolean muddy, boolean puddle, double x, double y,
                      double z, float dirX, float dirZ, float moved, float intensity, int count, float scale) {
        count = Math.max(1, Math.round(count * Math.min(1.5f, QualityManager.spawnScale())));
        float back = Math.min(0.5f, moved) * 0.5f;
        float bx = -dirX * back, bz = -dirZ * back;
        float r = m.red(), g = m.green(), b = m.blue();
        if (muddy || m == SurfaceMaterial.MUD || m == SurfaceMaterial.CLAY) {
            float mr = muddy ? SurfaceMaterial.MUD.red() : r;
            float mg = muddy ? SurfaceMaterial.MUD.green() : g;
            float mb = muddy ? SurfaceMaterial.MUD.blue() : b;
            for (int i = 0; i < count; i++) {
                Fx.mud(x + rs() * 0.12 * scale, y + 0.05, z + rs() * 0.12 * scale, bx + rs() * 0.05f,
                        0.07f + rf() * 0.12f * intensity, bz + rs() * 0.05f, mr, mg, mb);
            }
            if (env.wetness > 0.5f && exposed) {
                Fx.tinySplash(x, y + 0.02, z, 0.05f);
            }
            return;
        }
        switch (m) {
            case SAND, RED_SAND -> {
                for (int i = 0; i < count; i++) {
                    Fx.grains(x + rs() * 0.12 * scale, y + 0.04, z + rs() * 0.12 * scale, bx + rs() * 0.04f,
                            0.05f + rf() * 0.11f * intensity, bz + rs() * 0.04f, r, g, b, 0.035f + rf() * 0.035f);
                }
                if (intensity > 0.7f && !(exposed && env.wetness > 0.3f)) {
                    Fx.dustPuff(x, y + 0.12, z, bx * 0.4f, 0.012f, bz * 0.4f, r, g, b, 0.16f * scale, 0.32f, 24f);
                }
            }
            case SNOW -> {
                Fx.snowPuff(x, y + 0.08, z, bx * 0.8f + rs() * 0.02f, 0.025f + 0.05f * intensity, bz * 0.8f + rs() * 0.02f,
                        (0.12f + 0.1f * intensity) * scale, 0.6f);
                for (int i = 1; i < count; i++) {
                    Fx.snowFleck(x + rs() * 0.15 * scale, y + 0.06, z + rs() * 0.15 * scale, bx + rs() * 0.05f,
                            0.05f + rf() * 0.1f * intensity, bz + rs() * 0.05f);
                }
            }
            case DIRT, GRAVEL, SOUL, ASH -> {
                boolean dry = !(exposed && env.wetness > 0.2f);
                if (dry && intensity >= 0.5f && m.dust > 0f) {
                    Fx.dustPuff(x, y + 0.1, z, bx * 0.4f, 0.01f, bz * 0.4f, r * 1.15f, g * 1.15f, b * 1.15f,
                            (0.12f + 0.08f * intensity) * scale, 0.24f * m.dust + 0.06f, 22f);
                }
                if (intensity > 0.7f) {
                    for (int i = 0; i < count / 2; i++) {
                        Fx.grains(x + rs() * 0.1, y + 0.04, z + rs() * 0.1, bx + rs() * 0.04f, 0.05f + rf() * 0.08f,
                                bz + rs() * 0.04f, r, g, b, 0.03f + rf() * 0.03f);
                    }
                }
            }
            case GRASS, MOSS -> {
                if (intensity > 0.7f) {
                    pos.set(MathHelper.floor(x), MathHelper.floor(y - 0.5), MathHelper.floor(z));
                    int c = BiomeColors.getGrassColor(world, pos);
                    float cr = ((c >> 16) & 255) / 255f, cg = ((c >> 8) & 255) / 255f, cb = (c & 255) / 255f;
                    for (int i = 0; i < Math.max(1, count / 3); i++) {
                        Fx.grassBits(x + rs() * 0.12, y + 0.06, z + rs() * 0.12, bx + rs() * 0.04f, 0.06f + rf() * 0.07f,
                                bz + rs() * 0.04f, cr, cg, cb);
                    }
                }
            }
            default -> {
                if (puddle) {
                    Fx.tinySplash(x, y + 0.02, z, 0.07f);
                    Fx.ripple(x, y + 0.014, z, 0.24f, 0.4f);
                    if (intensity > 0.7f) {
                        for (int i = 0; i < 3; i++) {
                            Fx.droplet(x + rs() * 0.1, y + 0.05, z + rs() * 0.1, bx + rs() * 0.05f, 0.08f + rf() * 0.08f,
                                    bz + rs() * 0.05f, 0.025f);
                        }
                    }
                } else if (m.dust > 0f && intensity > 0.9f) {
                    Fx.dustPuff(x, y + 0.1, z, bx * 0.3f, 0.008f, bz * 0.3f, 0.7f, 0.7f, 0.7f, 0.14f * scale, 0.16f, 18f);
                }
            }
        }
    }
}
