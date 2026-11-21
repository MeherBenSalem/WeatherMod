package dev.livingworld.forge.compat.embeddium;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Embeddium mixins only when Embeddium is installed, and only for the 0.3 line the hooks
 * were written against (the port of Sodium 0.5 to Forge). With any other version (or none) nothing here
 * touches anything, and water, sway and seasonal foliage simply stay off under Embeddium.
 */
public final class EmbeddiumMixinPlugin implements IMixinConfigPlugin {
    private static final String MOD_ID = "embeddium";

    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        apply = false;
        // Mixin configurations are read before the mods themselves are constructed, so the list of mod
        // files that were found is all there is to ask.
        ModFileInfo file = LoadingModList.get().getModFileById(MOD_ID);
        if (file == null) {
            return;
        }
        for (IModInfo mod : file.getMods()) {
            if (MOD_ID.equals(mod.getModId())) {
                apply = mod.getVersion().toString().startsWith("0.3.");
            }
        }
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
