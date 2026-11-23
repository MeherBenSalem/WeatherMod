package dev.livingworld.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingworld.client.LivingWorldClient;
import dev.livingworld.client.gui.LWConfigScreen;
import dev.livingworld.client.render.LWShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * The client half of the NeoForge module: starts the common client code and forwards NeoForge's client
 * events to it. Only reached behind a client-side check, so a dedicated server never loads it.
 */
public final class LivingWorldNeoForgeClient {
    private LivingWorldNeoForgeClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        LivingWorldClient.init();

        modBus.addListener(LivingWorldNeoForgeClient::registerShaders);
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(LivingWorldClient.configKey()));
        modBus.addListener(NeoForgeSwayModels::onModifyBakingResult);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Post event) -> LivingWorldClient.onClientTick(Minecraft.getInstance()));
        bus.addListener(LivingWorldNeoForgeClient::renderStage);
        bus.addListener((RenderGuiEvent.Post event) -> LivingWorldClient.onHudRender(event.getGuiGraphics(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        bus.addListener((RegisterClientCommandsEvent event) -> LivingWorldClient.registerCommands(event.getDispatcher(),
                (source, message) -> source.sendSuccess(() -> message, false)));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LivingWorldClient.onDisconnect());

        // The "Config" button of the mod in NeoForge's mod list opens the settings screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new LWConfigScreen(parent));
    }

    /** One of the mod's packets has arrived. */
    public static void receive(ResourceLocation channel, FriendlyByteBuf body) {
        LivingWorldClient.receive(channel, body);
    }

    private static void registerShaders(RegisterShadersEvent event) {
        // NeoForge fires this exactly once per resource reload.
        LWShaders.newReload();
        LivingWorldClient.registerShaders((id, format, onLoaded) ->
                event.registerShader(new ShaderInstance(event.getResourceProvider(), id, format), onLoaded));
    }

    /** The camera's pose (its rotation) for the frame being drawn, as the level renderer was handed it. */
    private static final Matrix4f FRAME_POSE = new Matrix4f();

    private static PoseStack framePose() {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(FRAME_POSE);
        return pose;
    }

    /**
     * The four points in a frame the common code draws at, as the stages of NeoForge's level renderer:
     * the sky is the first thing drawn, so "after the sky" is before any terrain; block entities are the
     * last opaque things drawn, so after them the frame is ready to be copied for the water. While the
     * level renderer draws, the camera's rotation is on the model-view stack, so what is drawn inside it is
     * given the identity pose; the camera's pose itself is given where the view is needed.
     */
    private static void renderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            FRAME_POSE.set(event.getModelViewMatrix());
            LivingWorldClient.onRenderStart(event.getCamera(), partialTick);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            LivingWorldClient.onAfterEntities(new PoseStack(), event.getCamera());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            LivingWorldClient.onBeforeTranslucent(framePose(), event.getProjectionMatrix(), event.getCamera(), partialTick);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            LivingWorldClient.onRenderLast(framePose(), event.getProjectionMatrix(), event.getCamera(), partialTick);
        }
    }
}
