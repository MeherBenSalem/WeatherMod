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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingworld.net.LWPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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
        // The level renderer keeps the camera's rotation on the model-view stack while it draws, and hands
        // events the identity pose; the common code is given the camera's pose (the frustum matrix) where it
        // needs the view itself, and the identity pose where it draws inside the level renderer.
        WorldRenderEvents.START.register(context -> LivingWorldClient.onRenderStart(context.camera(), partialTick(context)));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> LivingWorldClient.onAfterEntities(new PoseStack(), context.camera()));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> LivingWorldClient.onBeforeTranslucent(cameraPose(context),
                context.projectionMatrix(), context.camera(), partialTick(context)));
        WorldRenderEvents.LAST.register(context -> LivingWorldClient.onRenderLast(cameraPose(context), context.projectionMatrix(),
                context.camera(), partialTick(context)));

        for (CustomPacketPayload.Type<LWPayload> type : LWNetworking.TYPES) {
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                    LivingWorldClient.receive(payload.type().id(), payload.read()));
        }
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LivingWorldClient.onDisconnect());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                LivingWorldClient.registerCommands(dispatcher, FabricClientCommandSource::sendFeedback));
        HudRenderCallback.EVENT.register((graphics, deltaTracker) ->
                LivingWorldClient.onHudRender(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        KeyBindingHelper.registerKeyBinding(LivingWorldClient.configKey());
    }

    private static float partialTick(WorldRenderContext context) {
        return context.tickCounter().getGameTimeDeltaPartialTick(false);
    }

    /** The camera's pose for this frame: its rotation, as the level renderer was given it. */
    private static PoseStack cameraPose(WorldRenderContext context) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(context.positionMatrix());
        return pose;
    }
}
