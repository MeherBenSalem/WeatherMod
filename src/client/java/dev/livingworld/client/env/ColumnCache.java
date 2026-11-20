package dev.livingworld.client.env;

import dev.livingworld.surface.SurfaceMaterial;
import dev.livingworld.surface.SurfaceMaterialResolver;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;

/**
 * A 64x64 wrap-around grid of terrain columns around the camera: the height of the surface that stops
 * precipitation, the ground under any tree canopy, and what both are made of.
 *
 * <p>It is the one place the weather systems look at the terrain, so rain culling, splashes, the rain
 * audio mix and the screen-space wetness pass all agree, and the world is never scanned more than a few
 * hundred columns per tick. Entries are refreshed lazily when a lookup lands on a stale cell and by a
 * rolling sweep that picks up block changes within about a second.
 */
public final class ColumnCache {
    public static final int SIZE = 64;
    private static final int MASK = SIZE - 1;
    private static final int COUNT = SIZE * SIZE;
    private static final int SWEEP_PER_TICK = 192;
    /** Height reported for columns in unloaded chunks: nothing blocks, nothing to hit. */
    public static final float NO_SURFACE = -4096f;

    private static final SurfaceMaterial[] MATERIALS = SurfaceMaterial.values();

    private final int[] colX = new int[COUNT];
    private final int[] colZ = new int[COUNT];
    private final boolean[] valid = new boolean[COUNT];
    private final float[] top = new float[COUNT];
    private final float[] ground = new float[COUNT];
    private final byte[] topMaterial = new byte[COUNT];
    private final byte[] groundMaterial = new byte[COUNT];

    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private ClientWorld world;
    private int sweepCursor;

    private NativeImageBackedTexture texture;
    private boolean textureDirty;
    private int ticksSinceUpload;

    /** Share of exposed surfaces near the camera per audio class: leaves, stone, water, wood, soft. */
    public final float[] surfaceMix = new float[5];

    public void setWorld(ClientWorld world) {
        this.world = world;
        java.util.Arrays.fill(valid, false);
    }

    public void tick(int camX, int camZ) {
        if (world == null) {
            return;
        }
        for (int n = 0; n < SWEEP_PER_TICK; n++) {
            int i = sweepCursor++ & (COUNT - 1);
            int wx = camX - SIZE / 2 + (i & MASK);
            int wz = camZ - SIZE / 2 + (i >> 6);
            refresh(index(wx, wz), wx, wz);
        }
        ticksSinceUpload++;
    }

    private static int index(int wx, int wz) {
        return (wx & MASK) | ((wz & MASK) << 6);
    }

    private int lookup(int wx, int wz) {
        int i = index(wx, wz);
        if (!valid[i] || colX[i] != wx || colZ[i] != wz) {
            refresh(i, wx, wz);
        }
        return i;
    }

    /** World height of the surface that stops rain/snow in this column. */
    public float top(int wx, int wz) {
        return top[lookup(wx, wz)];
    }

    /** World height of the ground beneath any leaves. Equal to {@link #top} where there is no canopy. */
    public float ground(int wx, int wz) {
        return ground[lookup(wx, wz)];
    }

    public SurfaceMaterial topMaterial(int wx, int wz) {
        return MATERIALS[topMaterial[lookup(wx, wz)]];
    }

    public SurfaceMaterial groundMaterial(int wx, int wz) {
        return MATERIALS[groundMaterial[lookup(wx, wz)]];
    }

    public boolean hasCanopy(int wx, int wz) {
        int i = lookup(wx, wz);
        return top[i] - ground[i] > 1.5f;
    }

    private void refresh(int i, int wx, int wz) {
        colX[i] = wx;
        colZ[i] = wz;
        valid[i] = true;
        if (world == null || !world.getChunkManager().isChunkLoaded(wx >> 4, wz >> 4)) {
            top[i] = NO_SURFACE;
            ground[i] = NO_SURFACE;
            topMaterial[i] = 0;
            groundMaterial[i] = 0;
            writeTexel(i, wx, wz);
            return;
        }
        int free = world.getTopY(Heightmap.Type.MOTION_BLOCKING, wx, wz);
        pos.set(wx, free - 1, wz);
        BlockState state = world.getBlockState(pos);
        float surface = surfaceHeight(state, free - 1);
        SurfaceMaterial material = SurfaceMaterialResolver.resolve(state);

        // A thin snow layer does not block movement, so the heightmap looks straight through it.
        pos.set(wx, free, wz);
        BlockState above = world.getBlockState(pos);
        if (above.getBlock() == Blocks.SNOW) {
            surface = free + above.get(SnowBlock.LAYERS) / 8f;
            material = SurfaceMaterial.SNOW;
        }

        float groundHeight = surface;
        SurfaceMaterial groundMat = material;
        if (material == SurfaceMaterial.LEAVES) {
            int bottom = Math.max(world.getBottomY(), free - 48);
            for (int y = free - 2; y >= bottom; y--) {
                pos.set(wx, y, wz);
                BlockState below = world.getBlockState(pos);
                if (below.isAir() || below.isIn(BlockTags.LEAVES)) {
                    continue;
                }
                FluidState fluid = below.getFluidState();
                if (fluid.isEmpty() && below.getCollisionShape(world, pos).isEmpty()) {
                    continue;
                }
                groundHeight = surfaceHeight(below, y);
                groundMat = SurfaceMaterialResolver.resolve(below);
                break;
            }
        }

        top[i] = surface;
        ground[i] = groundHeight;
        topMaterial[i] = (byte) material.ordinal();
        groundMaterial[i] = (byte) groundMat.ordinal();
        writeTexel(i, wx, wz);
    }

