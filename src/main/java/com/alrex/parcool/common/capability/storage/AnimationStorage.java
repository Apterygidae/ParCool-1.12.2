package com.alrex.parcool.common.capability.storage;

import com.alrex.parcool.common.capability.Animation;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Animation 的 NBT 读写（1.12.2 版）。
 *
 * <p><b>刻意不持久化</b> —— 与上游一致：{@code writeNBT} 返回 {@code null}，{@code readNBT} 空实现。
 * 当前播放的动画是纯运行时状态。</p>
 */
@SideOnly(Side.CLIENT)
public class AnimationStorage implements Capability.IStorage<Animation> {

    @Nullable
    @Override
    public NBTBase writeNBT(Capability<Animation> capability, Animation instance, EnumFacing side) {
        return null;
    }

    @Override
    public void readNBT(Capability<Animation> capability, Animation instance, EnumFacing side, NBTBase nbt) {
        // 无持久化状态
    }
}
