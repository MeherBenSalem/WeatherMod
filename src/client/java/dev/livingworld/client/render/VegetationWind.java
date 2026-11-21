package dev.livingworld.client.render;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.compat.sodium.SodiumState;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.AbstractPlantBlock;
import net.minecraft.block.AbstractPlantStemBlock;
import net.minecraft.block.AzaleaBlock;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

/**
 * Wind-driven vegetation without touching a single block.
 *
 * <p>Two halves: at model-bake time the models of plants, leaves and vines are wrapped so that their
 * vertices carry a small tag in the (otherwise unused) vertex alpha, saying "this is vegetation, and
 * this far from its root". At render time the mod's override of the vanilla cut-out terrain vertex
 * shader reads the tag and displaces the vertex by the shared wind field — entirely on the GPU, with no
 * chunk rebuilds and no per-block updates.
 *
 * <p>The same shader also recolours biome-tinted foliage with the season (see {@code seasonal()} in
 * {@code rendertype_cutout.vsh}): the tint is changed as it is drawn, so the year turns smoothly with no
 * chunk rebuilds.
 *
 * <p>This rides on the vanilla terrain shaders, so it is active with the vanilla/Indigo renderer and
 * inactive under an Iris shader pack (which animates foliage itself).
 *
 * <p>Sodium meshes and draws terrain itself, so there the same thing is done by other means: the tag is
 * written by a hook in Sodium's mesher into a spare byte of the light coordinate, and Sodium's terrain
 * shader is patched to read it (see {@code client/compat/sodium}). The values the shader needs are worked
 * out once per frame here, into the static fields below, and handed to whichever shader is in use.
 */
public final class VegetationWind {
    private static final int KIND_ROOTED = 0;
    private static final int KIND_UNIFORM = 1;
    /** Share of the wind's travel that gusts cross vegetation at: most of it, a gust is the wind arriving. */
    private static final double GUST_DRIFT = 0.7;

    /**
     * How the vertices of a kind of plant take the wind.
     *
     * @param kind 0 = rooted at its base and bending toward the tip, 1 = moving as a whole
     * @param base bend weight at the bottom of the block
     * @param span how much the weight grows from the bottom of the block to its top
     */
    public record Sway(int kind, float base, float span) {
    }

    private static final Sway LEAVES = new Sway(KIND_UNIFORM, 1f, 0f);
    private static final Sway HANGING = new Sway(KIND_UNIFORM, 0.8f, 0f);
    private static final Sway CANE = new Sway(KIND_UNIFORM, 0.5f, 0f);
    // Two-block plants: the lower half bends over the first half of the range, the upper over the second.
    private static final Sway TALL_LOWER = new Sway(KIND_ROOTED, 0f, 0.5f);
    private static final Sway TALL_UPPER = new Sway(KIND_ROOTED, 0.5f, 0.5f);
    private static final Sway ROOTED = new Sway(KIND_ROOTED, 0f, 1f);

