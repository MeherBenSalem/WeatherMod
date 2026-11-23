package dev.livingworld.server;

import dev.livingworld.climate.Seasons;
import dev.livingworld.config.ServerConfig;
import com.mojang.serialization.Codec;
import dev.livingworld.LivingWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The one thing about the year that has to be remembered: how far the calendar has been shifted by
 * {@code /livingworld season set}. Saved with the world (in the overworld's data folder), so every
 * world keeps its own calendar. Everything else about the season follows from the day counter.
 */
public final class SeasonState extends SavedData {
    private static final Codec<SeasonState> CODEC = Codec.FLOAT.optionalFieldOf("offsetDays", 0f).codec()
            .xmap(SeasonState::of, state -> state.offsetDays);
    // Mod data has no data fixers (both loaders accept a null fixer type).
    private static final SavedDataType<SeasonState> TYPE = new SavedDataType<>(LivingWorld.id("seasons"), SeasonState::new, CODEC, null);

    /** Calendar shift in days. */
    public float offsetDays;

    public static SeasonState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static SeasonState of(float offsetDays) {
        SeasonState state = new SeasonState();
        state.offsetDays = offsetDays;
        return state;
    }

    /** Length of a season in days as this server runs it, or 0 when seasons are switched off. */
    public static float seasonDays() {
        ServerConfig config = ServerConfig.get();
        return config.seasons ? config.seasonLengthDays : 0f;
    }

    /** Position in the year right now (0..1), or a negative value when seasons are off. */
    public static float yearPhase(MinecraftServer server) {
        float days = seasonDays();
        if (days <= 0f) {
            return -1f;
        }
        return Seasons.yearPhase(server.overworld().getOverworldClockTime(), days, get(server).offsetDays);
    }

    /** Moves the calendar so that today falls in the middle of the given season. */
    public static void jumpTo(MinecraftServer server, Seasons.Season season) {
        SeasonState state = get(server);
        state.offsetDays = Seasons.offsetFor(season, server.overworld().getOverworldClockTime(), ServerConfig.get().seasonLengthDays);
        state.setDirty();
    }

    /** Changes the length of a season while keeping today where it is in the year. */
    public static void setLength(MinecraftServer server, float days) {
        ServerConfig config = ServerConfig.get();
        SeasonState state = get(server);
        long time = server.overworld().getOverworldClockTime();
        float phase = Seasons.yearPhase(time, config.seasonLengthDays, state.offsetDays);
        config.seasonLengthDays = days;
        config.save();
        double year = config.seasonLengthDays * 4.0;
        double offset = ((phase - Seasons.EPOCH) * year - time / 24000.0) % year;
        state.offsetDays = (float) (offset < 0.0 ? offset + year : offset);
        state.setDirty();
    }
}
