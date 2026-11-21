package dev.livingworld.client.compat;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.config.ClientConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Detects other rendering mods and decides which render paths are safe. Everything here degrades
 * gracefully: an unknown situation selects the conservative path instead of risking broken frames.
 */
public final class Compat {
    public static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
    public static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

    private static Method irisIsShaderPackInUse;
    private static Object irisApi;
    private static boolean irisLookupFailed;
    private static boolean shaderPackCached;
    private static long shaderPackCheckedAt;

    private Compat() {
    }

    /** True while an Iris shader pack is actually rendering the world. Cheap: re-queried twice a second. */
    public static boolean shaderPackActive() {
        if (!IRIS || irisLookupFailed) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - shaderPackCheckedAt < 500L) {
            return shaderPackCached;
        }
        shaderPackCheckedAt = now;
        try {
            if (irisApi == null) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisIsShaderPackInUse = api.getMethod("isShaderPackInUse");
            }
            shaderPackCached = (Boolean) irisIsShaderPackInUse.invoke(irisApi);
        } catch (Throwable t) {
            irisLookupFailed = true;
            shaderPackCached = false;
            LivingWorld.LOGGER.warn("Iris is installed but its API could not be queried; assuming no shader pack: {}", t.toString());
        }
        return shaderPackCached;
    }

    /**
     * Whether the mod's own shader programs (lit footprints, full-screen atmosphere pass, lens overlay)
     * may be used. Shader packs own the whole pipeline, so with one active we fall back to plain
     * vanilla-program rendering and let the pack do its own atmosphere.
     */
    public static boolean customShadersAllowed() {
        ClientConfig c = ClientConfig.get();
        if (c.simpleRendering) {
            return false;
        }
        return !(c.yieldToShaderPacks && shaderPackActive());
    }

    /** Whether the full-screen atmosphere pass should run. */
    public static boolean postProcessingAllowed() {
        return ClientConfig.get().postProcessing && customShadersAllowed();
    }

    /**
     * Vegetation sway and seasonal foliage are done in the terrain vertex shader: the mod's override of
     * the vanilla one, or Sodium's once the bridge has patched it. A shader pack replaces both.
     */
    public static boolean vegetationSwayAvailable() {
        return (!SODIUM || dev.livingworld.client.compat.sodium.SodiumState.ready()) && !shaderPackActive();
    }

    public static String describe() {
        return "sodium=" + SODIUM + " iris=" + IRIS + " shaderPack=" + shaderPackActive()
                + " customShaders=" + customShadersAllowed()
                + (SODIUM ? " " + dev.livingworld.client.compat.sodium.SodiumState.describe() : "");
    }
}
