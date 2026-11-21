package dev.livingworld.client.particle;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.QualityManager;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.env.ColumnCache;
import dev.livingworld.climate.BiomeProfile.Kind;
import dev.livingworld.climate.WindField;
import dev.livingworld.surface.SurfaceMaterial;
import dev.livingworld.util.LWMath;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns the particles that give each place and each kind of weather its character: rain splashes and
 * ripples, drips from wet leaves and cave ceilings, falling leaves, pollen, fireflies, blowing sand,
 * dust devils, spindrift, ice crystals, sea spray, embers, ash and the End's drifting motes.
 *
 * <p>Every rate is a product of the shared environment state (biome blend, wind, temperature, light,
 * shelter, wetness), so the effects emerge from conditions rather than from per-biome scripts: leaves
 * fall faster in a gale, dust only lifts from dry ground, fireflies wait for a calm night.
 */
public final class AmbientSpawner {
    public static final AmbientSpawner INSTANCE = new AmbientSpawner();

    /** Callback for discrete audible events (drips, lava pops). */
    public interface EventListener {
        void onDrip(double x, double y, double z);
    }

    public EventListener listener;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private final float[] dust = new float[3];

    // One wandering dust devil at a time.
    private double devilX, devilY, devilZ;
    private int devilLife;
    private float devilPhase;

