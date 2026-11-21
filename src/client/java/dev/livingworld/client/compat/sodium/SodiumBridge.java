package dev.livingworld.client.compat.sodium;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.client.render.VegetationWind;
import dev.livingworld.client.render.WaterShading;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

/**
 * The two places the mod reaches into Sodium (0.5.x) while it works: tagging vertices as Sodium meshes a
 * chunk, and feeding the patched terrain shader when a terrain pass begins. Only ever loaded from the
 * Sodium mixins, which are only applied when Sodium is installed.
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
     * Called for every quad of a block model just before Sodium writes it out: marks faces that look at
     * the sky, and gives the vertices of plants their bend weight.
     */
    public static void tagBlock(BlockRenderContext ctx, Vec3d offset, BakedQuadView quad, ChunkVertexEncoder.Vertex[] vertices) {
        if (!SodiumState.tagging()) {
            return;
        }
        int face = quad.getNormalFace() == ModelQuadFacing.POS_Y ? SodiumShaders.TAG_UP : 0;
        VegetationWind.Sway sway = VegetationWind.classify(ctx.state());
        if (sway == null) {
            if (face != 0) {
                for (ChunkVertexEncoder.Vertex vertex : vertices) {
                    vertex.light |= face << 8;
                }
            }
            return;
        }
        // Height of the vertex within its block, as the model has it.
        float bottom = ctx.origin().y() + (float) offset.getY();
        int kind = SodiumShaders.TAG_VEGETATION | (sway.kind() == 1 ? SodiumShaders.TAG_WHOLE : 0) | face;
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            float weight = sway.base() + (vertex.y - bottom) * sway.span();
            int steps = Math.round(Math.max(0f, Math.min(1f, weight)) * 15f);
            vertex.light |= (kind | steps) << 8;
        }
    }

    /** Called for every quad of water just before Sodium writes it out. */
    public static void tagWater(ChunkVertexEncoder.Vertex[] vertices) {
        if (!SodiumState.tagging()) {
            return;
        }
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            vertex.light |= SodiumShaders.TAG_WATER << 8;
        }
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
