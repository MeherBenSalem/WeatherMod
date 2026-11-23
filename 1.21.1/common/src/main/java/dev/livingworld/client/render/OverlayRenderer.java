package dev.livingworld.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.ClientHooks;
import dev.livingworld.client.compat.Compat;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.debug.Perf;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.client.player.PlayerExposure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * The lens overlay: frost creeping in from the edges, water droplets that refract the scene, and a
 * faint warm/cold vignette under temperature strain. Runs after the world and hand but under the HUD,
 * and only on frames where something is actually visible.
 */
public final class OverlayRenderer {
    private static final ResourceLocation FROST = LivingWorld.id("textures/overlay/frost.png");
    private static final ResourceLocation DROPS = LivingWorld.id("textures/overlay/lens_drops.png");

    private OverlayRenderer() {
    }

    private static float frost() {
        return PlayerExposure.INSTANCE.frost;
    }

    private static boolean wanted() {
        if (!ClientHooks.active() || !ClientConfig.get().overlays()) {
            return false;
        }
        PlayerExposure body = PlayerExposure.INSTANCE;
        return body.frost > 0.004f || body.lensDrops > 0.004f || body.heat > 0.2f || body.cold > 0.2f;
    }

    /** Shader path: called right after the world (and hand) have been rendered. */
    public static void render(float tickDelta) {
        if (!wanted() || LWShaders.overlay == null || !Compat.customShadersAllowed()) {
            return;
        }
        long start = System.nanoTime();
        Minecraft client = Minecraft.getInstance();
        ClientEnvironment env = ClientEnvironment.INSTANCE;
        PlayerExposure body = PlayerExposure.INSTANCE;
        ClientConfig config = ClientConfig.get();
        ShaderInstance program = LWShaders.overlay;

        RenderTarget source = PostProcessor.copyMain(false);
        client.getTextureManager().getTexture(FROST).setFilter(true, false);
        client.getTextureManager().getTexture(DROPS).setFilter(true, false);
        program.setSampler("DiffuseSampler", source.getColorTextureId());
        program.setSampler("FrostSampler", client.getTextureManager().getTexture(FROST).getId());
        program.setSampler("DropSampler", client.getTextureManager().getTexture(DROPS).getId());

        RenderTarget main = client.getMainRenderTarget();
        float seconds = (float) (((env.ticks % 72000L) + tickDelta) / 20.0);
        float distortion = config.reduceDistortion ? 0.4f : 1f;
        LWShaders.set(program, "Lens", body.frost, body.lensDrops * distortion, seconds, (float) main.width / main.height);
        float tone = config.temperatureTint ? 1f : 0f;
        LWShaders.set(program, "Tone", Math.max(0f, body.heat - 0.2f) * 0.45f * tone, Math.max(0f, body.cold - 0.2f) * 0.5f * tone, 0f, 0f);
        PostProcessor.drawFullscreen(program);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Perf.RENDER_OVERLAY.add(System.nanoTime() - start);
    }

    /**
     * Fallback when custom programs are unavailable (shader pack, simple rendering): frost only, drawn
     * as a plain textured overlay from the HUD pass.
     */
    public static void renderHudFallback(GuiGraphics context) {
        if (!wanted() || (LWShaders.overlay != null && Compat.customShadersAllowed())) {
            return;
        }
        float amount = frost();
        if (amount <= 0.02f) {
            return;
        }
        int w = context.guiWidth();
        int h = context.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setColor(1f, 1f, 1f, Math.min(1f, amount * 1.1f));
        context.blit(FROST, 0, 0, 0f, 0f, w, h, w, h);
        context.setColor(1f, 1f, 1f, 1f);
    }
}
