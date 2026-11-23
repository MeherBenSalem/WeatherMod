package dev.livingworld.climate;

import dev.livingworld.climate.BiomeProfile.Kind;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Maps every biome (vanilla or modded) to a {@link BiomeProfile}. Explicit registrations win; anything
 * unknown is classified from its tags, temperature and precipitation so modded biomes get sensible
 * behaviour without any integration work.
 */
public final class BiomeAtmosphereRegistry {
    private static final Map<ResourceKey<Biome>, BiomeProfile> BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Biome, BiomeProfile> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private BiomeAtmosphereRegistry() {
    }

    public static void register(ResourceKey<Biome> key, BiomeProfile profile) {
        BY_KEY.put(key, profile);
        CACHE.clear();
    }

    public static BiomeProfile get(Holder<Biome> entry) {
        Biome biome = entry.value();
        BiomeProfile cached = CACHE.get(biome);
        if (cached != null) {
            return cached;
        }
        BiomeProfile resolved = entry.unwrapKey().map(BY_KEY::get).orElse(null);
        if (resolved == null) {
            resolved = classify(entry);
        }
        CACHE.put(biome, resolved);
        return resolved;
    }

    private static BiomeProfile classify(Holder<Biome> entry) {
        Biome biome = entry.value();
        float t = biome.getBaseTemperature();
        boolean wet = biome.hasPrecipitation();
        if (entry.is(BiomeTags.IS_NETHER)) {
            return Kind.NETHER_WASTES.profile();
        }
        if (entry.is(BiomeTags.IS_END)) {
            return Kind.END.profile();
        }
        if (entry.is(BiomeTags.IS_OCEAN)) {
            return Kind.OCEAN.profile();
        }
        if (entry.is(BiomeTags.IS_BEACH)) {
            return Kind.BEACH.profile();
        }
        if (entry.is(BiomeTags.IS_RIVER)) {
            return Kind.RIVER.profile();
        }
        if (entry.is(BiomeTags.IS_BADLANDS)) {
            return Kind.BADLANDS.profile();
        }
        if (entry.is(BiomeTags.IS_JUNGLE)) {
            return Kind.JUNGLE.profile();
        }
        if (entry.is(BiomeTags.IS_SAVANNA)) {
            return Kind.SAVANNA.profile();
        }
        if (entry.is(BiomeTags.IS_MOUNTAIN)) {
            return t < 0.15f ? Kind.MOUNTAIN.profile().withHumidity(0.45f) : Kind.MOUNTAIN.profile();
        }
        if (t < 0.15f) {
            return Kind.SNOWY.profile();
        }
        if (entry.is(BiomeTags.IS_TAIGA)) {
            return Kind.TAIGA.profile();
        }
        if (entry.is(BiomeTags.IS_FOREST)) {
            return Kind.FOREST.profile();
        }
        if (!wet && t >= 1.5f) {
            return Kind.DESERT.profile();
        }
        if (!wet) {
            return Kind.SAVANNA.profile();
        }
        return t >= 0.9f ? Kind.JUNGLE.profile().withFoliage(0.7f) : Kind.PLAINS.profile();
    }

    private static void put(BiomeProfile profile, ResourceKey<?>... keys) {
        for (ResourceKey<?> key : keys) {
            @SuppressWarnings("unchecked")
            ResourceKey<Biome> biomeKey = (ResourceKey<Biome>) key;
            BY_KEY.put(biomeKey, profile);
        }
    }

    static {
        put(Kind.PLAINS.profile(), Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS);
        put(Kind.PLAINS.profile().withFoliage(0.6f).withWindiness(1.15f), Biomes.MEADOW);
        put(Kind.FOREST.profile(), Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
                Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.CHERRY_GROVE);
        put(Kind.FOREST.profile().withHumidity(0.7f).withWindiness(0.55f), Biomes.DARK_FOREST);
        put(Kind.FOREST.profile().withWindiness(1.15f), Biomes.WINDSWEPT_FOREST);
        put(Kind.TAIGA.profile(), Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        put(Kind.SNOWY.profile().withFoliage(0.6f).withWindiness(0.85f), Biomes.SNOWY_TAIGA, Biomes.GROVE);
        put(Kind.JUNGLE.profile(), Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE);
        put(Kind.JUNGLE.profile().withFoliage(0.7f).withWindiness(0.75f), Biomes.SPARSE_JUNGLE);
        put(Kind.SWAMP.profile(), Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
        put(Kind.DESERT.profile(), Biomes.DESERT);
        put(Kind.BADLANDS.profile(), Biomes.BADLANDS, Biomes.ERODED_BADLANDS);
        put(Kind.BADLANDS.profile().withFoliage(0.25f).withDustiness(0.6f), Biomes.WOODED_BADLANDS);
        put(Kind.SAVANNA.profile(), Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU);
        put(Kind.SAVANNA.profile().withWindiness(1.3f), Biomes.WINDSWEPT_SAVANNA);
        put(Kind.SNOWY.profile(), Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.FROZEN_RIVER,
                Biomes.SNOWY_BEACH);
        put(Kind.MOUNTAIN.profile(), Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
                Biomes.STONY_PEAKS, Biomes.STONY_SHORE);
        put(Kind.MOUNTAIN.profile().withHumidity(0.45f), Biomes.SNOWY_SLOPES, Biomes.FROZEN_PEAKS,
                Biomes.JAGGED_PEAKS);
        put(Kind.OCEAN.profile(), Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN,
                Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.COLD_OCEAN,
                Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN);
        put(Kind.BEACH.profile(), Biomes.BEACH);
        put(Kind.RIVER.profile(), Biomes.RIVER);
        put(Kind.MUSHROOM.profile(), Biomes.MUSHROOM_FIELDS);
        put(Kind.LUSH_CAVES.profile(), Biomes.LUSH_CAVES);
        put(Kind.DRIPSTONE_CAVES.profile(), Biomes.DRIPSTONE_CAVES);
        put(Kind.DEEP_DARK.profile(), Biomes.DEEP_DARK);
        put(Kind.NETHER_WASTES.profile(), Biomes.NETHER_WASTES);
        put(Kind.CRIMSON_FOREST.profile(), Biomes.CRIMSON_FOREST);
        put(Kind.WARPED_FOREST.profile(), Biomes.WARPED_FOREST);
        put(Kind.SOUL_SAND_VALLEY.profile(), Biomes.SOUL_SAND_VALLEY);
        put(Kind.BASALT_DELTAS.profile(), Biomes.BASALT_DELTAS);
        put(Kind.END.profile(), Biomes.THE_END, Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS,
                Biomes.SMALL_END_ISLANDS, Biomes.END_BARRENS);
        put(Kind.VOID.profile(), Biomes.THE_VOID);
    }
}