    private AmbientSpawner() {
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    private static float rf() {
        return rnd().nextFloat();
    }

    private static float rs() {
        return rnd().nextFloat() * 2f - 1f;
    }

    /** Converts an expected count per tick into an integer count with the right long-run average. */
    private static int count(float expected) {
        int n = (int) expected;
        return rf() < expected - n ? n + 1 : n;
    }

    public void tick(ClientWorld world, ClientEnvironment env) {
        ClientConfig config = ClientConfig.get();
        float scale = QualityManager.spawnScale();
        if (scale <= 0f) {
            return;
        }
        weather(world, env, config, scale);
        if (config.biomeAmbience) {
            if (env.natural) {
                overworld(world, env, config, scale);
            } else if (env.nether) {
                nether(world, env, scale);
            } else if (env.end) {
                end(env, scale);
            }
            underground(world, env, config, scale);
        }
    }

    // ---- rain: splashes, ripples, drips ---------------------------------------------------------

    private void weather(ClientWorld world, ClientEnvironment env, ClientConfig config, float scale) {
        ColumnCache columns = env.columns;
        int cx = env.camBlock.getX(), cz = env.camBlock.getZ();
        float camY = (float) env.camY;

        if (config.rainSplashes && env.rain > 0.02f && env.submerged < 0.5f) {
            int n = count(env.rain * 13f * scale);
            for (int i = 0; i < n; i++) {
                float r = 1f + 14f * rf() * rf();
                float a = rf() * LWMath.TAU;
                int wx = cx + Math.round(MathHelper.cos(a) * r);
                int wz = cz + Math.round(MathHelper.sin(a) * r);
                float top = columns.top(wx, wz);
                if (top <= ColumnCache.NO_SURFACE + 1f || top > camY + 7f || top < camY - 14f) {
                    continue;
                }
                double x = wx + rf(), z = wz + rf();
                SurfaceMaterial m = columns.topMaterial(wx, wz);
                switch (m) {
                    case WATER -> {
                        if (config.waterRipples) {
                            Fx.ripple(x, top + 0.02, z, 0.16f + 0.24f * rf(), 0.55f);
                        }
                        if (rf() < 0.25f) {
                            Fx.droplet(x, top + 0.05, z, rs() * 0.02f, 0.07f + rf() * 0.05f, rs() * 0.02f, 0.02f);
                        }
                    }
                    case LEAVES -> Fx.tinySplash(x, top + 0.02, z, 0.02f);
                    case SNOW, NONE -> {
                    }
                    case STONE, WOOD, ICE, GRAVEL -> Fx.splash(x, top, z, 0.06f + 0.05f * rf(), 0.75f);
                    default -> Fx.splash(x, top, z, 0.05f + 0.035f * rf(), 0.5f);
                }
            }
        }

        // Water collected on foliage keeps dripping during and well after the rain.
        float dripRate = (env.rain * 1.6f + env.wetness * 0.7f) * env.foliage * scale;
        if (config.rainSplashes && dripRate > 0.01f && env.ambientC > 0f) {
            int n = count(dripRate);
            for (int i = 0; i < n; i++) {
                int wx = cx + rnd().nextInt(-12, 13);
                int wz = cz + rnd().nextInt(-12, 13);
                if (!columns.hasCanopy(wx, wz)) {
                    continue;
                }
                int y = MathHelper.floor(columns.top(wx, wz)) - 1;
                int floor = MathHelper.floor(columns.ground(wx, wz));
                for (int k = 0; k < 10 && y > floor; k++, y--) {
                    pos.set(wx, y, wz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        if (Math.abs(y - camY) < 14f) {
                            Fx.drip(wx + 0.15 + rf() * 0.7, y + 0.95, wz + 0.15 + rf() * 0.7);
                        }
                        break;
                    }
                    if (!state.isIn(BlockTags.LEAVES)) {
                        break;
                    }
                }
            }
        }
    }

    // ---- overworld ambience ---------------------------------------------------------------------

    private void overworld(ClientWorld world, ClientEnvironment env, ClientConfig config, float scale) {
        ColumnCache columns = env.columns;
        int cx = env.camBlock.getX(), cz = env.camBlock.getZ();
        float camY = (float) env.camY;
        float wind = env.wind.speed;
        float calmAir = 1f - LWMath.clamp01(env.precip * 2.5f);
        float outdoors = env.openness;
        float night = 1f - env.daylight;
        float windVX = env.wind.dirX * env.windLocal * WindField.BLOCKS_PER_TICK;
        float windVZ = env.wind.dirZ * env.windLocal * WindField.BLOCKS_PER_TICK;

        // Falling leaves: a steady trickle, a flurry when the wind gets up. Autumn brings them down in
        // earnest; by midwinter there is little left to fall.
        if (config.fallingLeaves && env.foliage > 0.2f && env.ambientC > -6f) {
            float seasonal = (1f + 3.5f * env.autumn) * (1f - 0.85f * env.winter);
            int n = count(0.2f * env.foliage * (0.25f + 2.2f * wind) * scale * seasonal);
            for (int i = 0; i < n; i++) {
                int wx = cx + rnd().nextInt(-16, 17);
                int wz = cz + rnd().nextInt(-16, 17);
                if (!columns.hasCanopy(wx, wz)) {
                    continue;
                }
                float top = columns.top(wx, wz);
                float ground = columns.ground(wx, wz);
                float y = ground + 2f + rf() * Math.max(0.5f, top - ground - 2.5f);
                if (Math.abs(y - camY) > 16f) {
                    continue;
                }
                pos.set(wx, MathHelper.floor(y), wz);
                if (!world.getBlockState(pos).isAir()) {
                    continue;
                }
                int c = leafColor(world, wx, MathHelper.floor(top) - 1, wz);
                float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
                // A falling leaf has the colour the canopy has turned (conifers, red == blue, stay green).
                boolean seasonalTint = config.seasonalFoliage && g > r * 1.07f && g > b * 1.07f && Math.abs(r - b) > 0.04f * g;
                if (seasonalTint && env.autumn + env.winter > 0.01f) {
                    float turn = rf();
                    float tr = LWMath.lerp(1f, 0.78f, turn), tg = LWMath.lerp(0.78f, 0.24f, turn), tb = LWMath.lerp(0.2f, 0.11f, turn);
                    float amount = LWMath.clamp01(env.autumn * (0.6f + 0.8f * rf()));
                    r = LWMath.lerp(r, tr * g, amount);
                    b = LWMath.lerp(b, tb * g, amount);
                    g = LWMath.lerp(g, tg * g, amount);
                    float brown = env.winter * 0.85f;
                    float lum = Math.max(r, Math.max(g, b));
                    r = LWMath.lerp(r, 0.44f * lum, brown);
                    g = LWMath.lerp(g, 0.35f * lum, brown);
                    b = LWMath.lerp(b, 0.25f * lum, brown);
                }
                Fx.leaf(wx + rf(), y, wz + rf(), windVX * 0.5f, windVZ * 0.5f, r, g, b);
            }
        }

        // Pollen and drifting specks in mild, dry daylight.
        float life = env.foliage * env.daylight * calmAir * LWMath.smoothstep(4f, 14f, env.ambientC);
        if (life > 0.02f) {
            int n = count(0.45f * life * scale);
            for (int i = 0; i < n; i++) {
                double x = env.camX + rs() * 11f, y = env.camY + rs() * 4f + 1f, z = env.camZ + rs() * 11f;
                if (!airAt(world, x, y, z)) {
                    continue;
                }
                float jungle = env.kind(Kind.JUNGLE);
                if (rf() < jungle) {
                    Fx.mote(x, y, z, Fx.SPORE, 0.75f, 0.95f, 0.6f, 0.55f, 0.03f + rf() * 0.025f, 170f);
                } else {
                    Fx.mote(x, y, z, rf() < 0.5f ? Fx.POLLEN : Fx.MOTE, 1f, 0.97f, 0.8f, 0.6f, 0.02f + rf() * 0.02f, 150f);
                }
            }
            // Seed fluff riding the breeze over open grassland.
            if (rf() < 0.025f * env.kind(Kind.PLAINS) * (0.3f + wind) * scale) {
                double x = env.camX + rs() * 12f - env.wind.dirX * 6f, z = env.camZ + rs() * 12f - env.wind.dirZ * 6f;
                double y = env.camY + 1f + rf() * 4f;
                if (airAt(world, x, y, z)) {
                    Fx.seedFluff(x, y, z);
                }
            }
        }

        // Fireflies on calm, warm nights; thickest over swamps.
        float fireflies = night * calmAir * (1f - LWMath.smoothstep(0.35f, 0.7f, wind)) * LWMath.smoothstep(8f, 16f, env.ambientC)
                * (env.kind(Kind.SWAMP) + 0.3f * env.kind(Kind.JUNGLE) + 0.18f * env.kind(Kind.FOREST) + 0.1f * env.kind(Kind.PLAINS))
                * outdoors;
        if (fireflies > 0.01f) {
            int n = count(0.3f * fireflies * scale);
            for (int i = 0; i < n; i++) {
                int wx = MathHelper.floor(env.camX + rs() * 14f), wz = MathHelper.floor(env.camZ + rs() * 14f);
                float ground = columns.ground(wx, wz);
                double y = ground + 0.4f + rf() * 2.6f;
                if (Math.abs(y - camY) < 10f && airAt(world, wx + 0.5, y, wz + 0.5)) {
                    Fx.firefly(wx + rf(), y, wz + rf());
                }
            }
        }

        // Loose sand and dust streaming over dry ground when the wind picks up.
        float dryGround = env.dustiness * (1f - LWMath.clamp01(env.wetness * 3f)) * outdoors;
        if (config.blowingSand && dryGround > 0.05f && env.windLocal > 0.22f) {
            env.dustColor(dust);
            float lit = Math.max(0.3f, env.daylight);
            float strength = dryGround * (env.windLocal - 0.2f);
            int n = count(5f * strength * scale);
            for (int i = 0; i < n; i++) {
                // Start upwind so the grains cross the view.
                double x = env.camX + rs() * 14f - env.wind.dirX * 5f, z = env.camZ + rs() * 14f - env.wind.dirZ * 5f;
                int wx = MathHelper.floor(x), wz = MathHelper.floor(z);
                SurfaceMaterial m = columns.topMaterial(wx, wz);
                if (m.dust < 0.4f) {
                    continue;
                }
                float top = columns.top(wx, wz);
                if (Math.abs(top - camY) > 10f) {
                    continue;
                }
                float r = m.isSandy() ? m.red() : dust[0], g = m.isSandy() ? m.green() : dust[1], b = m.isSandy() ? m.blue() : dust[2];
                if (rf() < 0.7f) {
                    Fx.streak(x, top + 0.05 + rf() * 0.5, z, windVX * 0.8f, 0.005f, windVZ * 0.8f, Fx.SAND_STREAK,
                            r * lit, g * lit, b * lit, 0.55f, 0.07f + rf() * 0.05f, 26f + rf() * 20f);
                } else {
                    Fx.dustPuff(x, top + 0.2 + rf() * 0.6, z, windVX * 0.6f, 0.01f, windVZ * 0.6f, r, g, b,
                            0.35f + rf() * 0.5f, 0.14f + 0.14f * Math.min(1f, strength * 2f), 50f);
                }
            }
        }
        // In a sandstorm the air itself is full of rolling clouds of dust.
        if (env.sand > 0.08f && outdoors > 0.2f) {
            env.dustColor(dust);
            float lit = Math.max(0.3f, env.daylight);
            int n = count(env.sand * 2.2f * scale);
            for (int i = 0; i < n; i++) {
                double x = env.camX + rs() * 16f - env.wind.dirX * 8f, z = env.camZ + rs() * 16f - env.wind.dirZ * 8f;
                float top = columns.top(MathHelper.floor(x), MathHelper.floor(z));
                if (top <= ColumnCache.NO_SURFACE + 1f || Math.abs(top - camY) > 12f) {
                    continue;
                }
                Fx.dustPuff(x, top + 0.3 + rf() * 3.5, z, windVX * 0.9f, 0.012f, windVZ * 0.9f, dust[0] * lit, dust[1] * lit,
                        dust[2] * lit, 0.9f + rf() * 1.4f, 0.12f + 0.12f * env.sand, 46f);
            }
        }
        dustDevil(world, env, config, scale, dryGround);

        // Spindrift torn off snowfields, and diamond dust glittering in very cold clear air.
        float snowy = env.kind(Kind.SNOWY) + env.kind(Kind.MOUNTAIN) * LWMath.smoothstep(2f, -6f, env.ambientC);
        if (snowy > 0.05f && env.windLocal > 0.3f) {
            int n = count(4f * snowy * (env.windLocal - 0.25f) * scale);
            for (int i = 0; i < n; i++) {
                double x = env.camX + rs() * 14f - env.wind.dirX * 5f, z = env.camZ + rs() * 14f - env.wind.dirZ * 5f;
                int wx = MathHelper.floor(x), wz = MathHelper.floor(z);
                if (columns.topMaterial(wx, wz) != SurfaceMaterial.SNOW) {
                    continue;
                }
                float top = columns.top(wx, wz);
                if (Math.abs(top - camY) > 10f) {
                    continue;
                }
                if (rf() < 0.6f) {
                    Fx.streak(x, top + 0.05 + rf() * 0.4, z, windVX * 0.8f, 0.006f, windVZ * 0.8f, Fx.SNOW_STREAK,
                            0.97f, 0.98f, 1f, 0.6f, 0.08f + rf() * 0.06f, 22f + rf() * 18f);
                } else {
                    Fx.snowPuff(x, top + 0.15, z, windVX * 0.6f, 0.012f, windVZ * 0.6f, 0.25f + rf() * 0.3f, 0.3f);
                }
            }
        }
        if (config.iceCrystals) {
            float crystals = LWMath.smoothstep(-7f, -18f, env.ambientC) * calmAir * outdoors * (0.3f + 0.7f * env.daylight);
            int n = count(0.5f * crystals * scale);
            for (int i = 0; i < n; i++) {
                double x = env.camX + rs() * 9f, y = env.camY + rs() * 4f + 0.5f, z = env.camZ + rs() * 9f;
                if (airAt(world, x, y, z)) {
                    Fx.iceSparkle(x, y, z);
                }
            }
        }

        // Shorelines: spray where wind-driven water meets land; cold water breathes mist.
        float coastal = env.kind(Kind.OCEAN) + env.kind(Kind.BEACH) + 0.4f * env.kind(Kind.RIVER);
        if (config.waterMist && coastal > 0.05f) {
            int tries = count((0.6f * wind + 0.15f) * coastal * 4f * scale);
            for (int i = 0; i < tries; i++) {
                int wx = cx + rnd().nextInt(-14, 15), wz = cz + rnd().nextInt(-14, 15);
                if (columns.topMaterial(wx, wz) != SurfaceMaterial.WATER) {
                    continue;
                }
                int sx = wx + Math.round(env.wind.dirX), sz = wz + Math.round(env.wind.dirZ);
                SurfaceMaterial ahead = columns.topMaterial(sx, sz);
                float top = columns.top(wx, wz);
                if (ahead != SurfaceMaterial.WATER && ahead != SurfaceMaterial.NONE && Math.abs(top - camY) < 10f) {
                    Fx.spray(wx + rf(), top + 0.15, wz + rf(), windVX * 0.3f + rs() * 0.02f, 0.04f + rf() * 0.06f * (0.5f + wind),
                            windVZ * 0.3f + rs() * 0.02f, 0.22f + rf() * 0.25f, 0.4f);
                    if (rf() < 0.5f) {
                        Fx.droplet(wx + rf(), top + 0.1, wz + rf(), windVX * 0.4f + rs() * 0.03f, 0.1f + rf() * 0.1f,
                                windVZ * 0.4f + rs() * 0.03f, 0.025f);
                    }
                }
            }
        }
        if (config.waterMist && env.ambientC < 3f && env.humidity > 0.4f && wind < 0.5f) {
            int tries = count(0.5f * scale);
            for (int i = 0; i < tries; i++) {
                int wx = cx + rnd().nextInt(-12, 13), wz = cz + rnd().nextInt(-12, 13);
                if (columns.topMaterial(wx, wz) == SurfaceMaterial.WATER) {
                    float top = columns.top(wx, wz);
                    if (Math.abs(top - camY) < 8f) {
                        float lit = Math.max(0.25f, env.daylight);
                        Fx.mist(wx + rf(), top + 0.3 + rf() * 0.4, wz + rf(), windVX * 0.3f, windVZ * 0.3f, 0.9f * lit, 0.94f * lit, lit,
                                0.22f, 0.8f + rf() * 0.9f);
                    }
                }
            }
        }

        waterfalls(world, env, config, scale);
    }

    /** Starts a dust devil at the given spot right away (used by the showcase; natural ones are random). */
    public void forceDustDevil(ClientEnvironment env, double x, double z) {
        float top = env.columns.top(MathHelper.floor(x), MathHelper.floor(z));
        if (top <= ColumnCache.NO_SURFACE + 1f) {
            return;
        }
        devilX = x;
        devilY = top;
        devilZ = z;
        devilLife = 300;
    }

    private void dustDevil(ClientWorld world, ClientEnvironment env, ClientConfig config, float scale, float dryGround) {
        if (devilLife > 0) {
            devilLife--;
            devilPhase += 0.55f;
            devilX += env.wind.dirX * 0.05f;
            devilZ += env.wind.dirZ * 0.05f;
            float ground = env.columns.top(MathHelper.floor(devilX), MathHelper.floor(devilZ));
            if (ground > ColumnCache.NO_SURFACE + 1f && Math.abs(ground - devilY) < 2f) {
                devilY = ground;
            }
            env.dustColor(dust);
            float fade = Math.min(1f, devilLife / 40f);
            int n = count(3f * Math.min(1.5f, scale));
            for (int i = 0; i < n; i++) {
                float h = rf();
                float radius = 0.25f + h * 0.9f;
                float a = devilPhase + rf() * LWMath.TAU;
                float c = MathHelper.cos(a), s = MathHelper.sin(a);
                // Tangential velocity: the column visibly rotates and rises.
                Fx.dustPuff(devilX + c * radius, devilY + 0.1 + h * 4.5, devilZ + s * radius, -s * 0.11f, 0.03f + 0.03f * rf(), c * 0.11f,
                        dust[0], dust[1], dust[2], 0.2f + 0.3f * h, 0.26f * fade, 16f);
            }
            if (rf() < 0.5f) {
                Fx.grains(devilX + rs() * 0.5, devilY + 0.1, devilZ + rs() * 0.5, rs() * 0.08f, 0.12f + rf() * 0.1f, rs() * 0.08f,
                        dust[0], dust[1], dust[2], 0.04f);
            }
            return;
        }
        if (!config.dustDevils || dryGround < 0.4f || env.ambientC < 30f || env.daylight < 0.6f || env.precip > 0.02f
                || env.wind.speed > 0.6f) {
            return;
        }
        if (rf() < 0.0022f * dryGround * scale) {
            float a = rf() * LWMath.TAU;
            float r = 7f + rf() * 12f;
            double x = env.camX + MathHelper.cos(a) * r, z = env.camZ + MathHelper.sin(a) * r;
            int wx = MathHelper.floor(x), wz = MathHelper.floor(z);
            if (env.columns.topMaterial(wx, wz).dust >= 0.5f) {
                float top = env.columns.top(wx, wz);
                if (Math.abs(top - env.camY) < 8f) {
                    devilX = x;
                    devilY = top;
                    devilZ = z;
                    devilLife = 140 + rnd().nextInt(140);
                }
            }
        }
    }

    private void waterfalls(ClientWorld world, ClientEnvironment env, ClientConfig config, float scale) {
        if (!config.waterMist) {
            return;
        }
        int tries = count(6f * Math.min(1f, scale));
        for (int i = 0; i < tries; i++) {
            pos.set(env.camBlock.getX() + rnd().nextInt(-12, 13), env.camBlock.getY() + rnd().nextInt(-8, 5),
                    env.camBlock.getZ() + rnd().nextInt(-12, 13));
            FluidState fluid = world.getFluidState(pos);
            if (fluid.getFluid() != Fluids.FLOWING_WATER || !fluid.get(Properties.FALLING)) {
                continue;
            }
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            pos.set(x, y - 1, z);
            FluidState below = world.getFluidState(pos);
            boolean base = below.isIn(FluidTags.WATER) ? !(below.getFluid() == Fluids.FLOWING_WATER && below.get(Properties.FALLING))
                    : !world.getBlockState(pos).isAir();
            if (base) {
                Fx.spray(x + rf(), y + 0.15, z + rf(), rs() * 0.03f, 0.04f + rf() * 0.05f, rs() * 0.03f, 0.3f + rf() * 0.3f, 0.4f);
                Fx.droplet(x + rf(), y + 0.2, z + rf(), rs() * 0.06f, 0.1f + rf() * 0.12f, rs() * 0.06f, 0.025f);
            }
        }
    }

    // ---- underground ----------------------------------------------------------------------------

    private void underground(ClientWorld world, ClientEnvironment env, ClientConfig config, float scale) {
        float damp = env.cave * (0.25f + 0.75f * env.humidity) + env.kind(Kind.DRIPSTONE_CAVES) * 0.6f + env.kind(Kind.LUSH_CAVES) * 0.5f;
        if (damp > 0.05f && env.ambientC > 0f) {
            int n = count(0.09f * damp * scale);
            for (int i = 0; i < n; i++) {
                int wx = env.camBlock.getX() + rnd().nextInt(-8, 9);
                int wz = env.camBlock.getZ() + rnd().nextInt(-8, 9);
                int y = env.camBlock.getY();
                pos.set(wx, y, wz);
                if (!world.getBlockState(pos).isAir()) {
                    continue;
                }
                for (int k = 1; k <= 9; k++) {
                    pos.set(wx, y + k, wz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        if (state.isOpaque() || state.getBlock() == Blocks.POINTED_DRIPSTONE) {
                            double dx = wx + 0.2 + rf() * 0.6, dy = y + k - 0.08, dz = wz + 0.2 + rf() * 0.6;
                            Fx.drip(dx, dy, dz);
                            if (listener != null) {
                                listener.onDrip(dx, dy, dz);
                            }
                        }
                        break;
                    }
                }
            }
        }
        // Dust hanging in lamplight.
        if (env.cave > 0.3f && env.blockLight > 0.35f) {
            int n = count(0.25f * env.cave * env.blockLight * scale);
            for (int i = 0; i < n; i++) {
                double x = env.camX + rs() * 6f, y = env.camY + rs() * 2.5f, z = env.camZ + rs() * 6f;
                if (airAt(world, x, y, z)) {
                    Fx.mote(x, y, z, Fx.MOTE, 1f, 0.95f, 0.85f, 0.5f, 0.018f + rf() * 0.02f, 160f);
                }
            }
        }
    }

    // ---- other dimensions -----------------------------------------------------------------------

    private void nether(ClientWorld world, ClientEnvironment env, float scale) {
        // Embers lifted by hot air; more of them the closer the lava.
        float heat = 0.25f + Math.min(1f, env.heatSources / 18f);
        int n = count(0.7f * heat * scale);
        for (int i = 0; i < n; i++) {
            double x = env.camX + rs() * 12f, y = env.camY + rs() * 5f - 1f, z = env.camZ + rs() * 12f;
            if (airAt(world, x, y, z)) {
                Fx.ember(x, y, z, rs() * 0.01f, 0.015f + rf() * 0.04f, rs() * 0.01f);
            }
        }
        float ashy = env.kind(Kind.BASALT_DELTAS) + 0.5f * env.kind(Kind.NETHER_WASTES) + 0.4f * env.kind(Kind.SOUL_SAND_VALLEY);
        n = count(0.8f * ashy * scale);
        for (int i = 0; i < n; i++) {
            double x = env.camX + rs() * 12f, y = env.camY + rf() * 7f, z = env.camZ + rs() * 12f;
            if (airAt(world, x, y, z)) {
                Fx.ash(x, y, z, rs() * 0.01f, -0.01f, rs() * 0.01f, 0.45f + rf() * 0.3f);
            }
        }
        if (rf() < 0.25f * env.kind(Kind.BASALT_DELTAS) * scale) {
            double x = env.camX + rs() * 12f, y = env.camY + rs() * 3f, z = env.camZ + rs() * 12f;
            if (airAt(world, x, y, z)) {
                Fx.smoke(x, y, z, rs() * 0.01f, 0.02f, rs() * 0.01f, 0.5f + rf() * 0.6f, 0.3f);
            }
        }
        float spores = env.kind(Kind.CRIMSON_FOREST) + env.kind(Kind.WARPED_FOREST);
        n = count(0.5f * spores * scale);
        for (int i = 0; i < n; i++) {
            double x = env.camX + rs() * 10f, y = env.camY + rs() * 4f, z = env.camZ + rs() * 10f;
            if (airAt(world, x, y, z)) {
                boolean crimson = rf() < env.kind(Kind.CRIMSON_FOREST) / Math.max(0.001f, spores);
                Fx.mote(x, y, z, Fx.SPORE, crimson ? 1f : 0.2f, crimson ? 0.25f : 0.9f, crimson ? 0.2f : 0.8f, 0.7f,
                        0.03f + rf() * 0.03f, 180f);
            }
        }
    }

    private void end(ClientEnvironment env, float scale) {
        int n = count(0.6f * scale);
        float t = env.ticks * 0.01f;
        for (int i = 0; i < n; i++) {
            double x = env.camX + rs() * 14f, y = env.camY + rs() * 7f, z = env.camZ + rs() * 14f;
            // A slow, curling current that keeps changing direction.
            float a = MathHelper.sin((float) x * 0.07f + t) * 3f + MathHelper.cos((float) z * 0.05f - t * 0.7f) * 2f;
            Fx.endMote(x, y, z, MathHelper.cos(a) * 0.02f, 0.006f + rf() * 0.012f, MathHelper.sin(a) * 0.02f);
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private boolean airAt(ClientWorld world, double x, double y, double z) {
        pos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        return world.getBlockState(pos).isAir();
    }

    private int leafColor(ClientWorld world, int x, int y, int z) {
        pos.set(x, y, z);
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() == Blocks.BIRCH_LEAVES) {
            return 0x80A755;
        }
        if (state.getBlock() == Blocks.SPRUCE_LEAVES) {
            return 0x619961;
        }
        if (state.getBlock() == Blocks.CHERRY_LEAVES) {
            return 0xF2B8D6;
        }
        if (state.getBlock() == Blocks.AZALEA_LEAVES || state.getBlock() == Blocks.FLOWERING_AZALEA_LEAVES) {
            return 0x6B8E3A;
        }
        return BiomeColors.getFoliageColor(world, pos);
    }
}
