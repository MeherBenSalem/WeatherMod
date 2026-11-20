package dev.livingworld.api;

import dev.livingworld.climate.BiomeAtmosphereRegistry;
import dev.livingworld.climate.BiomeProfile;
import dev.livingworld.surface.SurfaceMaterial;
import dev.livingworld.surface.SurfaceMaterialResolver;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;

/**
 * Extension points for other mods. Safe to call from a common initializer on either side.
 *
 * <pre>{@code
 * LivingWorldApi.registerBiome(MyBiomes.SALT_FLATS,
 *         BiomeProfile.Kind.DESERT.profile().withDustiness(0.6f).withWindiness(1.3f));
 * LivingWorldApi.registerSurface(MyBlocks.VOLCANIC_ASH, SurfaceMaterial.ASH);
 * }</pre>
 */
public final class LivingWorldApi {
    private LivingWorldApi() {
    }

    /** Gives a biome its atmospheric identity. Unregistered biomes are classified automatically. */
    public static void registerBiome(RegistryKey<Biome> biome, BiomeProfile profile) {
        BiomeAtmosphereRegistry.register(biome, profile);
    }

    /** Declares how a block behaves underfoot (footprints, dust, splashes, footstep audio). */
    public static void registerSurface(Block block, SurfaceMaterial material) {
        SurfaceMaterialResolver.register(block, material);
    }

    /** Declares how every block in a tag behaves underfoot. Later registrations take precedence. */
    public static void registerSurface(TagKey<Block> tag, SurfaceMaterial material) {
        SurfaceMaterialResolver.register(tag, material);
    }
}
