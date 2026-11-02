package com.alrex.parcool.common.block.zipline;

import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import com.alrex.parcool.common.zipline.ZiplineType;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * 一条滑索的「元数据」：**松紧档位 + 颜色** ✓ —— 1.12.2 版。
 *
 * <p>它跟着**连接记录**走（存在挂钩 TileEntity 的 NBT 里 ✓），
 * 也被用来生成绳索实体 ✓。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><td>{@code CompoundNBT}</td><td>{@link NBTTagCompound} ✓</td></tr>
 *   <tr><td>{@code INBT}</td><td>{@link NBTBase} ✓</td></tr>
 *   <tr><td>{@code CompoundNBT#contains(k)}</td><td>{@code hasKey(k)} ✓</td></tr>
 *   <tr><td>{@code tag.putInt/putByte}</td><td>{@code setInteger/setByte} ✓</td></tr>
 *   <tr><td>{@code tag.getInt/getByte}</td><td>{@code getInteger/getByte} ✓</td></tr>
 * </table>
 *
 * <p><b>⚠️ 档位存的是 {@code byte}（ordinal ✓）</b> —— 读回时用
 * {@code % values().length} 兜底 ✗→✓（枚举将来加项也不会读出越界 ✗）✓ 照搬上游 ✓</p>
 */
public class ZiplineInfo {

    private final ZiplineType type;
    private final int color;

    public ZiplineInfo(ZiplineType type, int color) {
        this.color = color;
        this.type = type;
    }

    public int getColor() {
        return color;
    }

    public ZiplineType getType() {
        return type;
    }

    public NBTBase save() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("color", color);
        tag.setByte("type", (byte) getType().ordinal());
        return tag;
    }

    /** 读档；NBT 缺失或形状不对时回落到默认（松 + 默认色 ✓）✓ */
    public static ZiplineInfo load(@Nullable NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound cTag = (NBTTagCompound) tag;
            int color = cTag.hasKey("color") ? cTag.getInteger("color") : ZiplineRopeItem.DEFAULT_COLOR;
            ZiplineType type = cTag.hasKey("type")
                    ? ZiplineType.values()[cTag.getByte("type") % ZiplineType.values().length]
                    : ZiplineType.LOOSE;
            return new ZiplineInfo(type, color);
        }
        return new ZiplineInfo(ZiplineType.LOOSE, ZiplineRopeItem.DEFAULT_COLOR);
    }
}
