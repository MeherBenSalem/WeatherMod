package dev.livingworld.client.render;

import dev.livingworld.LivingWorld;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.function.Consumer;

/**
 * The mod's own shader programs. Each one is optional: if a program fails to load (driver quirk, a
 * resource pack replacing it with something broken) the field stays null and the feature that needs it
 * falls back or switches off, rather than failing the whole resource reload.
 */
public final class LWShaders {
    /** Normal-mapped, sun-lit ground decals. */
    public static ShaderProgram decal;
    /** Soft particles and precipitation (keeps faint alpha, fades into fog). */
    public static ShaderProgram fx;
    /** Full-screen atmosphere pass. */
    public static ShaderProgram post;
    /** Lens overlay: frost and water droplets. */
    public static ShaderProgram overlay;
    /** Bright-pass and blur for bloom. */
    public static ShaderProgram bloom;

    private LWShaders() {
    }

    /** Programs queued in the current resource reload whose load callbacks have not run yet. */
    private static int pending;

    public static void init() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
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
            register(context, "bloom", VertexFormats.POSITION_TEXTURE, p -> bloom = p);
            register(context, "decal", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, p -> decal = p);
            register(context, "fx", VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, p -> fx = p);
            register(context, "post", VertexFormats.POSITION_TEXTURE, p -> post = p);
            register(context, "overlay", VertexFormats.POSITION_TEXTURE, p -> overlay = p);
        });
    }

    private static void register(CoreShaderRegistrationCallback.RegistrationContext context, String name,
                                 VertexFormat format, Consumer<ShaderProgram> setter) {
        try {
            context.register(LivingWorld.id(name), format, program -> {
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
                + " bloom=" + (bloom != null);
    }

    public static void set(ShaderProgram program, String name, float a) {
        GlUniform u = program.getUniform(name);
        if (u != null) {
            u.set(a);
        }
    }

    public static void set(ShaderProgram program, String name, float a, float b) {
        GlUniform u = program.getUniform(name);
        if (u != null) {
            u.set(a, b);
        }
    }

    public static void set(ShaderProgram program, String name, float a, float b, float c) {
        GlUniform u = program.getUniform(name);
        if (u != null) {
            u.set(a, b, c);
        }
    }

    public static void set(ShaderProgram program, String name, float a, float b, float c, float d) {
        GlUniform u = program.getUniform(name);
        if (u != null) {
            u.set(a, b, c, d);
        }
    }
}
