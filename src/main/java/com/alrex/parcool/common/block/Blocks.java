package com.alrex.parcool.common.block;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.zipline.IronZiplineHookBlock;
import com.alrex.parcool.common.block.zipline.WoodenZiplineHookBlock;
import com.alrex.parcool.common.item.ParCoolItemGroup;
import com.alrex.parcool.utilities.RegistryRef;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * ParCool 的方块登记 ✓ —— 1.12.2 版。
 *
 * <h3>★ 与上游的结构差异</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code DeferredRegister} + {@code RegistryObject}</td>
 *       <td><b>{@link RegistryRef} + {@code RegistryEvent.Register&lt;Block&gt;}</b> ✓
 *           （本工程既有机制 ✓，见 {@code SoundEvents}）</td></tr>
 *   <tr><td>{@code AbstractBlock.Properties.of(Material).strength(..).sound(..)}</td>
 *       <td><b>setter 链式调用</b> ✗ —— {@code setHardness/setResistance/setSoundType} ✓</td></tr>
 *   <tr><td>{@code .harvestTool(ToolType.AXE)}</td>
 *       <td>{@b {@code setHarvestLevel("axe", 0)}}</b> ✗（工具是<b>字符串</b> ✗）</td></tr>
 *   <tr><td>{@code .noCollission()}</td>
 *       <td>1.12.2 <b>没有这个 setter</b> ✗ → 由方块覆写
 *           {@code getCollisionBoundingBox} 返回 {@code NULL_AABB} ✓</td></tr>
 * </table>
 *
 * <h3>★ 方块与它对应的 ItemBlock 是<b>两次</b>注册 ✗</h3>
 * <p>1.12.2 里「方块在手上」靠 {@code ItemBlock} ✓，
 * 而它是<b>物品</b> ✓ → 必须注册进 {@code ITEMS} 表 ✗。
 * 所以本类的 {@link #registerAll} 造方块 ✓，
 * 对应的 ItemBlock 由 {@code common.item.Items#registerAll} 造 ✓
 * （两边靠 {@link #WOODEN_ZIPLINE_HOOK} / {@link #IRON_ZIPLINE_HOOK} 传递实例 ✓）。</p>
 *
 * <p><b>⚠️ 顺序</b>：Forge 保证 {@code Register&lt;Block&gt;} 早于 {@code Register&lt;Item&gt;} ✓，
 * 所以物品那边能安全地 {@code get()} 方块 ✓。</p>
 */
public class Blocks {

    public static final RegistryRef<Block> WOODEN_ZIPLINE_HOOK = RegistryRef.of();
    public static final RegistryRef<Block> IRON_ZIPLINE_HOOK = RegistryRef.of();

    /** 由 {@code RegistryHandler.onRegisterBlocks} 调用 ✓。 */
    public static void registerAll(IForgeRegistry<Block> registry) {
        if (WOODEN_ZIPLINE_HOOK.isPresent()) return;

        // ── 木制钩 ✓：木质手感，镐/斧可快速破坏 ✓
        // ⚠️ setSoundType 是 protected ✗ → 已在 WoodenZiplineHookBlock 构造器里设 ✓
        // ⚠️ 1.12.2 的 Block **没有** setUnlocalizedName ✗ →
        //    翻译键由 setRegistryName 推导 ✓（语言文件用 tile.parcool:wooden_zipline_hook.name ✓）
        ResourceLocation woodenName = new ResourceLocation(ParCool.MOD_ID, "wooden_zipline_hook");
        Block wooden = new WoodenZiplineHookBlock(Material.WOOD)
                .setHardness(1.0f)
                .setResistance(3.0f)
                .setCreativeTab(ParCoolItemGroup.INSTANCE);
        wooden.setHarvestLevel("axe", 0);
        wooden.setRegistryName(woodenName);
        registry.register(wooden);
        WOODEN_ZIPLINE_HOOK.set(wooden);

        // ── 铁钩 ✓：金属，需要镐 ✓；**不吃碰撞** ✗（见 IronZiplineHookBlock ✓）
        ResourceLocation ironName = new ResourceLocation(ParCool.MOD_ID, "iron_zipline_hook");
        Block iron = new IronZiplineHookBlock(Material.IRON)
                .setHardness(1.0f)
                .setResistance(3.0f)
                .setCreativeTab(ParCoolItemGroup.INSTANCE);
        iron.setHarvestLevel("pickaxe", 0);
        iron.setRegistryName(ironName);
        registry.register(iron);
        IRON_ZIPLINE_HOOK.set(iron);

        // 方块实体：1.12.2 用 GameRegistry 直接登记「类 ↔ 名字」✓
        // ⚠️ 必须在方块注册之后（登记表要能看到方块 ✓），preInit 期间完成 ✓
        GameRegistry.registerTileEntity(
                com.alrex.parcool.common.block.zipline.ZiplineHookTileEntity.class,
                TileEntities.ZIPLINE_HOOK_NAME);
    }

    private Blocks() {
    }
}
