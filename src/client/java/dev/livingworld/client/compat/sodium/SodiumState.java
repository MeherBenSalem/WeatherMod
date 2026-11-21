package dev.livingworld.client.compat.sodium;

import dev.livingworld.client.compat.Compat;

/**
 * Whether the mod has managed to teach Sodium's terrain shader its tricks. This class mentions nothing
 * of Sodium's, so the rest of the mod can ask it whether or not Sodium is installed.
 *
 * <p>Sodium draws terrain with its own shader, loaded from its own jar, so the mod's overrides of the
 * vanilla terrain shaders do nothing there. Instead the source of Sodium's shader is patched as it is
 * loaded (see {@link SodiumShaders}). The patch looks for specific lines; if a different version of
 * Sodium no longer has them, nothing is patched and water, sway and seasonal colours stay off under
 * Sodium, exactly as before this bridge existed.
 */
public final class SodiumState {
    /** The vertex stage was patched: tags are understood, vegetation sways, foliage follows the season. */
    static volatile boolean vertexPatched;
    /** The fragment stage was patched as well: water is shaded as water. */
    static volatile boolean fragmentPatched;
    /** A line the patch needs was not found: Sodium is left exactly as it is. */
    static volatile boolean gaveUp;

    private SodiumState() {
    }

    /** True when Sodium's terrain shader understands the mod's vertex tags. */
    public static boolean ready() {
        return Compat.SODIUM && vertexPatched && !gaveUp;
    }

    /** True when Sodium's terrain shader also shades water. */
    public static boolean waterReady() {
        return ready() && fragmentPatched;
    }

    /**
     * Whether chunk vertices should carry the mod's tags right now. Never while a shader pack is drawing:
     * the tags ride in a spare byte of the light coordinate, which only the patched shader masks off.
     */
    public static boolean tagging() {
        return ready() && !Compat.shaderPackActive();
    }

    public static String describe() {
        return "sodium bridge: vertex=" + vertexPatched + " fragment=" + fragmentPatched + (gaveUp ? " GAVE UP" : "");
    }
}
