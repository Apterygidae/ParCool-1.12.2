package com.alrex.parcool.common.block;

import com.alrex.parcool.ParCool;
import net.minecraft.util.ResourceLocation;

/**
 * 方块实体（TileEntity）的登记 ✓ —— 1.12.2 版。
 *
 * <h3>★★ 1.12.2 没有 {@code TileEntityType} ✗</h3>
 * <p>1.16 里方块实体要注册一个 {@code TileEntityType<T>} 对象 ✓，
 * 方块实体构造时把它传进 {@code super(...)} ✗，再靠它反查类型 ✓。</p>
 * <p><b>1.12.2 完全没有这套</b> ✗：</p>
 * <ul>
 *   <li>{@code TileEntity} <b>只有无参构造</b> ✓（本工程的
 *       {@code ZiplineHookTileEntity()} 已是无参 ✓）</li>
 *   <li>类型靠<b>「类 ↔ 名字」的全局登记</b> ✓ —— 即
 *       {@code GameRegistry.registerTileEntity(Class, ResourceLocation)} ✓</li>
 *   <li>方块那边靠 {@code Block#createNewTileEntity(World, int)} 直接 {@code new} ✓
 *       （本工程的 {@code ZiplineHookBlock} 已是这样 ✓）</li>
 * </ul>
 * <p>所以本类<b>只是一个名字常量 + 登记入口</b> ✓，没有「类型对象」✗。</p>
 *
 * <h3>⚠️ 登记时机</h3>
 * <p>{@code GameRegistry.registerTileEntity} <b>不是</b> {@code RegistryEvent} ✗
 * —— 它必须在 {@code preInit} 调 ✓（{@code RegistryEvent.Register<Block>} 之后 ✓，
 * 否则无从给方块实体挂类型 ✓）。</p>
 */
public class TileEntities {

    /** 方块实体的登记名 ✓（全局唯一 ✓）。 */
    public static final ResourceLocation ZIPLINE_HOOK_NAME =
            new ResourceLocation(ParCool.MOD_ID, "zipline_hook");

    private TileEntities() {
    }
}
