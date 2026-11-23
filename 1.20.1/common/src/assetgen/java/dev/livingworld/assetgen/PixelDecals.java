package dev.livingworld.assetgen;

/**
 * Blocky, pixel-art ground decals: footprints and tracks drawn at the same texel density as Minecraft's
 * own block textures (16 texels per block), so a print looks like pixels pressed into the block rather
 * than a smooth photo-real impression.
 *
 * <p>Each sprite is a 16x16 cell (one block) holding a small depth pattern. Two sheets are produced from
 * the same patterns, both 64x64 with a 4x4 grid of cells, meant to be sampled with nearest filtering:
 * <ul>
 *   <li>{@code decals_n.png}: R,G = per-texel normal, B = height (128 = ground), A = 1 on print texels.
 *       The game lights every texel against the real sun direction, so the print's sunny and shadowed
 *       edge texels swap sides through the day.</li>
 *   <li>{@code decals.png}: pre-shaded fallback (light from the top-left) for the simple render path.</li>
 * </ul>
 *
 * <p>Cells 0-11 are the straight sprites, toe toward the top of the cell. Cells 12-14 are the boot,
 * sprint boot and sole rasterised at 45 degrees (toe toward the top-right), so the renderer can show
 * eight walking directions while every texel stays on the block grid.
 */
final class PixelDecals {
    static final int CELL = 16;
    static final int GRID = 4;
    static final int SIZE = CELL * GRID;

    // Depth legend: '.' untouched ground, '1' shallow, '2' medium, '3' deep.
    private static final String[][] SPRITES = {
            // 0 boot: rounded toe, ball of the foot, a gap under the arch, heel
            sprite(4,
                    ".......22.......",
                    "......2332......",
                    "......3333......",
                    "......2332......",
                    "................",
                    "......2332......",
                    ".......22......."),
            // 1 sprinting boot: weight on the toe, barely a heel, material kicked out behind
            sprite(3,
                    ".......22.......",
                    "......3333......",
                    "......3333......",
                    "......2332......",
                    "................",
                    ".......11.......",
                    "................",
                    ".......1........",
                    ".........1......"),
            // 2 cloven hoof
            sprite(6,
                    ".....22..22.....",
                    ".....33..33.....",
                    ".....33..33.....",
                    ".....22..22....."),
            // 3 paw
            sprite(4,
                    ".......22.......",
                    "......2..2......",
                    "................",
                    "......3333......",
                    ".......33......."),
            // 4 landing crater
            sprite(4,
                    ".....222222.....",
                    "....23333332....",
                    "....32222223....",
                    "....32111123....",
                    "....32111123....",
                    "....32222223....",
                    "....23333332....",
                    ".....222222....."),
            // 5 bird track
            sprite(6,
                    "......2.2.2.....",
                    ".......222......",
                    "........2.......",
                    "........2......."),
            // 6 scuff
            sprite(5,
                    ".......22.......",
                    ".......11.......",
                    ".......11.......",
                    ".......11.......",
                    "........1......."),
            // 7 sole (wet print on hard ground): tread rows with gaps
            sprite(4,
                    ".......22.......",
                    "......2222......",
                    "......2..2......",
                    "......2222......",
                    "................",
                    "......2222......",
                    ".......22......."),
            // 8 splat
            sprite(4,
                    "......2.....2...",
                    ".....2222.......",
                    "....222222......",
                    "....2222222.....",
                    ".....22222......",
                    "...2..222.......",
                    ".........2......"),
            // 9 heavy round print
            sprite(5,
                    ".....333333.....",
                    ".....322223.....",
                    ".....322223.....",
                    ".....322223.....",
                    ".....322223.....",
                    ".....333333....."),
            // 10 small dots
            sprite(6,
                    ".......2.2......",
                    ".....2.....2...."),
            // 11 horseshoe
            sprite(5,
                    "......3333......",
                    ".....33..33.....",
                    ".....3....3.....",
                    ".....3....3.....",
                    ".....2....2....."),
    };

