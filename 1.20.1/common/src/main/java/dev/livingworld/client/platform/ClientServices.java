package dev.livingworld.client.platform;

import dev.livingworld.platform.Services;

/** The client's door to the mod loader; see {@link Services} for the one both sides share. */
public final class ClientServices {
    public static final IClientPlatformHelper PLATFORM = Services.load(IClientPlatformHelper.class);

    private ClientServices() {
    }
}
