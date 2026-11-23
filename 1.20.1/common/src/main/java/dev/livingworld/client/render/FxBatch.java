package dev.livingworld.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * One translucent draw call for everything that uses the particle sprite sheet (simulated particles and
 * precipitation). Normally it uses the mod's soft-particle program; when custom programs are not safe
 * (shader pack active, or simple rendering selected) it falls back to the stock particle program, which
 * the pack knows how to shade, at the cost of harder sprite edges.
 */
public final class FxBatch {
    public static final ResourceLocation SHEET = LivingWorld.id("textures/fx/particles.png");

    private BufferBuilder buffer;
    private int vertices;
    /** Quads drawn by the previous frame (debug/benchmark). */
    public int lastQuads;

    // The batch is drawn after the full-screen atmosphere pass (otherwise every drop would be fogged by
    // the distance of whatever lies behind it), so sprites are hazed here by their own distance instead.
    public float hazeR, hazeG, hazeB;
    private float hazeDensity, hazeMax;

    public void setHaze(float r, float g, float b, float density, float max) {
        hazeR = r;
        hazeG = g;
        hazeB = b;
        hazeDensity = density;
        hazeMax = max;
    }

    /** Fraction (0..1) by which something at this distance is replaced by the haze colour. */
    public float haze(float distance) {
        return hazeDensity <= 1.0e-5f ? 0f : (1f - (float) Math.exp(-hazeDensity * distance)) * hazeMax;
    }

    public void begin() {
        buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        vertices = 0;
    }

    public void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a, int light) {
        // Colours are stored as bytes: anything above 1 would wrap around rather than saturate.
        buffer.vertex(x, y, z).uv(u, v)
                .color(r > 1f ? 1f : r, g > 1f ? 1f : g, b > 1f ? 1f : b, a > 1f ? 1f : a)
                .uv2(light).endVertex();
        vertices++;
    }

    public void draw() {
        lastQuads = vertices / 4;
        if (vertices == 0) {
            BufferBuilder.RenderedBuffer empty = buffer.endOrDiscardIfEmpty();
            if (empty != null) {
                empty.release();
            }
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LightTexture lightmap = client.gameRenderer.lightTexture();
        if (LWShaders.fx != null && Compat.customShadersAllowed()) {
            RenderSystem.setShader(() -> LWShaders.fx);
        } else {
            RenderSystem.setShader(GameRenderer::getParticleShader);
        }
        RenderSystem.setShaderTexture(0, SHEET);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        lightmap.turnOnLightLayer();
        BufferUploader.drawWithShader(buffer.end());
        lightmap.turnOffLightLayer();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
