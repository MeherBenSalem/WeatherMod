package dev.livingworld.surface;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.BlockSoundGroup;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
            if (state.isIn(rule.tag)) {
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
        if (state.getFluidState().isIn(FluidTags.LAVA)) {
            return SurfaceMaterial.NONE;
        }
        if (block == Blocks.RED_SAND) {
            return SurfaceMaterial.RED_SAND;
        }
        if (state.isIn(BlockTags.SAND)) {
            return SurfaceMaterial.SAND;
        }
        if (state.isIn(BlockTags.SNOW) || block == Blocks.POWDER_SNOW) {
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
        if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH || state.isIn(BlockTags.DIRT)) {
            return SurfaceMaterial.DIRT;
        }
        if (state.isIn(BlockTags.NYLIUM) || block == Blocks.NETHERRACK) {
            return SurfaceMaterial.ASH;
        }
        if (state.isIn(BlockTags.ICE)) {
            return SurfaceMaterial.ICE;
        }
        if (state.isIn(BlockTags.LEAVES)) {
            return SurfaceMaterial.LEAVES;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.WOODEN_SLABS)
                || state.isIn(BlockTags.WOODEN_STAIRS)) {
            return SurfaceMaterial.WOOD;
        }
        BlockSoundGroup sound = state.getSoundGroup();
        if (sound == BlockSoundGroup.SAND) {
            return SurfaceMaterial.SAND;
        }
        if (sound == BlockSoundGroup.SNOW || sound == BlockSoundGroup.POWDER_SNOW) {
            return SurfaceMaterial.SNOW;
        }
        if (sound == BlockSoundGroup.MUD || sound == BlockSoundGroup.PACKED_MUD) {
            return SurfaceMaterial.MUD;
        }
        if (sound == BlockSoundGroup.GRAVEL || sound == BlockSoundGroup.ROOTED_DIRT) {
            return SurfaceMaterial.DIRT;
        }
        if (sound == BlockSoundGroup.GRASS) {
            return state.isOpaque() ? SurfaceMaterial.GRASS : SurfaceMaterial.NONE;
        }
        if (sound == BlockSoundGroup.MOSS_BLOCK || sound == BlockSoundGroup.MOSS_CARPET) {
            return SurfaceMaterial.MOSS;
        }
        if (sound == BlockSoundGroup.SOUL_SAND || sound == BlockSoundGroup.SOUL_SOIL) {
            return SurfaceMaterial.SOUL;
        }
        if (sound == BlockSoundGroup.WOOD || sound == BlockSoundGroup.BAMBOO_WOOD || sound == BlockSoundGroup.CHERRY_WOOD
                || sound == BlockSoundGroup.NETHER_WOOD) {
            return SurfaceMaterial.WOOD;
        }
        return state.isOpaque() ? SurfaceMaterial.STONE : SurfaceMaterial.NONE;
    }
}
