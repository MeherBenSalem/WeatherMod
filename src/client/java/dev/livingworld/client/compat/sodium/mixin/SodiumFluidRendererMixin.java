package dev.livingworld.client.compat.sodium.mixin;

import dev.livingworld.client.compat.sodium.SodiumBridge;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags water vertices as Sodium's fluid mesher is about to write them out, so the patched terrain shader
 * can tell water from glass and ice. One mesher per worker thread, so a plain field is enough.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer", remap = false)
public abstract class SodiumFluidRendererMixin {
    @Shadow
    @Final
    private ChunkVertexEncoder.Vertex[] vertices;

    @Unique
    private boolean livingworld$water;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void livingworld$noteFluid(WorldSlice world, FluidState fluidState, BlockPos pos, BlockPos offset, ChunkBuildBuffers buffers,
                                       CallbackInfo ci) {
        this.livingworld$water = fluidState.isIn(FluidTags.WATER);
    }

    @Inject(method = "writeQuad", require = 0, at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lme/jellysquid/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lme/jellysquid/mods/sodium/client/render/chunk/terrain/material/Material;)V"))
    private void livingworld$tag(ChunkModelBuilder builder, Material material, BlockPos offset, ModelQuadView quad, ModelQuadFacing facing,
                                 boolean flip, CallbackInfo ci) {
        if (this.livingworld$water) {
            SodiumBridge.tagWater(this.vertices);
        }
    }
}
