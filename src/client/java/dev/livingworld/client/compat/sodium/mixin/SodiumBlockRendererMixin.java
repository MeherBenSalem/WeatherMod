package dev.livingworld.client.compat.sodium.mixin;

import dev.livingworld.client.compat.sodium.SodiumBridge;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tags the vertices of plants and of sky-facing quads as Sodium's block mesher is about to write them out. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {
    @Shadow
    @Final
    private ChunkVertexEncoder.Vertex[] vertices;

    @Inject(method = "writeGeometry", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lme/jellysquid/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;)V"))
    private void livingworld$tag(BlockRenderContext ctx, ChunkModelBuilder builder, Vec3d offset, Material material, BakedQuadView quad,
                                 int[] colors, QuadLightData light, CallbackInfo ci) {
        SodiumBridge.tagBlock(ctx, offset, quad, this.vertices);
    }
}
