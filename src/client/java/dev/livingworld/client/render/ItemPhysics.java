package dev.livingworld.client.render;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.util.LWMath;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

import java.util.WeakHashMap;

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

    public static void render(ItemEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
                              int light, ItemRenderer renderer, Random random, int copies) {
        ItemStack stack = entity.getStack();
        random.setSeed(stack.isEmpty() ? 187 : Item.getRawId(stack.getItem()) + stack.getDamage());
        BakedModel model = renderer.getModel(stack, entity.getWorld(), null, entity.getId());
        boolean solid = model.hasDepth();
        Transformation ground = model.getTransformation().ground;
        float size = ground.scale.y();
        float thickness = solid ? size : ground.scale.z() / 16f;

        boolean afloat = entity.isTouchingWater() || entity.isInLava();
        boolean resting = entity.isOnGround() || afloat;
        float speed = (float) entity.getVelocity().length();
        float time = entity.getItemAge() + tickDelta;

        Pose pose = POSES.get(entity);
        if (pose == null) {
            pose = new Pose();
            // Items that are already lying around when they first come into view start at rest.
            pose.tumble = resting ? (solid ? 0f : 90f) : entity.uniqueOffset * 57.29578f;
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
            bob = MathHelper.sin(time / 9f + entity.uniqueOffset) * 0.025f + 0.06f;
            rock = MathHelper.sin(time / 13f + entity.uniqueOffset * 2f) * 7f;
        } else if (resting && !solid) {
            // Light, flat things lift at the edges when the wind gets strong.
            float wind = ClientEnvironment.INSTANCE.windLocal;
            if (wind > 0.45f) {
                rock = MathHelper.sin(time * 0.55f + entity.uniqueOffset * 3f) * Math.min(1f, wind - 0.45f) * 5f;
            }
        }

        float yaw = entity.uniqueOffset * 57.29578f + pose.spin;
        // How flat the item currently lies: stacked copies pile up vertically when flat.
        float flat = Math.abs(MathHelper.sin(pose.tumble * 0.017453292f));
        for (int copy = 0; copy < copies; copy++) {
            matrices.push();
            float ox = 0f, oy = 0f, oz = 0f, twist = 0f;
            if (copy > 0) {
                ox = (random.nextFloat() * 2f - 1f) * (solid ? 0.13f : 0.07f);
                oz = (random.nextFloat() * 2f - 1f) * (solid ? 0.13f : 0.07f);
                twist = (random.nextFloat() * 2f - 1f) * 50f;
                oy = solid ? copy * 0.004f : copy * (thickness + 0.002f) * flat;
            }
            matrices.translate(ox, pose.height + bob + oy, oz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw + twist));
            if (rock != 0f) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rock));
            }
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.tumble));
            // The model's own "ground" offset assumes a hovering item; cancel it so the item turns about its centre.
            matrices.translate(-ground.translation.x(), -ground.translation.y(), -ground.translation.z());
            renderer.renderItem(stack, ModelTransformationMode.GROUND, false, matrices, consumers, light, OverlayTexture.DEFAULT_UV, model);
            matrices.pop();
        }
    }

    /** Height of the centre of an item tilted by {@code tumble} degrees whose lowest point touches the ground. */
    private static float restHeight(float tumble, float size, float thickness, boolean solid) {
        float radians = tumble * 0.017453292f;
        float c = Math.abs(MathHelper.cos(radians)), s = Math.abs(MathHelper.sin(radians));
        return solid ? 0.5f * size * (c + s) : 0.5f * size * c + 0.5f * thickness * s;
    }
}
