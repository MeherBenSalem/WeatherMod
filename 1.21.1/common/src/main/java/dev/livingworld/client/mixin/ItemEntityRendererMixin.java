package dev.livingworld.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingworld.client.render.ItemPhysics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the drawing of dropped items to {@link ItemPhysics} when the option is on. Optional: if another
 * mod has rewritten this renderer the injection is skipped and items keep whatever that mod does.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin extends EntityRenderer<ItemEntity> {
    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    @Shadow
    @Final
    private RandomSource random;

    protected ItemEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void livingworld$physics(ItemEntity entity, float yaw, float tickDelta, PoseStack matrices,
                                     MultiBufferSource consumers, int light, CallbackInfo ci) {
        if (!ItemPhysics.active()) {
            return;
        }
        matrices.pushPose();
        ItemPhysics.render(entity, tickDelta, matrices, consumers, light, this.itemRenderer, this.random,
                livingworld$renderedAmount(entity.getItem().getCount()));
        matrices.popPose();
        // The name tag, exactly as the vanilla renderer ends.
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
        ci.cancel();
    }

    /** How many copies of the stack the vanilla renderer draws for this count (its own helper is protected). */
    private static int livingworld$renderedAmount(int count) {
        if (count > 48) {
            return 5;
        }
        if (count > 32) {
            return 4;
        }
        if (count > 16) {
            return 3;
        }
        return count > 1 ? 2 : 1;
    }
}
