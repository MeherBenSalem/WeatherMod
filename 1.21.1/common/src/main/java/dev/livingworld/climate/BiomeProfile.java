package dev.livingworld.climate;

/**
 * The atmospheric identity of a biome. Other mods can register profiles for their own biomes through
 * {@link dev.livingworld.api.LivingWorldApi}.
 *
 * @param kind      which family of ambience (particles, sounds, fog, colour grade) the biome belongs to
 * @param humidity  0 bone dry .. 1 saturated
 * @param windiness multiplier on sustained wind (≈0.5 dense jungle .. 1.35 exposed peaks)
 * @param dustiness how much loose dust/sand the wind can lift, 0..1
 * @param foliage   amount of leaves and grass (rustling, falling leaves, pollen), 0..1
 */
public record BiomeProfile(Kind kind, float humidity, float windiness, float dustiness, float foliage) {

    public enum Kind {
        PLAINS(0.45f, 1.05f, 0.15f, 0.45f),
        FOREST(0.6f, 0.7f, 0.03f, 1f),
        TAIGA(0.55f, 0.8f, 0.02f, 0.85f),
        JUNGLE(0.95f, 0.55f, 0f, 1f),
        SWAMP(0.9f, 0.5f, 0f, 0.7f),
        DESERT(0.04f, 1.2f, 1f, 0.02f),
        BADLANDS(0.08f, 1.15f, 0.85f, 0.03f),
        SAVANNA(0.22f, 1.1f, 0.5f, 0.3f),
        SNOWY(0.4f, 1.1f, 0f, 0.15f),
        MOUNTAIN(0.35f, 1.35f, 0.1f, 0.1f),
        OCEAN(0.8f, 1.3f, 0f, 0f),
        BEACH(0.6f, 1.2f, 0.25f, 0.05f),
        RIVER(0.7f, 0.9f, 0f, 0.3f),
        MUSHROOM(0.8f, 0.9f, 0f, 0.1f),
        LUSH_CAVES(0.95f, 0.1f, 0f, 0.5f),
        DRIPSTONE_CAVES(0.75f, 0.1f, 0.05f, 0f),
        DEEP_DARK(0.5f, 0.05f, 0.05f, 0f),
        NETHER_WASTES(0f, 0.5f, 0.5f, 0f),
        CRIMSON_FOREST(0.2f, 0.4f, 0.1f, 0.3f),
        WARPED_FOREST(0.3f, 0.4f, 0.1f, 0.3f),
        SOUL_SAND_VALLEY(0.05f, 0.7f, 0.6f, 0f),
        BASALT_DELTAS(0f, 0.6f, 0.8f, 0f),
        END(0f, 0.35f, 0.05f, 0f),
        VOID(0.3f, 0.5f, 0f, 0f);

        public static final Kind[] VALUES = values();

        private final BiomeProfile defaults;

        Kind(float humidity, float windiness, float dustiness, float foliage) {
            this.defaults = new BiomeProfile(this, humidity, windiness, dustiness, foliage);
        }

        /** The stock profile of this family. */
        public BiomeProfile profile() {
            return defaults;
        }

        public boolean isNether() {
            return this == NETHER_WASTES || this == CRIMSON_FOREST || this == WARPED_FOREST
                    || this == SOUL_SAND_VALLEY || this == BASALT_DELTAS;
        }

        public boolean isCave() {
            return this == LUSH_CAVES || this == DRIPSTONE_CAVES || this == DEEP_DARK;
        }
    }

    public BiomeProfile withHumidity(float value) {
        return new BiomeProfile(kind, value, windiness, dustiness, foliage);
    }

    public BiomeProfile withWindiness(float value) {
        return new BiomeProfile(kind, humidity, value, dustiness, foliage);
    }

    public BiomeProfile withDustiness(float value) {
        return new BiomeProfile(kind, humidity, windiness, value, foliage);
    }

    public BiomeProfile withFoliage(float value) {
        return new BiomeProfile(kind, humidity, windiness, dustiness, value);
    }
}
