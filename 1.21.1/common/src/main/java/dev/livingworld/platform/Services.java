package dev.livingworld.platform;

import dev.livingworld.LivingWorld;
import dev.livingworld.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * The one door from the loader-independent code to the mod loader it happens to be running on. Each
 * loader module names its implementation in {@code META-INF/services}, and it is looked up here once.
 */
public final class Services {
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> type) {
        T service = ServiceLoader.load(type).findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + type.getName()));
        LivingWorld.LOGGER.debug("Loaded {} for service {}", service, type);
        return service;
    }
}
