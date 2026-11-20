package dev.livingworld.climate;

import dev.livingworld.climate.BiomeProfile.Kind;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps every biome (vanilla or modded) to a {@link BiomeProfile}. Explicit registrations win; anything
 * unknown is classified from its tags, temperature and precipitation so modded biomes get sensible
 * behaviour without any integration work.
 */
public final class BiomeAtmosphereRegistry {
    private static final Map<RegistryKey<Biome>, BiomeProfile> BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Biome, BiomeProfile> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private BiomeAtmosphereRegistry() {
    }

    public static void register(RegistryKey<Biome> key, BiomeProfile profile) {
        BY_KEY.put(key, profile);
        CACHE.clear();
    }

    public static BiomeProfile get(RegistryEntry<Biome> entry) {
        Biome biome = entry.value();
        BiomeProfile cached = CACHE.get(biome);
        if (cached != null) {
            return cached;
        }
        BiomeProfile resolved = entry.getKey().map(BY_KEY::get).orElse(null);
        if (resolved == null) {
            resolved = classify(entry);
        }
        CACHE.put(biome, resolved);
        return resolved;
    }

    private static BiomeProfile classify(RegistryEntry<Biome> entry) {
        Biome biome = entry.value();
        float t = biome.getTemperature();
        boolean wet = biome.hasPrecipitation();
        if (entry.isIn(BiomeTags.IS_NETHER)) {
            return Kind.NETHER_WASTES.profile();
        }
        if (entry.isIn(BiomeTags.IS_END)) {
            return Kind.END.profile();
        }
        if (entry.isIn(BiomeTags.IS_OCEAN)) {
            return Kind.OCEAN.profile();
        }
        if (entry.isIn(BiomeTags.IS_BEACH)) {
            return Kind.BEACH.profile();
        }
        if (entry.isIn(BiomeTags.IS_RIVER)) {
            return Kind.RIVER.profile();
        }
        if (entry.isIn(BiomeTags.IS_BADLANDS)) {
            return Kind.BADLANDS.profile();
        }
        if (entry.isIn(BiomeTags.IS_JUNGLE)) {
            return Kind.JUNGLE.profile();
        }
        if (entry.isIn(BiomeTags.IS_SAVANNA)) {
            return Kind.SAVANNA.profile();
        }
        if (entry.isIn(BiomeTags.IS_MOUNTAIN)) {
            return t < 0.15f ? Kind.MOUNTAIN.profile().withHumidity(0.45f) : Kind.MOUNTAIN.profile();
        }
        if (t < 0.15f) {
            return Kind.SNOWY.profile();
        }
        if (entry.isIn(BiomeTags.IS_TAIGA)) {
            return Kind.TAIGA.profile();
        }
        if (entry.isIn(BiomeTags.IS_FOREST)) {
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

    private static void put(BiomeProfile profile, RegistryKey<?>... keys) {
        for (RegistryKey<?> key : keys) {
            @SuppressWarnings("unchecked")
            RegistryKey<Biome> biomeKey = (RegistryKey<Biome>) key;
            BY_KEY.put(biomeKey, profile);
        }
    }

    static {
        put(Kind.PLAINS.profile(), BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS);
        put(Kind.PLAINS.profile().withFoliage(0.6f).withWindiness(1.15f), BiomeKeys.MEADOW);
        put(Kind.FOREST.profile(), BiomeKeys.FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.BIRCH_FOREST,
                BiomeKeys.OLD_GROWTH_BIRCH_FOREST, BiomeKeys.CHERRY_GROVE);
        put(Kind.FOREST.profile().withHumidity(0.7f).withWindiness(0.55f), BiomeKeys.DARK_FOREST);
        put(Kind.FOREST.profile().withWindiness(1.15f), BiomeKeys.WINDSWEPT_FOREST);
        put(Kind.TAIGA.profile(), BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
        put(Kind.SNOWY.profile().withFoliage(0.6f).withWindiness(0.85f), BiomeKeys.SNOWY_TAIGA, BiomeKeys.GROVE);
        put(Kind.JUNGLE.profile(), BiomeKeys.JUNGLE, BiomeKeys.BAMBOO_JUNGLE);
        put(Kind.JUNGLE.profile().withFoliage(0.7f).withWindiness(0.75f), BiomeKeys.SPARSE_JUNGLE);
        put(Kind.SWAMP.profile(), BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP);
        put(Kind.DESERT.profile(), BiomeKeys.DESERT);
        put(Kind.BADLANDS.profile(), BiomeKeys.BADLANDS, BiomeKeys.ERODED_BADLANDS);
        put(Kind.BADLANDS.profile().withFoliage(0.25f).withDustiness(0.6f), BiomeKeys.WOODED_BADLANDS);
        put(Kind.SAVANNA.profile(), BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU);
        put(Kind.SAVANNA.profile().withWindiness(1.3f), BiomeKeys.WINDSWEPT_SAVANNA);
        put(Kind.SNOWY.profile(), BiomeKeys.SNOWY_PLAINS, BiomeKeys.ICE_SPIKES, BiomeKeys.FROZEN_RIVER,
                BiomeKeys.SNOWY_BEACH);
        put(Kind.MOUNTAIN.profile(), BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS,
                BiomeKeys.STONY_PEAKS, BiomeKeys.STONY_SHORE);
        put(Kind.MOUNTAIN.profile().withHumidity(0.45f), BiomeKeys.SNOWY_SLOPES, BiomeKeys.FROZEN_PEAKS,
                BiomeKeys.JAGGED_PEAKS);
        put(Kind.OCEAN.profile(), BiomeKeys.OCEAN, BiomeKeys.DEEP_OCEAN, BiomeKeys.WARM_OCEAN,
                BiomeKeys.LUKEWARM_OCEAN, BiomeKeys.DEEP_LUKEWARM_OCEAN, BiomeKeys.COLD_OCEAN,
                BiomeKeys.DEEP_COLD_OCEAN, BiomeKeys.FROZEN_OCEAN, BiomeKeys.DEEP_FROZEN_OCEAN);
        put(Kind.BEACH.profile(), BiomeKeys.BEACH);
        put(Kind.RIVER.profile(), BiomeKeys.RIVER);
        put(Kind.MUSHROOM.profile(), BiomeKeys.MUSHROOM_FIELDS);
        put(Kind.LUSH_CAVES.profile(), BiomeKeys.LUSH_CAVES);
        put(Kind.DRIPSTONE_CAVES.profile(), BiomeKeys.DRIPSTONE_CAVES);
        put(Kind.DEEP_DARK.profile(), BiomeKeys.DEEP_DARK);
        put(Kind.NETHER_WASTES.profile(), BiomeKeys.NETHER_WASTES);
        put(Kind.CRIMSON_FOREST.profile(), BiomeKeys.CRIMSON_FOREST);
        put(Kind.WARPED_FOREST.profile(), BiomeKeys.WARPED_FOREST);
        put(Kind.SOUL_SAND_VALLEY.profile(), BiomeKeys.SOUL_SAND_VALLEY);
        put(Kind.BASALT_DELTAS.profile(), BiomeKeys.BASALT_DELTAS);
        put(Kind.END.profile(), BiomeKeys.THE_END, BiomeKeys.END_HIGHLANDS, BiomeKeys.END_MIDLANDS,
                BiomeKeys.SMALL_END_ISLANDS, BiomeKeys.END_BARRENS);
        put(Kind.VOID.profile(), BiomeKeys.THE_VOID);
    }
}
