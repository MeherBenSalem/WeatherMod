package dev.livingworld.surface;

/**
 * How a ground surface responds to feet, wind and rain. Drives footprints, kicked-up particles,
 * footstep audio layers and rain splashes.
 */
public enum SurfaceMaterial {
    //            prints  depth  life(s)  wind   rain   snow   dust   tint
    NONE(         false,  0f,    0f,      0f,    0f,    0f,    0f,    0xFFFFFF),
    SAND(         true,   1.0f,  150f,    1.0f,  0.55f, 0.7f,  1.0f,  0xD9C28E),
    RED_SAND(     true,   1.0f,  150f,    1.0f,  0.55f, 0.7f,  1.0f,  0xB8612A),
    SNOW(         true,   1.2f,  260f,    0.35f, 0.9f,  1.0f,  0f,    0xE8F1FA),
    MUD(          true,   0.9f,  220f,    0.02f, 0.5f,  0.6f,  0f,    0x3B2B22),
    DIRT(         true,   0.45f, 70f,     0.5f,  0.9f,  0.8f,  0.7f,  0x7A5639),
    GRASS(        true,   0.3f,  28f,     0.25f, 0.4f,  0.9f,  0.1f,  0x55702F),
    GRAVEL(       true,   0.4f,  60f,     0.15f, 0.5f,  0.8f,  0.5f,  0x8A8580),
    CLAY(         true,   0.6f,  180f,    0.02f, 0.5f,  0.7f,  0f,    0x8E95A3),
    SOUL(         true,   0.9f,  160f,    0.3f,  0f,    0f,    0.6f,  0x4A382C),
    ASH(          true,   0.5f,  90f,     0.6f,  0f,    0f,    0.9f,  0x5A3030),
    MOSS(         true,   0.35f, 35f,     0.1f,  0.3f,  0.8f,  0f,    0x5C7A2E),
    STONE(        false,  0f,    0f,      0f,    0f,    0f,    0.15f, 0x808080),
    WOOD(         false,  0f,    0f,      0f,    0f,    0f,    0.05f, 0x8C6B42),
    LEAVES(       false,  0f,    0f,      0f,    0f,    0f,    0f,    0x4E7A2A),
    ICE(          false,  0f,    0f,      0f,    0f,    0f,    0f,    0xA9C8F0),
    WATER(        false,  0f,    0f,      0f,    0f,    0f,    0f,    0x3F76E4);

    /** Whether feet leave an impression in the dry material. Hard surfaces only show wet prints. */
    public final boolean holdsPrints;
    /** Relative depth of an impression (scales decal strength). */
    public final float printDepth;
    /** Seconds an undisturbed print stays visible in calm, dry weather. */
    public final float printLifetime;
    /** How quickly wind, rain and snowfall erase a print (0 = immune, 1 = fast). */
    public final float windErosion;
    public final float rainErosion;
    public final float snowErosion;
    /** How much dry dust a footstep or gust lifts, 0..1. */
    public final float dust;
    /** Representative colour, used to tint particles and decals (0xRRGGBB). */
    public final int tint;

    SurfaceMaterial(boolean holdsPrints, float printDepth, float printLifetime, float windErosion, float rainErosion,
                    float snowErosion, float dust, int tint) {
        this.holdsPrints = holdsPrints;
        this.printDepth = printDepth;
        this.printLifetime = printLifetime;
        this.windErosion = windErosion;
        this.rainErosion = rainErosion;
        this.snowErosion = snowErosion;
        this.dust = dust;
        this.tint = tint;
    }

    public boolean isSandy() {
        return this == SAND || this == RED_SAND;
    }

    /** Loose ground that turns to mud when soaked. */
    public boolean turnsToMud() {
        return this == DIRT || this == GRASS;
    }

    /** Hard ground that shows wet boot prints. */
    public boolean isHard() {
        return this == STONE || this == WOOD || this == ICE;
    }

    public float red() {
        return ((tint >> 16) & 255) / 255f;
    }

    public float green() {
        return ((tint >> 8) & 255) / 255f;
    }

    public float blue() {
        return (tint & 255) / 255f;
    }
}
