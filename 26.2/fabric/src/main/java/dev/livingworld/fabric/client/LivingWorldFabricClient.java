package dev.livingworld.fabric.client;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.LivingWorldClient;
import dev.livingworld.net.LWNetworking;
import dev.livingworld.net.LWPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Fabric's client entry point: starts the common client code and forwards Fabric API's client events to
 * it. Never loaded on a dedicated server.
 */
public final class LivingWorldFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LivingWorldClient.init();

        ClientTickEvents.END_CLIENT_TICK.register(LivingWorldClient::onClientTick);
        // The level's submit phase: the pose stack is camera-relative (translation only), like NeoForge's
        // SubmitCustomGeometryEvent.
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            LivingWorldClient.onSubmit(context.poseStack(), context.submitNodeCollector(), mc.gameRenderer.getMainCamera(),
                    mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        });

        for (CustomPacketPayload.Type<LWPayload> type : LWNetworking.TYPES) {
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                    LivingWorldClient.receive(payload.type().id(), payload.read()));
        }
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LivingWorldClient.onDisconnect());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                LivingWorldClient.registerCommands(dispatcher, FabricClientCommandSource::sendFeedback));
        HudElementRegistry.addLast(LivingWorld.id("hud"), (graphics, deltaTracker) ->
                LivingWorldClient.onHudRender(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        KeyMappingHelper.registerKeyMapping(LivingWorldClient.configKey(KeyMapping.Category.register(LivingWorldClient.keyCategoryId())));
    }
}
