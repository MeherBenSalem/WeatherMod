package dev.livingworld.neoforge.client;

import dev.livingworld.client.render.VegetationWind;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The NeoForge half of {@link VegetationWind}: wraps the baked models of plants, leaves and vines so that
 * their vertices carry the sway tag in the vertex alpha.
 *
 * <p>NeoForge has no hook that changes a quad while a chunk is meshed, so the wrapper hands out copies of
 * the model's quads with the tag written into their vertex data. NeoForge's chunk mesher carries the alpha
 * of a quad's vertices through to the terrain shader, which is where the tag is read. Each quad is copied
 * once and then reused; the model underneath is left as it is.
 */
final class NeoForgeSwayModels {
    /** Ints per vertex in a baked quad (the block vertex format), and where in a vertex its y and its colour are. */
    private static final int STRIDE = 8, Y = 1, COLOR = 3;

    private NeoForgeSwayModels() {
    }

    static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        if (!VegetationWind.wrapsModels()) {
            return;
        }
        VegetationWind.wrappedModels = 0;
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            BakedModel model = entry.getValue();
            if (model == null) {
                continue;
            }
            VegetationWind.Sway sway = VegetationWind.swayOf(entry.getKey());
            if (sway != null) {
                entry.setValue(new SwayModel(model, sway));
                VegetationWind.wrappedModels++;
            }
        }
    }

    /** Hands out the wrapped model's quads with every vertex tagged with its vegetation kind and bend weight. */
    private static final class SwayModel extends BakedModelWrapper<BakedModel> {
        private final VegetationWind.Sway sway;
        /** Chunks are meshed on several threads at once. */
        private final Map<BakedQuad, BakedQuad> tagged = new ConcurrentHashMap<>();

        SwayModel(BakedModel wrapped, VegetationWind.Sway sway) {
            super(wrapped);
            this.sway = sway;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return tag(this.originalModel.getQuads(state, side, random));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data,
                                        @Nullable RenderType renderType) {
            return tag(this.originalModel.getQuads(state, side, random, data, renderType));
        }

        private List<BakedQuad> tag(List<BakedQuad> quads) {
            if (quads.isEmpty()) {
                return quads;
            }
            List<BakedQuad> out = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                out.add(this.tagged.computeIfAbsent(quad, this::tag));
            }
            return out;
        }

        private BakedQuad tag(BakedQuad quad) {
            int[] vertices = quad.getVertices().clone();
            if (vertices.length < 4 * STRIDE) {
                return quad;
            }
            for (int v = 0; v < 4; v++) {
                // Height of the vertex within its block, as the model has it.
                float y = Float.intBitsToFloat(vertices[v * STRIDE + Y]);
                int alpha = VegetationWind.tagAlpha(this.sway, y);
                vertices[v * STRIDE + COLOR] = (vertices[v * STRIDE + COLOR] & 0x00FFFFFF) | (alpha << 24);
            }
            return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                    quad.hasAmbientOcclusion());
        }
    }
}
