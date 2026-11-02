package com.alrex.parcool.common.capability.storage;

import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

/**
 * Parkourability 的 NBT 读写（1.12.2 版）。
 *
 * <p><b>刻意不持久化任何东西</b> —— 与上游一致：{@code writeNBT} 返回 {@code null}，
 * {@code readNBT} 空实现。跑酷状态本来就只活在会话内（动作状态、冷却等），
 * 重启世界不该带着上次的「正在滑铲」。</p>
 *
 * <p>1.12.2 差异只有改名：{@code INBT} → {@link NBTBase}、{@code Direction} → {@link EnumFacing}。</p>
 */
public class ParkourabilityStorage implements Capability.IStorage<Parkourability> {

    @Nullable
    @Override
    public NBTBase writeNBT(Capability<Parkourability> capability, Parkourability instance, EnumFacing side) {
        return null;
    }

    @Override
    public void readNBT(Capability<Parkourability> capability, Parkourability instance, EnumFacing side, NBTBase nbt) {
        // 无持久化状态
    }
}
