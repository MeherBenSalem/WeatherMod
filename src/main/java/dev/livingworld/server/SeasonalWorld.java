package dev.livingworld.server;

import dev.livingworld.climate.BiomeAtmosphereRegistry;
import dev.livingworld.climate.ClimateMath;
import dev.livingworld.climate.Seasons;
import dev.livingworld.config.ServerConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;

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

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(SeasonalWorld::tick);
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

    private static void tick(ServerWorld world) {
        ServerConfig config = ServerConfig.get();
        if (!config.seasonalSnowAndIce || !world.getDimension().hasSkyLight() || world.getDimension().hasCeiling()) {
            return;
        }
        float phase = SeasonState.yearPhase(world.getServer());
        if (phase < 0f || world.getPlayers().isEmpty()) {
            return;
        }
        SeasonalBlocks blocks = SeasonalBlocks.get(world);
        Random random = world.random;
        boolean falling = world.isRaining();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            for (int n = 0; n < config.seasonalChangeRate; n++) {
                int x = player.getBlockX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = player.getBlockZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                if (world.isChunkLoaded(x >> 4, z >> 4)) {
                    column(world, blocks, pos, x, z, phase, falling, random, config.maxSnowLayers);
                }
            }
        }
    }

    private static void column(ServerWorld world, SeasonalBlocks blocks, BlockPos.Mutable pos, int x, int z, float phase,
                               boolean falling, Random random, int maxLayers) {
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        pos.set(x, y, z);
        RegistryEntry<Biome> entry = world.getBiome(pos);
        Biome biome = entry.value();
        // Dry biomes get no snow, and where the game already snows the game is in charge.
        if (!biome.hasPrecipitation() || biome.getPrecipitation(pos) == Biome.Precipitation.SNOW) {
            return;
        }
        float seasonality = Seasons.seasonality(BiomeAtmosphereRegistry.get(entry).kind());
        float mean = ClimateMath.biomeMeanC(biome.getTemperature()) + Seasons.temperatureOffsetC(phase, seasonality)
                + ClimateMath.altitudeOffset(y);
        Change change = changeAt(mean);
        if (change == Change.NONE) {
            return;
        }
        BlockState above = world.getBlockState(pos);
        BlockPos.Mutable ground = new BlockPos.Mutable(x, y - 1, z);
        BlockState below = world.getBlockState(ground);

        if (change == Change.THAW) {
            if (blocks.contains(pos)) {
                if (above.isOf(Blocks.SNOW)) {
                    int layers = above.get(SnowBlock.LAYERS);
                    if (layers > 1) {
                        world.setBlockState(pos, above.with(SnowBlock.LAYERS, layers - 1));
                        return;
                    }
                    world.removeBlock(pos, false);
                }
                blocks.remove(pos);
            }
            if (blocks.contains(ground)) {
                if (below.isOf(Blocks.ICE)) {
                    world.setBlockState(ground, Blocks.WATER.getDefaultState());
                }
                blocks.remove(ground);
            }
            return;
        }

        // Snow: only while something is falling, and never where a lamp would melt it.
        if (falling && world.getLightLevel(LightType.BLOCK, pos) < 10) {
            if (above.isAir()) {
                BlockState snow = Blocks.SNOW.getDefaultState();
                if (snow.canPlaceAt(world, pos) && blocks.add(pos)) {
                    world.setBlockState(pos, snow);
                }
            } else if (above.isOf(Blocks.SNOW) && blocks.contains(pos)) {
                int layers = above.get(SnowBlock.LAYERS);
                if (layers < maxLayers && random.nextInt(4) == 0) {
                    world.setBlockState(pos, above.with(SnowBlock.LAYERS, layers + 1));
                }
            }
        }

        // Ice: still water under the sky, from the banks inward, the way the game freezes its own lakes.
        if (change == Change.FREEZE_HARD && below.isOf(Blocks.WATER) && below.getFluidState().isStill()
                && world.getLightLevel(LightType.BLOCK, ground) < 10 && touchesLand(world, ground) && blocks.add(ground)) {
            world.setBlockState(ground, Blocks.ICE.getDefaultState());
        }
    }

    /** True if at least one of the four neighbours is not water: the bank, or ice that has already formed. */
    private static boolean touchesLand(ServerWorld world, BlockPos water) {
        BlockPos.Mutable side = new BlockPos.Mutable();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            side.set(water, direction);
            if (!world.getFluidState(side).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}
