package dev.livingworld.fabric.client;

import dev.livingworld.client.render.VegetationWind;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * The Fabric half of {@link VegetationWind}: wraps the baked models of plants, leaves and vines so that
 * their vertices carry the sway tag in the vertex alpha. Done through Fabric's rendering API, as a quad
 * transform that runs while the chunk is meshed, so the models themselves are left as they are.
 */
final class FabricSwayModels {
    private FabricSwayModels() {
    }

    static void register() {
        if (!VegetationWind.wrapsModels()) {
            return;
        }
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            if (model == null) {
                return model;
            }
            VegetationWind.Sway sway = VegetationWind.swayOf(context.topLevelId());
            return sway == null ? model : new SwayModel(model, sway);
        }));
    }

    /** Tags every vertex of the wrapped model with its vegetation kind and bend weight. */
    private static final class SwayModel extends ForwardingBakedModel {
        private final RenderContext.QuadTransform transform;

        SwayModel(BakedModel wrapped, VegetationWind.Sway sway) {
            this.wrapped = wrapped;
            VegetationWind.wrappedModels++;
            this.transform = quad -> {
                for (int v = 0; v < 4; v++) {
                    int alpha = VegetationWind.tagAlpha(sway, quad.y(v));
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
        public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier,
                                   RenderContext context) {
            context.pushTransform(transform);
            super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
            context.popTransform();
        }
    }
}
