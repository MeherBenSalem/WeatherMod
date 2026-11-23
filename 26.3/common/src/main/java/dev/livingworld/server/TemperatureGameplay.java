package dev.livingworld.server;

import dev.livingworld.climate.ClimateMath;
import dev.livingworld.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.Equippable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Optional survival rules for extreme heat and cold. Disabled unless the server config enables them, and
 * deliberately mild: extra hunger drain, plus slight slowness after prolonged extreme exposure. Never
 * deals damage and never touches the world.
 */
public final class TemperatureGameplay {
    private static final int INTERVAL = 40;
    /** Smoothed [cold, heat] strain per player. */
    private static final Map<UUID, float[]> BODY = new HashMap<>();
    private static final ServerClimate.Reading READING = new ServerClimate.Reading();
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private TemperatureGameplay() {
    }

    /** Drops what was remembered about a player who has left. */
    public static void forget(ServerPlayer player) {
        BODY.remove(player.getUUID());
    }

    /** Called at the end of every server tick. */
    public static void tick(MinecraftServer server) {
        ServerConfig config = ServerConfig.get();
        if (!config.temperatureEffects) {
            if (!BODY.isEmpty()) {
                BODY.clear();
            }
            return;
        }
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            apply(player, config);
        }
    }

    private static void apply(ServerPlayer player, ServerConfig config) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return;
        }
        ServerClimate.Reading reading = ServerClimate.read(player, READING);
        float cold = ClimateMath.coldStress(reading.feelsLikeC);
        float heat = ClimateMath.heatStress(reading.feelsLikeC);

        int leather = 0;
        int heavy = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            // Armor is any worn piece with an armour model (an elytra has one too, but is not armour).
            ItemStack stack = player.getItemBySlot(slot);
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null || equippable.assetId().isEmpty() || stack.has(DataComponents.GLIDER)) {
                continue;
            }
            if (equippable.assetId().get().equals(ArmorMaterials.LEATHER.assetId())) {
                leather++;
            } else {
                heavy++;
            }
        }
        cold *= 1f - 0.22f * leather;
        heat *= 1f + 0.06f * heavy + 0.04f * leather;
        if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            heat = 0f;
        }
        if (player.isInWater()) {
            heat *= 0.3f;
        }

        float[] body = BODY.computeIfAbsent(player.getUUID(), k -> new float[2]);
        body[0] += (cold - body[0]) * 0.15f;
        body[1] += (Math.min(heat, 1f) - body[1]) * 0.15f;
        float strain = Math.max(body[0], body[1]);

        if (strain > 0.25f) {
            player.causeFoodExhaustion(0.12f * strain * config.exhaustionScale);
        }
        if (config.extremeSlowness && strain > 0.8f) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0, true, false, true));
        }
    }
}
