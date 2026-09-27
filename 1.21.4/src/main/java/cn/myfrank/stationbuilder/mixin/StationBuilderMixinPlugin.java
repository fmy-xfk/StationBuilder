package cn.myfrank.stationbuilder.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class StationBuilderMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".mtr.")) {
            try {
                // 1. 尝试使用 LoadingModList 检测。它在 FML 加载的最早期对 MixinConfigPlugin 是可用的
                return net.neoforged.fml.loading.LoadingModList.get().getModFileById("mtr") != null;
            } catch (Throwable e) {
                try {
                    // 2. 备用安全方案：直接检测 MTR 相关的关键类是否在当前 classpath 下
                    Class.forName("org.mtr.mod.item.ItemRailModifier", false, this.getClass().getClassLoader());
                    return true;
                } catch (ClassNotFoundException ex) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}