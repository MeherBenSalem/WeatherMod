package dev.livingworld.fabric;

import dev.livingworld.LivingWorld;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import dev.livingworld.net.LWNetworking;
import dev.livingworld.net.LWPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Fabric's main entry point. Runs on both the client and the dedicated server: it starts the common
 * code and forwards Fabric API's events to it. Everything the mod actually does is in the common module.
 */
public final class LivingWorldFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        LivingWorld.init();
        // Every packet of the mod travels from the server to the client, each type on its own channel.
        for (CustomPacketPayload.Type<LWPayload> type : LWNetworking.TYPES) {
            PayloadTypeRegistry.playS2C().register(type, LWPayload.codec(type));
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LivingWorld.registerCommands(dispatcher));
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> LivingWorld.onTagsLoaded());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LivingWorld.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LivingWorld.onPlayerLeave(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> LivingWorld.onPlayerChangedDimension(player));

        ServerTickEvents.END_WORLD_TICK.register(LivingWorld::onLevelTick);
        ServerTickEvents.END_SERVER_TICK.register(LivingWorld::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LivingWorld.onServerStopped());
    }
}
