package dev.livingworld.forge.compat.embeddium.mixin;

import dev.livingworld.client.compat.sodium.SodiumBridge;
import dev.livingworld.client.compat.sodium.SodiumState;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When Embeddium has bound its terrain program for a pass, hands the program this frame's wind, season and water values. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class EmbeddiumChunkRendererMixin {
    @Shadow
    protected GlProgram<ChunkShaderInterface> activeProgram;

    @Inject(method = "begin", at = @At("TAIL"), require = 0)
    private void livingworld$feed(TerrainRenderPass pass, CallbackInfo ci) {
        // Embeddium can be told to draw with a vertex format laid out like the game's own, which has no
        // spare byte for the tags; the mod then leaves its terrain alone.
        SodiumState.noRoomForTags = SodiumClientMod.canUseVanillaVertices();
        if (this.activeProgram != null) {
            SodiumBridge.feed(this.activeProgram.handle());
        }
    }
}
