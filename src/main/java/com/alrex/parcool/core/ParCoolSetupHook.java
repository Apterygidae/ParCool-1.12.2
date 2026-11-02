package com.alrex.parcool.core;

import net.minecraftforge.fml.relauncher.IFMLCallHook;

import java.util.Map;

/**
 * CoreMod 的 setup hook。
 *
 * <p>FML 会在合适的时机调用 {@link #call()}，这里再初始化 Mixin —— 比在
 * {@code IFMLLoadingPlugin} 构造器里初始化更安全（此时类加载器状态已稳定）。</p>
 */
public class ParCoolSetupHook implements IFMLCallHook {

    @Override
    public void injectData(Map<String, Object> data) {
        // 不需要额外数据
    }

    @Override
    public Void call() {
        ParCoolCore.setupMixins();
        return null;
    }
}
