package dev.livingworld.server;

import com.mojang.serialization.Codec;
import dev.livingworld.LivingWorld;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Optional;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which snow layers and ice blocks in a dimension were put there by the seasons (see
 * {@link SeasonalWorld}). Only these are ever thawed again, so snow and ice a player placed, and the
 * snow and ice of cold biomes, are never touched. Saved with the dimension.
 */
public final class SeasonalBlocks extends SavedData {
    private static final Codec<SeasonalBlocks> CODEC = Codec.LONG_STREAM.optionalFieldOf("blocks").codec()
            .xmap(saved -> of(saved.orElseGet(LongStream::empty)), state -> Optional.of(LongStream.of(state.positions.toLongArray())));
    // Mod data has no data fixers (both loaders accept a null fixer type).
    private static final SavedDataType<SeasonalBlocks> TYPE = new SavedDataType<>(LivingWorld.id("seasonal_blocks"), SeasonalBlocks::new, CODEC, null);
    /** Beyond this the winter stops spreading in this dimension: about three megabytes of positions. */
    static final int LIMIT = 400_000;

    private final LongOpenHashSet positions = new LongOpenHashSet();

    public static SeasonalBlocks get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }

    private static SeasonalBlocks of(LongStream saved) {
        SeasonalBlocks state = new SeasonalBlocks();
        saved.forEach(state.positions::add);
        return state;
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
