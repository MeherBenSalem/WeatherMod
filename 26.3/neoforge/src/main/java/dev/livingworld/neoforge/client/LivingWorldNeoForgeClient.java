package dev.livingworld.neoforge.client;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.LivingWorldClient;
import dev.livingworld.client.gui.LWConfigScreen;
import dev.livingworld.net.LWNetworking;
import dev.livingworld.net.LWPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client half of the NeoForge module: starts the common client code and forwards NeoForge's client
 * events to it. Only reached behind a client-side check, so a dedicated server never loads it.
 */
public final class LivingWorldNeoForgeClient {
    private LivingWorldNeoForgeClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        LivingWorldClient.init();

        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            KeyMapping.Category category = new KeyMapping.Category(LivingWorldClient.keyCategoryId());
            event.registerCategory(category);
            event.register(LivingWorldClient.configKey(category));
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(LivingWorld.id("hud"),
                (graphics, deltaTracker) -> LivingWorldClient.onHudRender(graphics, deltaTracker.getGameTimeDeltaPartialTick(false))));
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            for (CustomPacketPayload.Type<LWPayload> type : LWNetworking.TYPES) {
                event.register(type, (payload, context) -> LivingWorldClient.receive(payload.type().id(), payload.read()));
            }
        });

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Post event) -> LivingWorldClient.onClientTick(Minecraft.getInstance()));
        // The level's submit phase: the pose stack is camera-relative (translation only).
        bus.addListener((SubmitCustomGeometryEvent event) -> {
            Minecraft mc = Minecraft.getInstance();
            LivingWorldClient.onSubmit(event.getPoseStack(), event.getSubmitNodeCollector(), mc.gameRenderer.mainCamera(),
                    mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        });
        bus.addListener((RegisterClientCommandsEvent event) -> LivingWorldClient.registerCommands(event.getDispatcher(),
                (source, message) -> source.sendSuccess(() -> message, false)));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LivingWorldClient.onDisconnect());

        // The "Config" button of the mod in NeoForge's mod list opens the settings screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new LWConfigScreen(parent));
    }
}
