package com.alrex.parcool.common.capability.provider;

import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.capability.capabilities.Capabilities;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;

/**
 * Parkourability 的 Capability Provider（1.12.2 版）。
 *
 * <p>上游用 {@code LazyOptional.of(Parkourability::new)}；1.12.2 无 {@code LazyOptional}，
 * 改为<b>延迟创建 + 缓存</b>，语义等价（第一次取用时才 new）。</p>
 *
 * <p>注意必须实现 {@code hasCapability} —— 见 {@code StaminaProvider} 里的说明。</p>
 */
public class ParkourabilityProvider implements ICapabilityProvider {

    @Nullable
    private Parkourability instance = null;

    private Parkourability getOrCreate() {
        if (instance == null) {
            instance = new Parkourability();
        }
        return instance;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == Capabilities.PARKOURABILITY_CAPABILITY;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == Capabilities.PARKOURABILITY_CAPABILITY) {
            return (T) getOrCreate();
        }
        return null;
    }
}
