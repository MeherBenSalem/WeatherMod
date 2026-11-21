package dev.livingworld.forge;

import dev.livingworld.LivingWorld;
import dev.livingworld.forge.client.LivingWorldForgeClient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge's mod class. Runs on both the client and the dedicated server: it starts the common code and
 * forwards Forge's events to it. Everything the mod actually does is in the common module.
 */
@Mod(LivingWorld.MOD_ID)
public final class LivingWorldForge {
    public LivingWorldForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        LivingWorld.init();
        ForgeNetwork.register();

        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((RegisterCommandsEvent event) -> LivingWorld.registerCommands(event.getDispatcher()));
        bus.addListener((TagsUpdatedEvent event) -> LivingWorld.onTagsLoaded());

        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                LivingWorld.onPlayerJoin(player);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                LivingWorld.onPlayerLeave(player);
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                LivingWorld.onPlayerChangedDimension(player);
            }
        });

        bus.addListener((TickEvent.LevelTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
                LivingWorld.onLevelTick(level);
            }
        });
        bus.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                LivingWorld.onServerTick(event.getServer());
            }
        });
        bus.addListener((ServerStoppedEvent event) -> LivingWorld.onServerStopped());

        // Nothing below this line may be loaded on a dedicated server.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LivingWorldForgeClient.init(modBus));
    }
}
