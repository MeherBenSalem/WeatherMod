package dev.livingworld.mixin;

import dev.livingworld.server.SeasonalGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
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
    private void livingworld$season(BlockState state, ServerLevel world, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!SeasonalGrowth.allow(world, pos, random)) {
            ci.cancel();
        }
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void livingworld$spring(BlockState state, ServerLevel world, BlockPos pos, RandomSource random, CallbackInfo ci) {
        SeasonalGrowth.bonus(world, pos, random);
    }
}
