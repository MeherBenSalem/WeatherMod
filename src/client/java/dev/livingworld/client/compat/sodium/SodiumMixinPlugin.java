package dev.livingworld.client.compat.sodium;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Applies the Sodium mixins only when Sodium is installed, and only for the 0.5 line the hooks were
 * written against. With any other version (or none) nothing here touches anything, and water, sway and
 * seasonal foliage simply stay off under Sodium.
 */
public final class SodiumMixinPlugin implements IMixinConfigPlugin {
    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        Optional<ModContainer> sodium = FabricLoader.getInstance().getModContainer("sodium");
        apply = sodium.isPresent() && sodium.get().getMetadata().getVersion().getFriendlyString().startsWith("0.5.");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return apply;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
