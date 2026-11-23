package dev.livingworld.client.compat.sodium;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.client.render.VegetationWind;
import dev.livingworld.client.render.WaterShading;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

/**
 * What the mod does inside Sodium (0.5.x) while it works: tagging vertices as Sodium meshes a chunk, and
 * feeding the patched terrain shader when a terrain pass begins.
 *
 * <p>Nothing in this class mentions a class of Sodium's, so it is shared by every loader. The hooks that
 * call it are mixins into Sodium itself and live in the loader modules: Sodium on Fabric, and Embeddium,
 * the port of the same code that Forge players use, on Forge.
 *
 * <p>See {@link SodiumShaders} for what the tags mean and how the shader reads them.
 */
public final class SodiumBridge {
    /** Texture units for the water shader's own textures, clear of the two Sodium uses (0 and 1). */
    private static final int UNIT_SCENE = 5, UNIT_DEPTH = 6, UNIT_NOISE = 7, UNIT_COLUMNS = 8;

    private static final float[] MATRIX = new float[16];

    private SodiumBridge() {
    }

    /**
     * The tag for one vertex of a block model, to be or-ed into the vertex's light coordinate as Sodium is
     * about to write the quad out: it marks faces that look at the sky, and gives the vertices of plants
     * their bend weight. Zero while nothing is being tagged.
     *
     * @param sway   how the block takes the wind ({@link VegetationWind#classify}), or null for anything that is not a plant
     * @param up     whether the quad faces straight up
     * @param height height of the vertex within its block, as the model has it: 0 at the bottom, 1 at the top
     */
    public static int blockTag(VegetationWind.Sway sway, boolean up, float height) {
        if (!SodiumState.tagging()) {
            return 0;
        }
        int face = up ? SodiumShaders.TAG_UP : 0;
        if (sway == null) {
            return face << 8;
        }
        int kind = SodiumShaders.TAG_VEGETATION | (sway.kind() == 1 ? SodiumShaders.TAG_WHOLE : 0) | face;
        float weight = sway.base() + height * sway.span();
        int steps = Math.round(Math.max(0f, Math.min(1f, weight)) * 15f);
        return (kind | steps) << 8;
    }

    /** The tag for a vertex of water, to be or-ed into its light coordinate. Zero while nothing is being tagged. */
    public static int waterTag() {
        return SodiumState.tagging() ? SodiumShaders.TAG_WATER << 8 : 0;
    }

    /**
     * Called when Sodium has bound its terrain program for a pass: hands it this frame's values, the
     * same ones the mod's overrides of the vanilla terrain shaders get.
     */
    public static void feed(int program) {
        if (!SodiumState.ready()) {
            return;
        }
        set(program, "LWSway", VegetationWind.swayX, VegetationWind.swayZ, VegetationWind.swayStrength, VegetationWind.swaySeconds);
        set(program, "LWDrift", VegetationWind.gustX, VegetationWind.gustZ, 0f, 0f);
        set(program, "LWBend", VegetationWind.bendX, VegetationWind.bendY, VegetationWind.bendZ, VegetationWind.bendStrength);
        set(program, "LWSeason", VegetationWind.autumn, VegetationWind.winter, VegetationWind.spring, VegetationWind.lyingSnow);
        int camera = GL20C.glGetUniformLocation(program, "LWCamera");
        if (camera >= 0) {
            GL20C.glUniform3f(camera, VegetationWind.cameraX, VegetationWind.cameraY, VegetationWind.cameraZ);
        }

        WaterShading.Frame f = WaterShading.FRAME;
        boolean water = f.active && SodiumState.waterReady();
        // The alpha of LWSky is the shader's switch: 0 leaves water with Sodium's own look.
        set(program, "LWSky", f.skyR, f.skyG, f.skyB, water ? 1f : 0f);
        set(program, "LWWater", f.clock, f.driftX, f.driftZ, water ? f.height : 0f);
        if (!water) {
            return;
        }
        set(program, "LWWind", f.windX, f.windZ, f.windSpeed, f.seconds);
        set(program, "LWSun", f.sunX, f.sunY, f.sunZ, f.sunStrength);
        int sunColor = GL20C.glGetUniformLocation(program, "LWSunColor");
        if (sunColor >= 0) {
            GL20C.glUniform3f(sunColor, f.sunR, f.sunG, f.sunB);
        }
        set(program, "LWCloud", f.cloudCover, f.cloudHeight, f.cloudOriginX, f.cloudOriginZ);
        set(program, "LWCloudColor", f.cloudR, f.cloudG, f.cloudB, f.surf ? 1f : 0f);
        set(program, "LWOptions", f.reflectionSteps, f.caustics, f.rain, f.refraction);
        int screen = GL20C.glGetUniformLocation(program, "LWScreen");
        if (screen >= 0) {
            GL20C.glUniform2f(screen, f.pixelX, f.pixelY);
        }
        matrix(program, "LWViewProj", f.viewProj);
        matrix(program, "LWInvViewProj", f.invViewProj);
        matrix(program, "LWView", f.view);

        int active = GlStateManager._getActiveTexture();
        texture(program, "LWScene", UNIT_SCENE, f.sceneColor);
        texture(program, "LWDepth", UNIT_DEPTH, f.sceneDepth);
        texture(program, "LWNoise", UNIT_NOISE, f.noise);
        texture(program, "LWColumns", UNIT_COLUMNS, f.columns);
        RenderSystem.activeTexture(active);
    }

    private static void set(int program, String name, float a, float b, float c, float d) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20C.glUniform4f(location, a, b, c, d);
        }
    }

    private static void matrix(int program, String name, Matrix4f value) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20C.glUniformMatrix4fv(location, false, value.get(MATRIX));
        }
    }

    private static void texture(int program, String name, int unit, int id) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location < 0) {
            return;
        }
        RenderSystem.activeTexture(GL13C.GL_TEXTURE0 + unit);
        RenderSystem.bindTexture(id);
        GL20C.glUniform1i(location, unit);
    }
}
