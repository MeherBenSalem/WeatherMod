package dev.livingworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.livingworld.LivingWorld;
import dev.livingworld.platform.Services;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Tiny JSON config persistence. Unknown keys are ignored and missing keys keep their defaults. */
public final class ConfigIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

    private ConfigIO() {
    }

    public static Path path(String fileName) {
        return Services.PLATFORM.getConfigDir().resolve(fileName);
    }

    public static <T> T load(String fileName, Class<T> type, Supplier<T> defaults) {
        Path path = path(fileName);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                T value = GSON.fromJson(reader, type);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                LivingWorld.LOGGER.warn("Could not read {}, using defaults: {}", fileName, e.toString());
            }
        }
        T value = defaults.get();
        save(fileName, value);
        return value;
    }

    public static void save(String fileName, Object value) {
        Path path = path(fileName);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(value, writer);
            }
        } catch (Exception e) {
            LivingWorld.LOGGER.warn("Could not write {}: {}", fileName, e.toString());
        }
    }
}
