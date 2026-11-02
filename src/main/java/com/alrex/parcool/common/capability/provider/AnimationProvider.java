package com.alrex.parcool.common.capability.provider;

import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.capabilities.ClientCapabilities;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Animation 的 Capability Provider（1.12.2 版）。
 *
 * <p>纯客户端 —— 本类与 {@link Animation} 都标了 {@code @SideOnly(Side.CLIENT)}，
 * 只由 {@code ClientProxy} 侧挂载（见 {@code AttachCapabilityHandler} 的类注释）。</p>
 *
 * <p>与 {@code ParkourabilityProvider} 同样的处理：上游的
 * {@code LazyOptional.of(Animation::new)} 换成「延迟创建 + 缓存」。</p>
 */
@SideOnly(Side.CLIENT)
public class AnimationProvider implements ICapabilityProvider {

    @Nullable
    private Animation instance = null;

    private Animation getOrCreate() {
        if (instance == null) {
            instance = new Animation();
        }
        return instance;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == ClientCapabilities.ANIMATION_CAPABILITY;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == ClientCapabilities.ANIMATION_CAPABILITY) {
            return (T) getOrCreate();
        }
        return null;
    }
}
