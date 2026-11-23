package dev.livingworld.server;

import dev.livingworld.climate.BiomeAtmosphereRegistry;
import dev.livingworld.climate.ClimateMath;
import dev.livingworld.climate.Seasons;
import dev.livingworld.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The optional rule that winter is real: where the season makes it cold enough, snow settles and builds
 * up while it is snowing, still water freezes over from its edges inward, and both go again when the
 * weather turns. Off by default ({@code seasonalSnowAndIce} in the server config, or
 * {@code /livingworld season snow on}), because it is the one thing in this mod that changes blocks.
 *
 * <p>It never touches a block it did not place: every snow layer and ice block it sets is recorded in
 * {@link SeasonalBlocks}, and only recorded ones are thawed. Biomes where the game itself keeps snow are
 * left to the game.
 *
 * <p>Each tick a few random columns near every player are looked at, so a snowfall whitens the land
 * over a minute or two and a thaw clears it at the same pace, with no sweep of the world.
 */
public final class SeasonalWorld {
    /** Columns are picked within this many blocks of a player. */
    private static final int RADIUS = 64;

    /** What the season does to a column of ground, decided from its daily mean temperature alone. */
    public enum Change {
        FREEZE_HARD, FREEZE, NONE, THAW
    }

    private SeasonalWorld() {
    }

    /**
     * Snow settles below 3 degrees and water freezes below 0, on the daily mean (nights are colder than
     * that); what winter laid down goes again once the mean is back above 5. The gap between the two
     * keeps a column from flickering between white and green on a day near the line.
     */
    public static Change changeAt(float dailyMeanC) {
        if (dailyMeanC < 0f) {
            return Change.FREEZE_HARD;
        }
        if (dailyMeanC < 3f) {
            return Change.FREEZE;
        }
        return dailyMeanC > 5f ? Change.THAW : Change.NONE;
    }

    /** Called at the end of every tick of every dimension. */
    public static void tick(ServerLevel world) {
        ServerConfig config = ServerConfig.get();
        if (!config.seasonalSnowAndIce || !world.dimensionType().hasSkyLight() || world.dimensionType().hasCeiling()) {
            return;
        }
        float phase = SeasonState.yearPhase(world.getServer());
        if (phase < 0f || world.players().isEmpty()) {
            return;
        }
        SeasonalBlocks blocks = SeasonalBlocks.get(world);
        RandomSource random = world.random;
        boolean falling = world.isRaining();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) {
                continue;
            }
            for (int n = 0; n < config.seasonalChangeRate; n++) {
                int x = player.getBlockX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = player.getBlockZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                if (world.hasChunk(x >> 4, z >> 4)) {
                    column(world, blocks, pos, x, z, phase, falling, random, config.maxSnowLayers);
                }
            }
        }
    }

    private static void column(ServerLevel world, SeasonalBlocks blocks, BlockPos.MutableBlockPos pos, int x, int z, float phase,
                               boolean falling, RandomSource random, int maxLayers) {
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        pos.set(x, y, z);
        Holder<Biome> entry = world.getBiome(pos);
        Biome biome = entry.value();
        // Dry biomes get no snow, and where the game already snows the game is in charge.
        if (!biome.hasPrecipitation() || biome.getPrecipitationAt(pos) == Biome.Precipitation.SNOW) {
            return;
        }
        float seasonality = Seasons.seasonality(BiomeAtmosphereRegistry.get(entry).kind());
        float mean = ClimateMath.biomeMeanC(biome.getBaseTemperature()) + Seasons.temperatureOffsetC(phase, seasonality)
                + ClimateMath.altitudeOffset(y);
        Change change = changeAt(mean);
        if (change == Change.NONE) {
            return;
        }
        BlockState above = world.getBlockState(pos);
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos(x, y - 1, z);
        BlockState below = world.getBlockState(ground);

        if (change == Change.THAW) {
            if (blocks.contains(pos)) {
                if (above.is(Blocks.SNOW)) {
                    int layers = above.getValue(SnowLayerBlock.LAYERS);
                    if (layers > 1) {
                        world.setBlockAndUpdate(pos, above.setValue(SnowLayerBlock.LAYERS, layers - 1));
                        return;
                    }
                    world.removeBlock(pos, false);
                }
                blocks.remove(pos);
            }
            if (blocks.contains(ground)) {
                if (below.is(Blocks.ICE)) {
                    world.setBlockAndUpdate(ground, Blocks.WATER.defaultBlockState());
                }
                blocks.remove(ground);
            }
            return;
        }

        // Snow: only while something is falling, and never where a lamp would melt it.
        if (falling && world.getBrightness(LightLayer.BLOCK, pos) < 10) {
            if (above.isAir()) {
                BlockState snow = Blocks.SNOW.defaultBlockState();
                if (snow.canSurvive(world, pos) && blocks.add(pos)) {
                    world.setBlockAndUpdate(pos, snow);
                }
            } else if (above.is(Blocks.SNOW) && blocks.contains(pos)) {
                int layers = above.getValue(SnowLayerBlock.LAYERS);
                if (layers < maxLayers && random.nextInt(4) == 0) {
                    world.setBlockAndUpdate(pos, above.setValue(SnowLayerBlock.LAYERS, layers + 1));
                }
            }
        }

        // Ice: still water under the sky, from the banks inward, the way the game freezes its own lakes.
        if (change == Change.FREEZE_HARD && below.is(Blocks.WATER) && below.getFluidState().isSource()
                && world.getBrightness(LightLayer.BLOCK, ground) < 10 && touchesLand(world, ground) && blocks.add(ground)) {
            world.setBlockAndUpdate(ground, Blocks.ICE.defaultBlockState());
        }
    }

    /** True if at least one of the four neighbours is not water: the bank, or ice that has already formed. */
    private static boolean touchesLand(ServerLevel world, BlockPos water) {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            side.setWithOffset(water, direction);
            if (!world.getFluidState(side).is(net.minecraft.tags.FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}
