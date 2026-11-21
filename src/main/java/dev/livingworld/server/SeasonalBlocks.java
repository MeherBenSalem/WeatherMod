package dev.livingworld.server;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * Which snow layers and ice blocks in a dimension were put there by the seasons (see
 * {@link SeasonalWorld}). Only these are ever thawed again, so snow and ice a player placed, and the
 * snow and ice of cold biomes, are never touched. Saved with the dimension.
 */
public final class SeasonalBlocks extends PersistentState {
    private static final String ID = "livingworld_seasonal_blocks";
    /** Beyond this the winter stops spreading in this dimension: about three megabytes of positions. */
    static final int LIMIT = 400_000;

    private final LongOpenHashSet positions = new LongOpenHashSet();

    public static SeasonalBlocks get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(SeasonalBlocks::read, SeasonalBlocks::new, ID);
    }

    private static SeasonalBlocks read(NbtCompound nbt) {
        SeasonalBlocks state = new SeasonalBlocks();
        for (long position : nbt.getLongArray("blocks")) {
            state.positions.add(position);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLongArray("blocks", positions.toLongArray());
        return nbt;
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    /** Remembers a block the seasons placed. False when the dimension's limit is reached (place nothing). */
    public boolean add(BlockPos pos) {
        if (positions.size() >= LIMIT) {
            return false;
        }
        if (positions.add(pos.asLong())) {
            markDirty();
        }
        return true;
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) {
            markDirty();
        }
    }

    public int size() {
        return positions.size();
    }
}
