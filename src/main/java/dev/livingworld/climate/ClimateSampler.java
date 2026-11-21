package dev.livingworld.climate;

import dev.livingworld.climate.BiomeProfile.Kind;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Reads the world around a position into a blended climate description. Biomes are sampled on a small
 * weighted stencil so every derived quantity changes gradually while crossing a biome border instead of
 * snapping. Shared by the client (visuals) and the server (optional survival rules).
 */
public final class ClimateSampler {
    /** dx, dz pairs of the sampling stencil. */
    private static final int[] OFFSETS = {
            0, 0,
            12, 0, -12, 0, 0, 12, 0, -12,
            9, 9, -9, 9, 9, -9, -9, -9,
            26, 0, -26, 0, 0, 26, 0, -26,
    };
    private static final float[] WEIGHTS = {
            4f,
            2f, 2f, 2f, 2f,
            2f, 2f, 2f, 2f,
            1f, 1f, 1f, 1f,
    };
    private static final float WEIGHT_SUM = 24f;

    private ClimateSampler() {
    }

    /** Mutable result, reused by callers to avoid per-tick allocation. */
    public static final class Sample {
        /** Blended daily-mean sea-level temperature of the surrounding biomes. */
        public float meanC;
        public float humidity;
        public float windiness;
        public float dustiness;
        public float foliage;
        /** Fractions of the surroundings where precipitation falls as rain / as snow / not at all. */
        public float rainFraction;
        public float snowFraction;
        public float dryFraction;
        /** How strongly the surroundings have seasons, 0..1 (see {@link Seasons#seasonality}). */
        public float seasonality;
        /** Blend weights per biome family, summing to 1. */
        public final float[] kind = new float[Kind.VALUES.length];
        public Kind dominant = Kind.PLAINS;

        public float weight(Kind k) {
            return kind[k.ordinal()];
        }
    }

    public static void sampleBiomes(World world, BlockPos center, Sample out) {
        float mean = 0f, humidity = 0f, windiness = 0f, dustiness = 0f, foliage = 0f;
        float rain = 0f, snow = 0f, dry = 0f;
        float[] kind = out.kind;
        java.util.Arrays.fill(kind, 0f);

        boolean nether = world.getDimension().ultrawarm();
        boolean end = world.getRegistryKey() == World.END;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int i = 0; i < WEIGHTS.length; i++) {
            p.set(center.getX() + OFFSETS[i * 2], center.getY(), center.getZ() + OFFSETS[i * 2 + 1]);
            RegistryEntry<Biome> entry = world.getBiome(p);
            Biome biome = entry.value();
            BiomeProfile profile = BiomeAtmosphereRegistry.get(entry);
            float w = WEIGHTS[i] / WEIGHT_SUM;

            float biomeMean;
            if (nether) {
                biomeMean = profile.kind() == Kind.SOUL_SAND_VALLEY ? 34f
                        : profile.kind() == Kind.BASALT_DELTAS ? 58f : 50f;
            } else if (end) {
                biomeMean = -14f;
            } else {
                biomeMean = ClimateMath.biomeMeanC(biome.getTemperature());
            }
            mean += biomeMean * w;
            humidity += profile.humidity() * w;
            windiness += profile.windiness() * w;
            dustiness += profile.dustiness() * w;
            foliage += profile.foliage() * w;
            kind[profile.kind().ordinal()] += w;

            Biome.Precipitation precipitation = nether || end ? Biome.Precipitation.NONE : biome.getPrecipitation(p);
            if (precipitation == Biome.Precipitation.RAIN) {
                rain += w;
            } else if (precipitation == Biome.Precipitation.SNOW) {
                snow += w;
            } else {
                dry += w;
            }
        }

        out.meanC = mean;
        out.humidity = humidity;
        out.windiness = windiness;
        out.dustiness = dustiness;
        out.foliage = foliage;
        out.rainFraction = rain;
        out.snowFraction = snow;
        out.dryFraction = dry;
        int best = 0;
        float seasonality = 0f;
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] > kind[best]) {
                best = i;
            }
            if (kind[i] > 0f) {
                seasonality += kind[i] * Seasons.seasonality(Kind.VALUES[i]);
            }
        }
        out.seasonality = nether || end ? 0f : seasonality;
        out.dominant = Kind.VALUES[best];
    }

    /**
     * Sums the warming (or cooling) effect of blocks close to a position, in degrees. Costs a few hundred
     * block reads, so callers run it about once per second.
     */
    public static float heatSources(World world, BlockPos center, int radius) {
        float sum = 0f;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(p);
                    if (state.isAir()) {
                        continue;
                    }
                    float heat = heatOf(state);
                    if (heat != 0f) {
                        float distSq = dx * dx + dy * dy + dz * dz;
                        sum += heat / (1f + distSq * 0.3f);
                    }
                }
            }
        }
        return Math.max(-8f, Math.min(sum, 32f));
    }

    private static float heatOf(BlockState state) {
        if (state.getFluidState().isIn(FluidTags.LAVA)) {
            return 9f;
        }
        Block block = state.getBlock();
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
            return 6f;
        }
        if (block instanceof CampfireBlock) {
            return state.get(CampfireBlock.LIT) ? 7f : 0f;
        }
        if (block instanceof AbstractFurnaceBlock) {
            return state.get(AbstractFurnaceBlock.LIT) ? 5f : 0f;
        }
        if (block == Blocks.MAGMA_BLOCK) {
            return 3f;
        }
        if (block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.LANTERN) {
            return 0.6f;
        }
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
            return -1.2f;
        }
        if (block == Blocks.POWDER_SNOW) {
            return -0.6f;
        }
        return 0f;
    }
}