    private float surfaceHeight(BlockState state, int y) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return y + fluid.getHeight(world, pos);
        }
        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) {
            return y + 1f;
        }
        return y + (float) shape.getMax(Direction.Axis.Y);
    }

    /** How readily water pools on a material (0..1). */
    private static float puddleCapacity(SurfaceMaterial m) {
        return switch (m) {
            case STONE, MUD, CLAY -> 1f;
            case DIRT -> 0.9f;
            case WOOD -> 0.75f;
            case ICE -> 0.7f;
            case GRAVEL -> 0.6f;
            case GRASS -> 0.55f;
            case MOSS -> 0.45f;
            case SAND, RED_SAND -> 0.28f;
            default -> 0f;
        };
    }

    // ---- GPU copy for the screen-space wetness pass --------------------------------------------

    private void writeTexel(int i, int wx, int wz) {
        if (texture == null) {
            return;
        }
        NativeImage image = texture.getImage();
        if (image == null) {
            return;
        }
        int h = Math.max(0, Math.min(65535, Math.round((top[i] + 64f) * 16f)));
        int cap = Math.round(puddleCapacity(MATERIALS[topMaterial[i]]) * 255f);
        int r = h & 255;
        int g = (h >> 8) & 255;
        // Blue: 1 where the surface is solid ground (takes damp darkening and snow), 0 for water / nothing.
        SurfaceMaterial material = MATERIALS[topMaterial[i]];
        int b = material == SurfaceMaterial.WATER || material == SurfaceMaterial.NONE ? 0 : 255;
        // NativeImage stores ABGR.
        image.setColor(wx & MASK, wz & MASK, (cap << 24) | (b << 16) | (g << 8) | r);
        textureDirty = true;
    }

    /** GL id of the column texture (64x64, nearest, repeating). Created on first use on the render thread. */
    public int textureId() {
        if (texture == null) {
            texture = new NativeImageBackedTexture(SIZE, SIZE, false);
            java.util.Arrays.fill(valid, false);
            textureDirty = true;
            ticksSinceUpload = 100;
        }
        if (textureDirty && ticksSinceUpload >= 3) {
            NativeImage image = texture.getImage();
            if (image != null) {
                texture.bindTexture();
                image.upload(0, 0, 0, 0, 0, SIZE, SIZE, false, false, false, false);
            }
            textureDirty = false;
            ticksSinceUpload = 0;
        }
        return texture.getGlId();
    }

    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    // ---- Audio surface statistics --------------------------------------------------------------

    /** Recomputes {@link #surfaceMix} from the exposed surfaces within {@code radius} columns of the camera. */
    public void computeSurfaceMix(int camX, int camZ, int radius) {
        float leaves = 0f, stone = 0f, water = 0f, wood = 0f, soft = 0f;
        for (int dz = -radius; dz <= radius; dz += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                float w = 1f / (1f + (dx * dx + dz * dz) * 0.03f);
                switch (topMaterial(camX + dx, camZ + dz)) {
                    case LEAVES -> leaves += w;
                    case STONE, ICE, GRAVEL -> stone += w;
                    case WATER -> water += w;
                    case WOOD -> wood += w;
                    case NONE -> {
                    }
                    default -> soft += w;
                }
            }
        }
        float sum = leaves + stone + water + wood + soft;
        if (sum <= 0f) {
            sum = 1f;
            soft = 1f;
        }
        surfaceMix[0] = leaves / sum;
        surfaceMix[1] = stone / sum;
        surfaceMix[2] = water / sum;
        surfaceMix[3] = wood / sum;
        surfaceMix[4] = soft / sum;
    }
}
