package dev.livingworld.server;

import dev.livingworld.climate.Seasons;
import dev.livingworld.config.ServerConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * The one thing about the year that has to be remembered: how far the calendar has been shifted by
 * {@code /livingworld season set}. Saved with the world (in the overworld's data folder), so every
 * world keeps its own calendar. Everything else about the season follows from the day counter.
 */
public final class SeasonState extends PersistentState {
    private static final String ID = "livingworld_seasons";

    /** Calendar shift in days. */
    public float offsetDays;

    public static SeasonState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(SeasonState::read, SeasonState::new, ID);
    }

    private static SeasonState read(NbtCompound nbt) {
        SeasonState state = new SeasonState();
        state.offsetDays = nbt.getFloat("offsetDays");
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putFloat("offsetDays", offsetDays);
        return nbt;
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
        return Seasons.yearPhase(server.getOverworld().getTimeOfDay(), days, get(server).offsetDays);
    }

    /** Moves the calendar so that today falls in the middle of the given season. */
    public static void jumpTo(MinecraftServer server, Seasons.Season season) {
        SeasonState state = get(server);
        state.offsetDays = Seasons.offsetFor(season, server.getOverworld().getTimeOfDay(), ServerConfig.get().seasonLengthDays);
        state.markDirty();
    }

    /** Changes the length of a season while keeping today where it is in the year. */
    public static void setLength(MinecraftServer server, float days) {
        ServerConfig config = ServerConfig.get();
        SeasonState state = get(server);
        long time = server.getOverworld().getTimeOfDay();
        float phase = Seasons.yearPhase(time, config.seasonLengthDays, state.offsetDays);
        config.seasonLengthDays = days;
        config.save();
        double year = config.seasonLengthDays * 4.0;
        double offset = ((phase - Seasons.EPOCH) * year - time / 24000.0) % year;
        state.offsetDays = (float) (offset < 0.0 ? offset + year : offset);
        state.markDirty();
    }
}
