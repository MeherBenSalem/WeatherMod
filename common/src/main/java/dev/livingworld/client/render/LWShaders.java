package dev.livingworld.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.livingworld.LivingWorld;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The mod's own shader programs. Each one is optional: if a program fails to load (driver quirk, a
 * resource pack replacing it with something broken) the field stays null and the feature that needs it
 * falls back or switches off, rather than failing the whole resource reload.
 */
public final class LWShaders {
    /** Normal-mapped, sun-lit ground decals. */
    public static ShaderInstance decal;
    /** Soft particles and precipitation (keeps faint alpha, fades into fog). */
    public static ShaderInstance fx;
    /** Full-screen atmosphere pass. */
    public static ShaderInstance post;
    /** Lens overlay: frost and water droplets. */
    public static ShaderInstance overlay;
    /** Bright-pass and blur for bloom. */
    public static ShaderInstance bloom;
    /** The cloud layer on its own, for the half-resolution cloud buffer. */
    public static ShaderInstance clouds;

    private LWShaders() {
    }

    /** Programs queued in the current resource reload whose load callbacks have not run yet. */
    private static int pending;

    /**
     * Says that a new resource reload has begun, for a loader whose registration event fires exactly once
     * per reload. Programs still waiting from a reload that failed part-way are forgotten, so they do
     * not hold this one back.
     */
    public static void newReload() {
        pending = 0;
    }

    /** How a mod loader's shader-reload event takes one more program, and hands it back once it is loaded. */
    @FunctionalInterface
    public interface Registrar {
        void register(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> onLoaded) throws IOException;
    }

    /**
     * Queues the mod's programs for loading. Called by the loader module from its shader registration
     * event, on every resource reload.
     */
    public static void registerAll(Registrar registrar) {
        // The event can fire many times within one reload (once per vanilla program on some Fabric
        // API versions). Register once, and only again after the queued programs have been handed over.
        if (pending > 0) {
            return;
        }
        decal = null;
        fx = null;
        post = null;
        overlay = null;
        bloom = null;
        clouds = null;
        register(registrar, "bloom", DefaultVertexFormat.POSITION_TEX, p -> bloom = p);
        register(registrar, "clouds", DefaultVertexFormat.POSITION_TEX, p -> clouds = p);
        register(registrar, "decal", DefaultVertexFormat.BLOCK, p -> decal = p);
        register(registrar, "fx", DefaultVertexFormat.PARTICLE, p -> fx = p);
        register(registrar, "post", DefaultVertexFormat.POSITION_TEX, p -> post = p);
        register(registrar, "overlay", DefaultVertexFormat.POSITION_TEX, p -> overlay = p);
    }

    private static void register(Registrar registrar, String name, VertexFormat format, Consumer<ShaderInstance> setter) {
        try {
            registrar.register(LivingWorld.id(name), format, program -> {
                pending = Math.max(0, pending - 1);
                setter.accept(program);
            });
            pending++;
        } catch (Exception e) {
            LivingWorld.LOGGER.error("Shader '{}' failed to load; the effect that uses it is disabled", name, e);
        }
    }

    public static String status() {
        return "decal=" + (decal != null) + " fx=" + (fx != null) + " post=" + (post != null) + " overlay=" + (overlay != null)
                + " bloom=" + (bloom != null) + " clouds=" + (clouds != null);
    }

    public static void set(ShaderInstance program, String name, float a) {
        Uniform u = program.getUniform(name);
        if (u != null) {
            u.set(a);
        }
    }

    public static void set(ShaderInstance program, String name, float a, float b) {
        Uniform u = program.getUniform(name);
        if (u != null) {
            u.set(a, b);
        }
    }

    public static void set(ShaderInstance program, String name, float a, float b, float c) {
        Uniform u = program.getUniform(name);
        if (u != null) {
            u.set(a, b, c);
        }
    }

    public static void set(ShaderInstance program, String name, float a, float b, float c, float d) {
        Uniform u = program.getUniform(name);
        if (u != null) {
            u.setMat2x2(a, b, c, d);
        }
    }
}
