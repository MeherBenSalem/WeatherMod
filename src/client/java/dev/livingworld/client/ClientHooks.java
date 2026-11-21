package dev.livingworld.client;

import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.env.ClientEnvironment;

/** Small predicates the mixins ask, kept out of the mixin classes themselves. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static boolean active() {
        return ClientConfig.get().enabled && ClientEnvironment.INSTANCE.inWorld;
    }

    public static boolean replacesVanillaPrecipitation() {
        return active() && ClientConfig.get().customPrecipitation;
    }

    /** The game's own flat clouds are not drawn while the mod draws its cloud layer. */
    public static boolean replacesVanillaClouds() {
        return active() && dev.livingworld.client.env.Atmosphere.INSTANCE.cloudsDrawn;
    }

    /**
     * Vanilla's rain splash particles and rain loop are only suppressed when the mod provides both
     * replacements; otherwise the player would lose the sound of rain.
     */
    public static boolean replacesVanillaRainAmbience() {
        ClientConfig c = ClientConfig.get();
        return active() && c.customPrecipitation && c.rainSplashes && c.ambience && c.replaceVanillaRain
                && LivingWorldClient.audioRunning();
    }
}
