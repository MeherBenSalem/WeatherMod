package dev.livingworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dropped items that behave like objects instead of spinning holograms: they tumble while they fly,
 * come to rest lying flat (or, for blocks, on a face), pile up when stacked, rock on water and flutter
 * a little in strong wind.
 *
 * <p>Purely a change to how the item entity is drawn. The entity, its position, its pick-up box and
 * everything the server knows about it are untouched, so it works on any server and with any other
 * client.
 */
public final class ItemPhysics {
    private static final class Pose {
        float tumble;      // degrees about the item's side-to-side axis; 0 = upright
        float spin;        // extra yaw picked up in flight, degrees
        float height;      // height of the item's centre above the entity's feet
        float lastTime = Float.NaN;
    }

    private static final WeakHashMap<ItemEntity, Pose> POSES = new WeakHashMap<>();

    private ItemPhysics() {
    }

    public static boolean active() {
        ClientConfig config = ClientConfig.get();
        return config.enabled && config.itemPhysics;
    }

    public static void render(ItemEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource consumers,
                              int light, ItemRenderer renderer, RandomSource random, int copies) {
        ItemStack stack = entity.getItem();
        random.setSeed(stack.isEmpty() ? 187 : Item.getId(stack.getItem()) + stack.getDamageValue());
        BakedModel model = renderer.getModel(stack, entity.level(), null, entity.getId());
        boolean solid = model.isGui3d();
        ItemTransform ground = model.getTransforms().ground;
        float size = ground.scale.y();
        float thickness = solid ? size : ground.scale.z() / 16f;

        boolean afloat = entity.isInWater() || entity.isInLava();
        boolean resting = entity.onGround() || afloat;
        float speed = (float) entity.getDeltaMovement().length();
        float time = entity.getAge() + tickDelta;

        Pose pose = POSES.get(entity);
        if (pose == null) {
            pose = new Pose();
            // Items that are already lying around when they first come into view start at rest.
            pose.tumble = resting ? (solid ? 0f : 90f) : entity.bobOffs * 57.29578f;
            pose.height = resting ? restHeight(pose.tumble, size, thickness, solid) : 0.125f;
            POSES.put(entity, pose);
        }
        float dt = Float.isNaN(pose.lastTime) ? 0f : LWMath.clamp(time - pose.lastTime, 0f, 3f);
        pose.lastTime = time;

        float targetHeight;
        if (resting) {
            // Fall over onto the nearest stable side and stay there.
            float rest = solid ? Math.round(pose.tumble / 90f) * 90f : Math.round((pose.tumble - 90f) / 180f) * 180f + 90f;
            float ease = 1f - (float) Math.exp(-dt * 0.5f);
            pose.tumble += (rest - pose.tumble) * ease;
            targetHeight = restHeight(pose.tumble, size, thickness, solid) + 0.003f;
            pose.height += (targetHeight - pose.height) * ease;
        } else {
            // In the air: tumble, faster the faster it is moving.
            pose.tumble += dt * (9f + 60f * speed);
            pose.spin += dt * 24f * speed;
            pose.height += (0.125f - pose.height) * (1f - (float) Math.exp(-dt * 0.6f));
        }
        if (pose.tumble > 720f || pose.tumble < -720f) {
            pose.tumble %= 360f;
        }

        float rock = 0f, bob = 0f;
        if (afloat) {
            // Floating things ride the surface.
            bob = Mth.sin(time / 9f + entity.bobOffs) * 0.025f + 0.06f;
            rock = Mth.sin(time / 13f + entity.bobOffs * 2f) * 7f;
        } else if (resting && !solid) {
            // Light, flat things lift at the edges when the wind gets strong.
            float wind = ClientEnvironment.INSTANCE.windLocal;
            if (wind > 0.45f) {
                rock = Mth.sin(time * 0.55f + entity.bobOffs * 3f) * Math.min(1f, wind - 0.45f) * 5f;
            }
        }

        float yaw = entity.bobOffs * 57.29578f + pose.spin;
        // How flat the item currently lies: stacked copies pile up vertically when flat.
        float flat = Math.abs(Mth.sin(pose.tumble * 0.017453292f));
        for (int copy = 0; copy < copies; copy++) {
            matrices.pushPose();
            float ox = 0f, oy = 0f, oz = 0f, twist = 0f;
            if (copy > 0) {
                ox = (random.nextFloat() * 2f - 1f) * (solid ? 0.13f : 0.07f);
                oz = (random.nextFloat() * 2f - 1f) * (solid ? 0.13f : 0.07f);
                twist = (random.nextFloat() * 2f - 1f) * 50f;
                oy = solid ? copy * 0.004f : copy * (thickness + 0.002f) * flat;
            }
            matrices.translate(ox, pose.height + bob + oy, oz);
            matrices.mulPose(Axis.YP.rotationDegrees(yaw + twist));
            if (rock != 0f) {
                matrices.mulPose(Axis.ZP.rotationDegrees(rock));
            }
            matrices.mulPose(Axis.XP.rotationDegrees(pose.tumble));
            // The model's own "ground" offset assumes a hovering item; cancel it so the item turns about its centre.
            matrices.translate(-ground.translation.x(), -ground.translation.y(), -ground.translation.z());
            renderer.render(stack, ItemDisplayContext.GROUND, false, matrices, consumers, light, OverlayTexture.NO_OVERLAY, model);
            matrices.popPose();
        }
    }

    /** Height of the centre of an item tilted by {@code tumble} degrees whose lowest point touches the ground. */
    private static float restHeight(float tumble, float size, float thickness, boolean solid) {
        float radians = tumble * 0.017453292f;
        float c = Math.abs(Mth.cos(radians)), s = Math.abs(Mth.sin(radians));
        return solid ? 0.5f * size * (c + s) : 0.5f * size * c + 0.5f * thickness * s;
    }
}