    /** Which straight sprites get a 45-degree variant, in the order of cells 12, 13, 14. */
    private static final int[] DIAGONALS = {0, 1, 7};

    final Img shaded = new Img(SIZE, SIZE);
    final Img normal = new Img(SIZE, SIZE);

    private PixelDecals() {
    }

    /** Pads a pattern to 16 rows, starting at the given row. */
    private static String[] sprite(int firstRow, String... rows) {
        String[] out = new String[CELL];
        java.util.Arrays.fill(out, "................");
        for (int i = 0; i < rows.length; i++) {
            if (rows[i].length() != CELL) {
                throw new IllegalArgumentException("sprite row must be 16 wide: " + rows[i]);
            }
            out[firstRow + i] = rows[i];
        }
        return out;
    }

    static PixelDecals generate() {
        PixelDecals d = new PixelDecals();
        d.shaded.fill(0.5, 0.5, 0.5, 0);
        d.normal.fill(0.5, 0.5, 0.5, 0);
        for (int i = 0; i < SPRITES.length; i++) {
            d.draw(i, depth(SPRITES[i]));
        }
        for (int i = 0; i < DIAGONALS.length; i++) {
            d.draw(12 + i, rotate45(depth(SPRITES[DIAGONALS[i]])));
        }
        return d;
    }

    private static float[][] depth(String[] rows) {
        float[][] h = new float[CELL][CELL];
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                char c = rows[y].charAt(x);
                h[y][x] = c == '1' ? 0.42f : c == '2' ? 0.72f : c == '3' ? 1f : 0f;
            }
        }
        return h;
    }

    /** Re-rasterises a pattern turned 45 degrees clockwise (toe from "up" to "up-right"), texel by texel. */
    private static float[][] rotate45(float[][] src) {
        float[][] out = new float[CELL][CELL];
        double c = StrictMath.cos(StrictMath.PI / 4), s = StrictMath.sin(StrictMath.PI / 4);
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                double tx = x + 0.5 - CELL / 2.0, ty = y + 0.5 - CELL / 2.0;
                int sx = (int) StrictMath.floor(tx * c + ty * s + CELL / 2.0);
                int sy = (int) StrictMath.floor(-tx * s + ty * c + CELL / 2.0);
                if (sx >= 0 && sy >= 0 && sx < CELL && sy < CELL) {
                    out[y][x] = src[sy][sx];
                }
            }
        }
        return out;
    }

    private static float at(float[][] depth, int x, int y) {
        return x < 0 || y < 0 || x >= CELL || y >= CELL ? 0f : depth[y][x];
    }

    private void draw(int cell, float[][] depth) {
        int ox = (cell % GRID) * CELL, oy = (cell / GRID) * CELL;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                float d = depth[y][x];
                if (d <= 0f) {
                    continue;
                }
                // Height is -depth. Slope from the neighbouring texels; the normal leans away from the uphill side.
                float gx = -(at(depth, x + 1, y) - at(depth, x - 1, y)) * 0.5f;
                float gyDown = -(at(depth, x, y + 1) - at(depth, x, y - 1)) * 0.5f;
                double nx = -gx * 1.6, nyUp = gyDown * 1.6, nz = 1.0;
                double len = StrictMath.sqrt(nx * nx + nyUp * nyUp + nz * nz);
                nx /= len;
                nyUp /= len;
                nz /= len;
                normal.set(ox + x, oy + y, nx * 0.5 + 0.5, nyUp * 0.5 + 0.5, 0.5 - d * 0.34, 1.0);

                // Fallback sheet: lit from the top-left (light direction x = -0.55, up-in-image = +0.55, z = 0.63).
                double lit = nx * -0.55 + nyUp * 0.55 + nz * 0.63 - 0.63;
                // Always darker than the ground around it, so it reads as pressed in, never as a raised stud.
                double level = 0.72 - d * 0.2 + lit * 0.4;
                level = level < 0.42 ? 0.42 : StrictMath.min(level, 0.92);
                shaded.set(ox + x, oy + y, level, level, level, 0.55 + 0.3 * d);
            }
        }
    }
}
