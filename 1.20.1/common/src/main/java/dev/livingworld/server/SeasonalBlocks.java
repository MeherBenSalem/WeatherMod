package dev.livingworld.server;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which snow layers and ice blocks in a dimension were put there by the seasons (see
 * {@link SeasonalWorld}). Only these are ever thawed again, so snow and ice a player placed, and the
 * snow and ice of cold biomes, are never touched. Saved with the dimension.
 */
public final class SeasonalBlocks extends SavedData {
    private static final String ID = "livingworld_seasonal_blocks";
    /** Beyond this the winter stops spreading in this dimension: about three megabytes of positions. */
    static final int LIMIT = 400_000;

    private final LongOpenHashSet positions = new LongOpenHashSet();

    public static SeasonalBlocks get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(SeasonalBlocks::read, SeasonalBlocks::new, ID);
    }

    private static SeasonalBlocks read(CompoundTag nbt) {
        SeasonalBlocks state = new SeasonalBlocks();
        for (long position : nbt.getLongArray("blocks")) {
            state.positions.add(position);
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
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
            setDirty();
        }
        return true;
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) {
            setDirty();
        }
    }

    public int size() {
        return positions.size();
    }
}
