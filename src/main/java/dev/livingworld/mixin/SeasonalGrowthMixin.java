package dev.livingworld.mixin;

import dev.livingworld.server.SeasonalGrowth;
import net.minecraft.block.BlockState;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the optional seasonal-growth rule skip, or add to, the random growth ticks of the plants a
 * farmer cares about. With the rule off (the default) both hooks return at once.
 */
@Mixin({CropBlock.class, StemBlock.class, SaplingBlock.class, SweetBerryBushBlock.class, CocoaBlock.class})
public abstract class SeasonalGrowthMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void livingworld$season(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!SeasonalGrowth.allow(world, pos, random)) {
            ci.cancel();
        }
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void livingworld$spring(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        SeasonalGrowth.bonus(world, pos, random);
    }
}
