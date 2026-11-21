package dev.livingworld.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.livingworld.client.LivingWorldClient;
import dev.livingworld.client.gui.LWConfigScreen;
import dev.livingworld.client.render.LWShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The client half of the Forge module: starts the common client code and forwards Forge's client events
 * to it. Only ever reached through {@code DistExecutor}, so a dedicated server never loads it.
 */
public final class LivingWorldForgeClient {
    private LivingWorldForgeClient() {
    }

    public static void init(IEventBus modBus) {
        LivingWorldClient.init();

        modBus.addListener(LivingWorldForgeClient::registerShaders);
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(LivingWorldClient.configKey()));
        modBus.addListener(ForgeSwayModels::onModifyBakingResult);

        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                LivingWorldClient.onClientTick(Minecraft.getInstance());
            }
        });
        bus.addListener(LivingWorldForgeClient::renderStage);
        bus.addListener((RenderGuiEvent.Post event) -> LivingWorldClient.onHudRender(event.getGuiGraphics(), event.getPartialTick()));
        bus.addListener((RegisterClientCommandsEvent event) -> LivingWorldClient.registerCommands(event.getDispatcher(),
                (source, message) -> source.sendSuccess(() -> message, false)));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LivingWorldClient.onDisconnect());

        // The "Config" button of the mod in Forge's mod list opens the settings screen.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new LWConfigScreen(parent)));
    }

    /** One of the mod's packets has arrived (network thread). */
    public static void receive(ResourceLocation channel, FriendlyByteBuf body) {
        LivingWorldClient.receive(channel, body);
    }

    private static void registerShaders(RegisterShadersEvent event) {
        // Forge fires this exactly once per resource reload.
        LWShaders.newReload();
        LivingWorldClient.registerShaders((id, format, onLoaded) ->
                event.registerShader(new ShaderInstance(event.getResourceProvider(), id, format), onLoaded));
    }

    /** The camera's pose for the frame being drawn, as the level renderer was handed it. */
    private static final Matrix4f FRAME_POSE = new Matrix4f();
    private static final Matrix3f FRAME_NORMAL = new Matrix3f();

    /**
     * The four points in a frame the common code draws at, as the stages of Forge's level renderer:
     * the sky is the first thing drawn, so "after the sky" is before any terrain; block entities are the
     * last opaque things drawn, so after them the frame is ready to be copied for the water.
     */
    private static void renderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            FRAME_POSE.set(event.getPoseStack().last().pose());
            FRAME_NORMAL.set(event.getPoseStack().last().normal());
            LivingWorldClient.onRenderStart(event.getCamera(), event.getPartialTick());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            LivingWorldClient.onAfterEntities(event.getPoseStack(), event.getCamera());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            LivingWorldClient.onBeforeTranslucent(event.getPoseStack(), event.getProjectionMatrix(), event.getCamera(), event.getPartialTick());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            // Every stage but this one comes with the camera's pose. This one is fired from the game
            // renderer, after the level renderer has returned, and Forge passes it the stack the
            // projection was built on instead. The camera's pose is the one the frame began with.
            PoseStack cameraPose = new PoseStack();
            cameraPose.last().pose().set(FRAME_POSE);
            cameraPose.last().normal().set(FRAME_NORMAL);
            LivingWorldClient.onRenderLast(cameraPose, event.getProjectionMatrix(), event.getCamera(), event.getPartialTick());
        }
    }
}
