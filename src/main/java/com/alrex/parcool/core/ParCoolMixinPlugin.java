package com.alrex.parcool.core;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件：按条件裁剪 mixin 的应用。
 *
 * <p>P0 阶段恒返回 true。保留它是因为后续三个场景都要用到（对应移植方案 §4.3 / §5）：</p>
 * <ul>
 *   <li>Optifine 存在时才启用某些渲染注入（1.12.2 玩家基数里 Optifine 占比很高）</li>
 *   <li>与 Aqua 的冲突项需要按条件开关（见 {@link #shouldApplyMixin}）</li>
 *   <li>第三方 mod（ShoulderSurfing 等）的兼容 mixin 按 {@code Loader.isModLoaded} 挂载</li>
 * </ul>
 */
public class ParCoolMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // 暂无需要预热的逻辑
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /** 用于按「某个可选类是否存在」决定是否应用 mixin，例如 Optifine。 */
    @SuppressWarnings("unused")
    private boolean doesClassExist(String name) {
        try {
            Class.forName(name, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // TODO(P3): 这里将是与 Aqua Acrobatics 冲突项的开关。
        //           注意 ParCool 与 Aqua 是「硬依赖 + 冲突治理」关系，
        //           不能靠关闭自己的 mixin 来回避冲突（否则功能直接缺失），
        //           而是要在 mixin 内部用 priority + 复用 Aqua API 的方式共存。
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 不需要感知其它配置的目标类
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无
    }
}
