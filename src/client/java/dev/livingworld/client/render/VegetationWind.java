package dev.livingworld.client.render;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.config.ClientConfig;
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
 * <p>This rides on the vanilla terrain shaders, so it is active with the vanilla/Indigo renderer and
 * automatically inactive under Sodium (its own terrain shaders) or an Iris shader pack (which animates
 * foliage itself). Models are not wrapped at all when Sodium is installed.
 */
public final class VegetationWind {
    private static final int KIND_ROOTED = 0;
    private static final int KIND_UNIFORM = 1;

    /** Models wrapped during the last bake (diagnostics). */
    public static int wrappedModels;
    private static float lastStrength;

    private VegetationWind() {
    }

    /** True if the active cut-out terrain program is the mod's override (not replaced by a pack or another mod). */
    public static boolean shaderBound() {
        ShaderProgram program = GameRenderer.getRenderTypeCutoutProgram();
        return program != null && program.getUniform("LWWind") != null;
    }

    public static String status() {
        return "wrapped models=" + wrappedModels + " shader bound=" + shaderBound() + " strength=" + lastStrength
                + " available=" + Compat.vegetationSwayAvailable();
    }

    public static void init() {
        if (Compat.SODIUM) {
            LivingWorld.LOGGER.info("Sodium detected: vegetation sway is disabled (it relies on the vanilla terrain shaders)");
            return;
        }
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            if (model == null || !(context.id() instanceof ModelIdentifier id) || "inventory".equals(id.getVariant())) {
                return model;
            }
            Block block = Registries.BLOCK.get(new Identifier(id.getNamespace(), id.getPath()));
            String variant = id.getVariant();
            if (block instanceof LeavesBlock) {
                return new SwayModel(model, KIND_UNIFORM, 1f, 0f);
            }
            if (block instanceof VineBlock || block instanceof AbstractPlantStemBlock || block instanceof AbstractPlantBlock) {
                return new SwayModel(model, KIND_UNIFORM, 0.8f, 0f);
            }
            if (block instanceof SugarCaneBlock || block instanceof BambooBlock) {
                return new SwayModel(model, KIND_UNIFORM, 0.5f, 0f);
            }
            if (block instanceof TallPlantBlock) {
                // Two-block plants: the lower half bends over the first half of the range, the upper over the second.
                return variant.contains("half=upper") ? new SwayModel(model, KIND_ROOTED, 0.5f, 0.5f)
                        : new SwayModel(model, KIND_ROOTED, 0f, 0.5f);
            }
            if (block instanceof PlantBlock && !(block instanceof LilyPadBlock) && !(block instanceof AzaleaBlock)) {
                return new SwayModel(model, KIND_ROOTED, 0f, 1f);
            }
            return model;
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

    /** Feeds this frame's wind and player position to the terrain shaders. Called before terrain is drawn. */
    public static void upload(Camera camera, float tickDelta) {
        ClientConfig config = ClientConfig.get();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        boolean active = config.enabled && config.vegetationSway && env.inWorld && Compat.vegetationSwayAvailable();
        float strength = active ? Math.min(1.6f, (0.12f + env.wind.speed * (0.35f + 0.65f * env.windExposure)) * config.swayStrength) : 0f;
        lastStrength = strength;
        float seconds = (float) (((env.ticks % 72000L) + tickDelta) / 20.0);
        Vec3d cam = camera.getPos();
        float camX = (float) (cam.x - Math.floor(cam.x / 4096.0) * 4096.0);
        float camZ = (float) (cam.z - Math.floor(cam.z / 4096.0) * 4096.0);

        float bx = 0f, by = 0f, bz = 0f, bend = 0f;
        Entity player = MinecraftClient.getInstance().player;
        if (active && config.vegetationBending && player != null && !player.isSpectator()) {
            bx = (float) (MathHelper.lerp(tickDelta, player.prevX, player.getX()) - cam.x);
            by = (float) (MathHelper.lerp(tickDelta, player.prevY, player.getY()) - cam.y);
            bz = (float) (MathHelper.lerp(tickDelta, player.prevZ, player.getZ()) - cam.z);
            bend = 1f;
        }
        apply(GameRenderer.getRenderTypeCutoutProgram(), env, strength, seconds, camX, (float) cam.y, camZ, bx, by, bz, bend);
        apply(GameRenderer.getRenderTypeCutoutMippedProgram(), env, strength, seconds, camX, (float) cam.y, camZ, bx, by, bz, bend);
    }

    private static void apply(ShaderProgram program, ClientEnvironment env, float strength, float seconds, float camX, float camY,
                              float camZ, float bx, float by, float bz, float bend) {
        if (program == null) {
            return;
        }
        // Null when a resource pack replaced the shader with one that does not know these uniforms.
        GlUniform wind = program.getUniform("LWWind");
        if (wind == null) {
            return;
        }
        wind.set(env.wind.dirX, env.wind.dirZ, strength, seconds);
        GlUniform cameraPos = program.getUniform("LWCamera");
        if (cameraPos != null) {
            cameraPos.set(camX, camY, camZ);
        }
        GlUniform bendUniform = program.getUniform("LWBend");
        if (bendUniform != null) {
            bendUniform.set(bx, by, bz, bend);
        }
    }
}
