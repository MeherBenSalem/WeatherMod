package dev.livingworld.assetgen;

import java.io.File;
import java.nio.file.Path;

/**
 * Procedural texture generator for Living World. Plain JDK, deterministic (fixed seeds, StrictMath).
 *
 * <pre>
 *   java dev.livingworld.assetgen.AssetGen &lt;textures-dir&gt; [icon.png]
 * </pre>
 *
 * Writes, relative to the textures directory:
 * <ul>
 *   <li>fx/particles.png      256x256, 8x8 grid of 32px cells</li>
 *   <li>fx/decals.png         64x64, 4x4 grid of 16px cells (one block each), pixel-art, pre-shaded fallback</li>
 *   <li>fx/decals_n.png       64x64, same grid: RG normal, B height, A coverage</li>
 *   <li>fx/noise.png          256x256, tileable: R perlin, G fbm, B worley, A blue noise</li>
 *   <li>overlay/frost.png     512x512</li>
 *   <li>overlay/lens_drops.png 256x256: RG refraction offset, B specular, A mask</li>
 * </ul>
 * and the mod icon (128x128). When the textures directory is the usual
 * {@code src/client/resources/assets/livingworld/textures}, the icon goes to
 * {@code src/main/resources/assets/livingworld/icon.png}; pass a second argument to put it elsewhere.
 */
public final class AssetGen {
    private AssetGen() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: AssetGen <textures-output-dir> [icon.png]");
            System.exit(2);
        }
        System.setProperty("java.awt.headless", "true");
        File out = new File(args[0]).getAbsoluteFile();
        long t0 = System.currentTimeMillis();

        write(ParticleAtlas.generate(), new File(out, "fx/particles.png"));

        PixelDecals decals = PixelDecals.generate();
        write(decals.shaded, new File(out, "fx/decals.png"));
        write(decals.normal, new File(out, "fx/decals_n.png"));

        write(NoiseTexture.generate(), new File(out, "fx/noise.png"));
        write(Overlays.frost(), new File(out, "overlay/frost.png"));
        write(Overlays.lensDrops(), new File(out, "overlay/lens_drops.png"));

        write(IconArt.generate(), iconFile(out, args.length > 1 ? args[1] : null));
        System.out.println("done in " + (System.currentTimeMillis() - t0) + " ms");
    }

    private static File iconFile(File textures, String override) {
        if (override != null) return new File(override).getAbsoluteFile();
        Path p = textures.toPath().normalize();
        String unix = p.toString().replace('\\', '/');
        if (unix.endsWith("src/client/resources/assets/livingworld/textures")) {
            // <root>/src/client/resources/assets/livingworld/textures -> <root>/src/main/resources/assets/livingworld
            return p.resolve("../../../../../main/resources/assets/livingworld/icon.png").normalize().toFile();
        }
        return new File(textures.getParentFile(), "icon.png");
    }

    private static void write(Img img, File f) throws Exception {
        img.save(f);
        System.out.println("wrote " + f + "  (" + img.w + "x" + img.h + ")");
    }
}
