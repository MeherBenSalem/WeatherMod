package dev.livingworld.client.mixin;

import dev.livingworld.client.render.ItemPhysics;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
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
    private Random random;

    @Shadow
    private int getRenderedAmount(ItemStack stack) {
        throw new AssertionError();
    }

    protected ItemEntityRendererMixin(EntityRendererFactory.Context context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void livingworld$physics(ItemEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                     VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!ItemPhysics.active()) {
            return;
        }
        matrices.push();
        ItemPhysics.render(entity, tickDelta, matrices, consumers, light, this.itemRenderer, this.random,
                this.getRenderedAmount(entity.getStack()));
        matrices.pop();
        // The name tag, exactly as the vanilla renderer ends.
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
        ci.cancel();
    }
}
