package dev.livingworld.client.render;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.compat.sodium.SodiumState;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.env.Atmosphere;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.GrowingPlantBodyBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

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
 * <p>This rides on the vanilla terrain shaders, so it is active with the vanilla renderer (Indigo on
 * Fabric, Forge's own on Forge) and
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
        if (block instanceof VineBlock || block instanceof GrowingPlantHeadBlock || block instanceof GrowingPlantBodyBlock) {
            return HANGING;
        }
        if (block instanceof SugarCaneBlock || block instanceof BambooStalkBlock) {
            return CANE;
        }
        if (block instanceof DoublePlantBlock) {
            return state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? TALL_UPPER : TALL_LOWER;
        }
        if (block instanceof BushBlock && !(block instanceof WaterlilyBlock) && !(block instanceof AzaleaBlock)) {
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
        ShaderInstance program = GameRenderer.getRendertypeCutoutShader();
        return program != null && program.getUniform("LWSway") != null;
    }

    public static String status() {
        return "wrapped models=" + wrappedModels + " shader bound=" + shaderBound() + " strength=" + lastStrength
                + " available=" + Compat.vegetationSwayAvailable();
    }

    /**
     * True when the models of plants have to be wrapped so that their vertices carry the tag. Not under
     * Sodium (or a port of it): its mesher does not go through the model hooks and is tagged directly.
     */
    public static boolean wrapsModels() {
        return !Compat.SODIUM;
    }

    /** Called once at start-up, by the loader module, to say which of the two ways of tagging is in use. */
    public static void logMode() {
        if (Compat.SODIUM) {
            LivingWorld.LOGGER.info("Sodium detected: vegetation is tagged in Sodium's mesher and its terrain shader is patched");
        }
    }

    /**
     * How the model baked under {@code modelId} takes the wind, or null if it is not the model of a
     * plant in the world (anything else, and the plant's own inventory model).
     *
     * <p>Wrapping the model is the loader module's job, because the two loaders hand out baked models and
     * let them be changed in different ways. What to wrap and what to write is decided here, so both do
     * exactly the same thing.
     */
    public static Sway swayOf(ModelResourceLocation id) {
        if (id == null || "inventory".equals(id.variant())) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(id.id());
        BlockState state = block.defaultBlockState();
        if (block instanceof DoublePlantBlock && state.hasProperty(DoublePlantBlock.HALF)) {
            state = state.setValue(DoublePlantBlock.HALF, id.variant().contains("half=upper") ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        }
        return classify(state);
    }

    /**
     * The vertex alpha (0-255) that tags a vertex of a plant's model: the kind of sway and the bend
     * weight at this height, packed just under full opacity where the terrain shader looks for it.
     *
     * @param y height of the vertex within its block, 0 at the bottom and 1 at the top
     */
    public static int tagAlpha(Sway sway, float y) {
        float weight = LWMath.clamp01(sway.base() + y * sway.span());
        return 254 - (sway.kind() * 16 + Math.round(weight * 15f));
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
        Vec3 cam = camera.getPosition();
        cameraX = (float) (cam.x - Math.floor(cam.x / 4096.0) * 4096.0);
        cameraY = (float) cam.y;
        cameraZ = (float) (cam.z - Math.floor(cam.z / 4096.0) * 4096.0);

        bendX = bendY = bendZ = bendStrength = 0f;
        Entity player = Minecraft.getInstance().player;
        if (active && config.vegetationBending && player != null && !player.isSpectator()) {
            bendX = (float) (Mth.lerp(tickDelta, player.xo, player.getX()) - cam.x);
            bendY = (float) (Mth.lerp(tickDelta, player.yo, player.getY()) - cam.y);
            bendZ = (float) (Mth.lerp(tickDelta, player.zo, player.getZ()) - cam.z);
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
        for (ShaderInstance program : new ShaderInstance[]{GameRenderer.getRendertypeCutoutShader(),
                GameRenderer.getRendertypeCutoutMippedShader()}) {
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