    /** How a block's vertices take the wind, or null for anything that is not vegetation. */
    public static Sway classify(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LeavesBlock) {
            return LEAVES;
        }
        if (block instanceof VineBlock || block instanceof AbstractPlantStemBlock || block instanceof AbstractPlantBlock) {
            return HANGING;
        }
        if (block instanceof SugarCaneBlock || block instanceof BambooBlock) {
            return CANE;
        }
        if (block instanceof TallPlantBlock) {
            return state.contains(TallPlantBlock.HALF) && state.get(TallPlantBlock.HALF) == DoubleBlockHalf.UPPER ? TALL_UPPER : TALL_LOWER;
        }
        if (block instanceof PlantBlock && !(block instanceof LilyPadBlock) && !(block instanceof AzaleaBlock)) {
            return ROOTED;
        }
        return null;
    }

    /** Models wrapped during the last bake (diagnostics). */
    public static int wrappedModels;
    private static float lastStrength;

    // ---- this frame's values for the terrain vertex shader, whichever renderer draws the terrain ----
    /** Wind direction and how strongly vegetation sways (0 = off), and the time in seconds. */
    public static float swayX = 1f, swayZ, swayStrength, swaySeconds;
    /** Camera position, x and z wrapped to 4096 blocks. */
    public static float cameraX, cameraY, cameraZ;
    /** The player's feet relative to the camera, and how strongly plants bend aside (0 = off). */
    public static float bendX, bendY, bendZ, bendStrength;
    /** How far the wind has carried its gusts. */
    public static float gustX, gustZ;
    /** The season's hold on foliage: autumn colour, winter bareness, spring freshness, snow lying (all 0 = off). */
    public static float autumn, winter, spring, lyingSnow;

    private VegetationWind() {
    }

    /** True if a terrain shader that knows the wind uniforms is in use: the mod's override, or Sodium's patched one. */
    public static boolean shaderBound() {
        if (Compat.SODIUM) {
            return SodiumState.ready();
        }
        ShaderProgram program = GameRenderer.getRenderTypeCutoutProgram();
        return program != null && program.getUniform("LWSway") != null;
    }

    public static String status() {
        return "wrapped models=" + wrappedModels + " shader bound=" + shaderBound() + " strength=" + lastStrength
                + " available=" + Compat.vegetationSwayAvailable();
    }

    public static void init() {
        if (Compat.SODIUM) {
            // Sodium does not go through the model hooks used below; its mesher is tagged directly.
            LivingWorld.LOGGER.info("Sodium detected: vegetation is tagged in Sodium's mesher and its terrain shader is patched");
            return;
        }
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            if (model == null || !(context.id() instanceof ModelIdentifier id) || "inventory".equals(id.getVariant())) {
                return model;
            }
            Block block = Registries.BLOCK.get(new Identifier(id.getNamespace(), id.getPath()));
            BlockState state = block.getDefaultState();
            if (block instanceof TallPlantBlock && state.contains(TallPlantBlock.HALF)) {
                state = state.with(TallPlantBlock.HALF, id.getVariant().contains("half=upper") ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            }
            Sway sway = classify(state);
            return sway == null ? model : new SwayModel(model, sway.kind(), sway.base(), sway.span());
        }));
    }

    /** Tags every vertex of the wrapped model with its vegetation kind and bend weight. */
    private static final class SwayModel extends ForwardingBakedModel {
        private final RenderContext.QuadTransform transform;

        SwayModel(BakedModel wrapped, int kind, float base, float span) {
            this.wrapped = wrapped;
            wrappedModels++;
            this.transform = quad -> {
                for (int v = 0; v < 4; v++) {
                    float weight = LWMath.clamp01(base + quad.y(v) * span);
                    int alpha = 254 - (kind * 16 + Math.round(weight * 15f));
                    quad.color(v, (quad.color(v) & 0x00FFFFFF) | (alpha << 24));
                }
                return true;
            };
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier,
                                   RenderContext context) {
            context.pushTransform(transform);
            super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
            context.popTransform();
        }
    }

    /**
     * Works out this frame's wind, player position and season for the terrain vertex shader, and hands
     * them to the mod's overrides of the vanilla cut-out shaders. Called before terrain is drawn. Under
     * Sodium the bridge hands the same values to Sodium's patched shader when a terrain pass begins.
     */
    public static void upload(Camera camera, float tickDelta) {
        ClientConfig config = ClientConfig.get();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        boolean available = Compat.vegetationSwayAvailable();
        boolean active = config.enabled && config.vegetationSway && env.inWorld && available;
        swayStrength = active ? Math.min(1.6f, (0.12f + env.wind.speed * (0.35f + 0.65f * env.windExposure)) * config.swayStrength) : 0f;
        lastStrength = swayStrength;
        swayX = env.wind.dirX;
        swayZ = env.wind.dirZ;
        swaySeconds = (float) (((env.ticks % 72000L) + tickDelta) / 20.0);
        Vec3d cam = camera.getPos();
        cameraX = (float) (cam.x - Math.floor(cam.x / 4096.0) * 4096.0);
        cameraY = (float) cam.y;
        cameraZ = (float) (cam.z - Math.floor(cam.z / 4096.0) * 4096.0);

        bendX = bendY = bendZ = bendStrength = 0f;
        Entity player = MinecraftClient.getInstance().player;
        if (active && config.vegetationBending && player != null && !player.isSpectator()) {
            bendX = (float) (MathHelper.lerp(tickDelta, player.prevX, player.getX()) - cam.x);
            bendY = (float) (MathHelper.lerp(tickDelta, player.prevY, player.getY()) - cam.y);
            bendZ = (float) (MathHelper.lerp(tickDelta, player.prevZ, player.getZ()) - cam.z);
            bendStrength = 1f;
        }
        gustX = env.windTravelX(tickDelta, GUST_DRIFT, 4096.0);
        gustZ = env.windTravelZ(tickDelta, GUST_DRIFT, 4096.0);
        boolean seasonal = config.enabled && config.seasons && config.seasonalFoliage && env.inWorld && env.seasonsActive && available;
        autumn = seasonal ? env.autumn : 0f;
        winter = seasonal ? env.winter : 0f;
        spring = seasonal ? env.spring : 0f;
        lyingSnow = seasonal && config.snowCover ? Atmosphere.INSTANCE.lyingSnow : 0f;

        if (Compat.SODIUM) {
            return;
        }
        for (ShaderProgram program : new ShaderProgram[]{GameRenderer.getRenderTypeCutoutProgram(),
                GameRenderer.getRenderTypeCutoutMippedProgram()}) {
            // No uniform when a resource pack replaced the shader with one that does not know it.
            if (program == null || program.getUniform("LWSway") == null) {
                continue;
            }
            LWShaders.set(program, "LWSway", swayX, swayZ, swayStrength, swaySeconds);
            LWShaders.set(program, "LWCamera", cameraX, cameraY, cameraZ);
            LWShaders.set(program, "LWBend", bendX, bendY, bendZ, bendStrength);
            LWShaders.set(program, "LWDrift", gustX, gustZ, 0f, 0f);
            LWShaders.set(program, "LWSeason", autumn, winter, spring, lyingSnow);
        }
    }
}
