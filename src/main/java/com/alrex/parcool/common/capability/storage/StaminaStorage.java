package com.alrex.parcool.common.capability.storage;

import com.alrex.parcool.common.capability.IStamina;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

/**
 * 体力 Capability 的 NBT 读写（1.12.2 版）。
 *
 * <p>对应上游 {@code StaminaStorage}。只有两类改名：</p>
 * <ul>
 *   <li>{@code CompoundNBT} → {@link NBTTagCompound}；{@code INBT} → {@link NBTBase}</li>
 *   <li>{@code Direction} → {@link EnumFacing}</li>
 * </ul>
 *
 * <p>键名（{@code "value"} / {@code "exhausted"}）<b>刻意与上游保持一致</b>，
 * 这样将来若要跨版本迁移存档数据可以直接对上。</p>
 */
public class StaminaStorage implements Capability.IStorage<IStamina> {

    @Override
    public NBTBase writeNBT(Capability<IStamina> capability, IStamina instance, EnumFacing side) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("exhausted", instance.isExhausted());
        nbt.setInteger("value", instance.get());
        return nbt;
    }

    @Override
    public void readNBT(Capability<IStamina> capability, IStamina instance, EnumFacing side, NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) nbt;
            instance.set(compound.getInteger("value"));
            instance.setExhaustion(compound.getBoolean("exhausted"));
        } else {
            throw new IllegalArgumentException("NBT for StaminaStorage is not an NBTTagCompound: " + nbt);
        }
    }
}
