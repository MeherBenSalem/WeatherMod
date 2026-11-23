package dev.livingworld.neoforge;

import dev.livingworld.LivingWorld;
import dev.livingworld.neoforge.client.LivingWorldNeoForgeClient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge's mod class. Runs on both the client and the dedicated server: it starts the common code and
 * forwards NeoForge's events to it. Everything the mod actually does is in the common module.
 */
@Mod(LivingWorld.MOD_ID)
public final class LivingWorldNeoForge {
    public LivingWorldNeoForge(IEventBus modBus, ModContainer container) {
        LivingWorld.init();
        modBus.addListener(NeoForgeNetwork::register);

        IEventBus bus = NeoForge.EVENT_BUS;
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

        bus.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                LivingWorld.onLevelTick(level);
            }
        });
        bus.addListener((ServerTickEvent.Post event) -> LivingWorld.onServerTick(event.getServer()));
        bus.addListener((ServerStoppedEvent event) -> LivingWorld.onServerStopped());

        // Nothing below this line may be loaded on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LivingWorldNeoForgeClient.init(modBus, container);
        }
    }
}
