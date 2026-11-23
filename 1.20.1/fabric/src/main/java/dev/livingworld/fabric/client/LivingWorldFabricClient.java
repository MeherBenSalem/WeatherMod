package dev.livingworld.fabric.client;

import dev.livingworld.client.LivingWorldClient;
import dev.livingworld.net.LWNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Fabric's client entry point: starts the common client code and forwards Fabric API's client events to
 * it. Never loaded on a dedicated server.
 */
public final class LivingWorldFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LivingWorldClient.init();

        CoreShaderRegistrationCallback.EVENT.register(context -> LivingWorldClient.registerShaders(context::register));
        FabricSwayModels.register();

        ClientTickEvents.END_CLIENT_TICK.register(LivingWorldClient::onClientTick);
        WorldRenderEvents.START.register(context -> LivingWorldClient.onRenderStart(context.camera(), context.tickDelta()));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> LivingWorldClient.onAfterEntities(context.matrixStack(), context.camera()));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> LivingWorldClient.onBeforeTranslucent(context.matrixStack(),
                context.projectionMatrix(), context.camera(), context.tickDelta()));
        WorldRenderEvents.LAST.register(context -> LivingWorldClient.onRenderLast(context.matrixStack(), context.projectionMatrix(),
                context.camera(), context.tickDelta()));

        ClientPlayNetworking.registerGlobalReceiver(LWNetworking.STATE,
                (client, handler, buf, sender) -> LivingWorldClient.receive(LWNetworking.STATE, buf));
        ClientPlayNetworking.registerGlobalReceiver(LWNetworking.CLIENT_COMMAND,
                (client, handler, buf, sender) -> LivingWorldClient.receive(LWNetworking.CLIENT_COMMAND, buf));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LivingWorldClient.onDisconnect());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                LivingWorldClient.registerCommands(dispatcher, FabricClientCommandSource::sendFeedback));
        HudRenderCallback.EVENT.register(LivingWorldClient::onHudRender);
        KeyBindingHelper.registerKeyBinding(LivingWorldClient.configKey());
    }
}
