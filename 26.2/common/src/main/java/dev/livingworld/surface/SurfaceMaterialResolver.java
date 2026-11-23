package dev.livingworld.surface;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Classifies block states into {@link SurfaceMaterial}s. Resolution order: explicit block registrations,
 * registered tags, built-in vanilla tags, and finally the block's step sound, which gives modded blocks a
 * reasonable material for free.
 */
public final class SurfaceMaterialResolver {
    private static final Map<Block, SurfaceMaterial> EXPLICIT = new IdentityHashMap<>();
    private static final List<TagRule> TAG_RULES = new ArrayList<>();
    private static final Map<Block, SurfaceMaterial> CACHE = new IdentityHashMap<>();

    private record TagRule(TagKey<Block> tag, SurfaceMaterial material) {
    }

    private SurfaceMaterialResolver() {
    }

    public static synchronized void register(Block block, SurfaceMaterial material) {
        EXPLICIT.put(block, material);
        CACHE.clear();
    }

    public static synchronized void register(TagKey<Block> tag, SurfaceMaterial material) {
        TAG_RULES.add(0, new TagRule(tag, material));
        CACHE.clear();
    }

    /** Tags are reloadable; call when they change. */
    public static synchronized void invalidate() {
        CACHE.clear();
    }

    public static synchronized SurfaceMaterial resolve(BlockState state) {
        Block block = state.getBlock();
        SurfaceMaterial cached = CACHE.get(block);
        if (cached == null) {
            cached = classify(state);
            CACHE.put(block, cached);
        }
        return cached;
    }

    private static SurfaceMaterial classify(BlockState state) {
        Block block = state.getBlock();
        SurfaceMaterial explicit = EXPLICIT.get(block);
        if (explicit != null) {
            return explicit;
        }
        for (TagRule rule : TAG_RULES) {
            if (state.is(rule.tag)) {
                return rule.material;
            }
        }
        if (state.isAir()) {
            return SurfaceMaterial.NONE;
        }
        if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN || block == Blocks.SEAGRASS
                || block == Blocks.TALL_SEAGRASS || block == Blocks.KELP || block == Blocks.KELP_PLANT) {
            return SurfaceMaterial.WATER;
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return SurfaceMaterial.NONE;
        }
        if (block == Blocks.RED_SAND) {
            return SurfaceMaterial.RED_SAND;
        }
        if (state.is(BlockTags.SAND)) {
            return SurfaceMaterial.SAND;
        }
        if (state.is(BlockTags.SNOW) || block == Blocks.POWDER_SNOW) {
            return SurfaceMaterial.SNOW;
        }
        if (block == Blocks.MUD || block == Blocks.MUDDY_MANGROVE_ROOTS) {
            return SurfaceMaterial.MUD;
        }
        if (block == Blocks.CLAY) {
            return SurfaceMaterial.CLAY;
        }
        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL) {
            return SurfaceMaterial.SOUL;
        }
        if (block == Blocks.GRAVEL) {
            return SurfaceMaterial.GRAVEL;
        }
        if (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL) {
            return SurfaceMaterial.GRASS;
        }
        if (block == Blocks.MOSS_BLOCK || block == Blocks.MOSS_CARPET) {
            return SurfaceMaterial.MOSS;
        }
        if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH || state.is(BlockTags.DIRT)) {
            return SurfaceMaterial.DIRT;
        }
        if (state.is(BlockTags.NYLIUM) || block == Blocks.NETHERRACK) {
            return SurfaceMaterial.ASH;
        }
        if (state.is(BlockTags.ICE)) {
            return SurfaceMaterial.ICE;
        }
        if (state.is(BlockTags.LEAVES)) {
            return SurfaceMaterial.LEAVES;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_STAIRS)) {
            return SurfaceMaterial.WOOD;
        }
        SoundType sound = state.getSoundType();
        if (sound == SoundType.SAND) {
            return SurfaceMaterial.SAND;
        }
        if (sound == SoundType.SNOW || sound == SoundType.POWDER_SNOW) {
            return SurfaceMaterial.SNOW;
        }
        if (sound == SoundType.MUD || sound == SoundType.PACKED_MUD) {
            return SurfaceMaterial.MUD;
        }
        if (sound == SoundType.GRAVEL || sound == SoundType.ROOTED_DIRT) {
            return SurfaceMaterial.DIRT;
        }
        if (sound == SoundType.GRASS) {
            return state.canOcclude() ? SurfaceMaterial.GRASS : SurfaceMaterial.NONE;
        }
        if (sound == SoundType.MOSS || sound == SoundType.MOSS_CARPET) {
            return SurfaceMaterial.MOSS;
        }
        if (sound == SoundType.SOUL_SAND || sound == SoundType.SOUL_SOIL) {
            return SurfaceMaterial.SOUL;
        }
        if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.NETHER_WOOD) {
            return SurfaceMaterial.WOOD;
        }
        return state.canOcclude() ? SurfaceMaterial.STONE : SurfaceMaterial.NONE;
    }
}
