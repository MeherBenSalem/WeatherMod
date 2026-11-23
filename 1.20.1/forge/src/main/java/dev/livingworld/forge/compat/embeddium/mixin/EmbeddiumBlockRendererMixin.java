package dev.livingworld.forge.compat.embeddium.mixin;

import dev.livingworld.client.compat.sodium.SodiumBridge;
import dev.livingworld.client.compat.sodium.SodiumState;
import dev.livingworld.client.render.VegetationWind;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags the vertices of plants and of sky-facing quads as Embeddium's block mesher is about to write them
 * out. Embeddium keeps Sodium's class names, so this is the twin of the Fabric module's Sodium mixin.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class EmbeddiumBlockRendererMixin {
    @Shadow
    @Final
    private ChunkVertexEncoder.Vertex[] vertices;

    @Inject(method = "writeGeometry", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lme/jellysquid/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;)V"))
    private void livingworld$tag(BlockRenderContext ctx, ChunkModelBuilder builder, Vec3 offset, Material material, BakedQuadView quad,
                                 int[] colors, QuadLightData light, CallbackInfo ci) {
        // Only the compact vertex format has a spare byte in its light coordinate.
        if (!SodiumState.tagging() || SodiumClientMod.canUseVanillaVertices()) {
            return;
        }
        boolean up = quad.getNormalFace() == ModelQuadFacing.POS_Y;
        VegetationWind.Sway sway = VegetationWind.classify(ctx.state());
        // Height of each vertex within its block, as the model has it.
        float bottom = ctx.origin().y() + (float) offset.y();
        for (ChunkVertexEncoder.Vertex vertex : this.vertices) {
            vertex.light |= SodiumBridge.blockTag(sway, up, vertex.y - bottom);
        }
    }
}
