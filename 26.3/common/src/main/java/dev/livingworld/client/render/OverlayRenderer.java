package dev.livingworld.client.render;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.ClientHooks;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.debug.Perf;
import dev.livingworld.client.player.PlayerExposure;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The lens overlay. Other versions draw frost, refracting water droplets and a warm/cold tint with a full-screen
 * program of the mod's own; this version of the game has no place for it, so the frost creeping in from the
 * edges is drawn as a plain textured layer with the HUD (droplets and tint are not shown).
 */
public final class OverlayRenderer {
    private static final Identifier FROST = LivingWorld.id("textures/overlay/frost.png");

    private OverlayRenderer() {
    }

    private static boolean wanted() {
        return ClientHooks.active() && ClientConfig.get().overlays();
    }

    /** Called from the HUD pass, under the rest of the HUD. */
    public static void renderHudFallback(GuiGraphicsExtractor context) {
        if (!wanted()) {
            return;
        }
        float amount = PlayerExposure.INSTANCE.frost;
        if (amount <= 0.02f) {
            return;
        }
        long start = System.nanoTime();
        int w = context.guiWidth();
        int h = context.guiHeight();
        context.blit(RenderPipelines.GUI_TEXTURED, FROST, 0, 0, 0f, 0f, w, h, w, h,
                ARGB.colorFromFloat(Math.min(1f, amount * 1.1f), 1f, 1f, 1f));
        Perf.RENDER_OVERLAY.add(System.nanoTime() - start);
    }
}
