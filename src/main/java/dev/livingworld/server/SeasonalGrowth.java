package dev.livingworld.server;

import dev.livingworld.climate.BiomeAtmosphereRegistry;
import dev.livingworld.climate.Seasons;
import dev.livingworld.config.ServerConfig;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;

/**
 * The optional rule that crops, stems, saplings, berry bushes and cocoa follow the year: they grow a
 * little faster in spring, as in vanilla in summer, slowly in autumn and hardly at all in winter. Off by
 * default ({@code seasonalCrops} in the server config, or {@code /livingworld season crops on}).
 *
 * <p>It works by letting only a share of a plant's random growth ticks through, or by granting an extra
 * one, so every plant keeps its own rules (light, farmland, space for a melon). A plant that the sky
 * does not reach (a cellar farm under torches) is left alone: the season is outdoors.
 */
public final class SeasonalGrowth {
    /** Set while an extra spring tick is being given, so that the extra tick is not rolled for again. */
    private static final ThreadLocal<Boolean> EXTRA = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SeasonalGrowth() {
    }

    /** Growth here and now relative to vanilla: below 1 slows plants, above 1 hurries them. */
    static float factor(ServerWorld world, BlockPos pos) {
        if (!ServerConfig.get().seasonalCrops) {
            return 1f;
        }
        if (!world.getDimension().hasSkyLight() || world.getDimension().hasCeiling()) {
            return 1f;
        }
        float phase = SeasonState.yearPhase(world.getServer());
        if (phase < 0f || world.getLightLevel(LightType.SKY, pos) < 5) {
            return 1f;
        }
        float seasonality = Seasons.seasonality(BiomeAtmosphereRegistry.get(world.getBiome(pos)).kind());
        return Seasons.growth(phase, seasonality);
    }

    /** Asked at the start of a plant's random tick: false means this tick is skipped. */
    public static boolean allow(ServerWorld world, BlockPos pos, Random random) {
        if (EXTRA.get()) {
            return true;
        }
        float factor = factor(world, pos);
        return factor >= 1f || random.nextFloat() < factor;
    }

    /** Called at the end of a plant's random tick: in spring, sometimes gives it another. */
    public static void bonus(ServerWorld world, BlockPos pos, Random random) {
        if (EXTRA.get()) {
            return;
        }
        float factor = factor(world, pos);
        if (factor <= 1f || random.nextFloat() >= factor - 1f) {
            return;
        }
        EXTRA.set(Boolean.TRUE);
        try {
            BlockState now = world.getBlockState(pos);
            if (now.hasRandomTicks()) {
                now.randomTick(world, pos, random);
            }
        } finally {
            EXTRA.set(Boolean.FALSE);
        }
    }
}
