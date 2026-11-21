package dev.livingworld.client.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.livingworld.client.render.WaterShading;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks water vertices while chunks are meshed, so the translucent terrain shader can tell water from
 * glass and ice. The mark is a vertex alpha of 250/255 where vanilla always writes 255; with the mod's
 * shader the alpha is restored to exactly 1, and without it (a resource pack replaced the shader) the
 * difference is a 2% change in water opacity. No block, fluid or world data is touched.
 *
 * <p>Chunks are meshed on worker threads, hence the thread-local. Both injections are optional: if
 * another mod rewrites the fluid mesher, water simply stays vanilla.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {
    @Unique
    private static final ThreadLocal<Boolean> LIVINGWORLD_WATER = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "tesselate", at = @At("HEAD"), require = 0)
    private void livingworld$noteFluid(BlockAndTintGetter world, BlockPos pos, VertexConsumer vertexConsumer, BlockState blockState,
                                       FluidState fluidState, CallbackInfo ci) {
        LIVINGWORLD_WATER.set(fluidState.is(FluidTags.WATER));
    }

    /**
     * The second name is the same method as Forge has it in a released game. Forge gives the method one
     * more parameter, and with its signature changed the build can no longer look its name up, so it is
     * written out here. Whichever of the two names exists is the one that is used.
     */
    @ModifyArg(method = {"vertex", "m_110984_"}, require = 0, index = 3, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;color(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private float livingworld$tagWater(float alpha) {
        return LIVINGWORLD_WATER.get() ? WaterShading.TAG_ALPHA : alpha;
    }
}
