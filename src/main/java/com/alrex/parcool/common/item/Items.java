package com.alrex.parcool.common.item;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.Blocks;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import com.alrex.parcool.utilities.RegistryRef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * ParCool 的物品登记 ✓ —— 1.12.2 版。
 *
 * <h3>★ 这里要登记四样东西</h3>
 * <ol>
 *   <li><b>手册</b>（{@code parcool_guide}）✓ —— 本体是 P6 的内容 ✓，
 *       此处先注册一个**普通物品占位** ✗，好让创造标签页有图标 ✓</li>
 *   <li><b>木钩的 ItemBlock</b> ✓</li>
 *   <li><b>铁钩的 ItemBlock</b> ✓</li>
 *   <li><b>滑索绳</b>（{@link ZiplineRopeItem} ✓）—— 同时把
 *       {@link ZiplineRopeItem#INSTANCE} 填上 ✓（1.12.2 没有 supplier 式注册 ✗）</li>
 * </ol>
 *
 * <h3>★ ItemBlock 的 1.12.2 处理</h3>
 * <p>1.16 的 {@code new BlockItem(block, new Item.Properties().tab(g))} ✗ →
 * 1.12.2 是 {@code new ItemBlock(block)} + {@code setCreativeTab(g)} ✓。</p>
 * <p><b>⚠️ ItemBlock 必须与方块<b>同名</b></b> ✗ —— 否则 {@code getUnlocalizedName}
 * 与模型查找都会错位 ✗（1.12.2 靠名字把「方块 → 物品」关联起来 ✓）。</p>
 *
 * <h3>⚠️ 时序</h3>
 * <p>依赖 {@code RegistryEvent.Register<Block>} 已完成 ✓
 * （Forge 保证先 Block 后 Item ✓）—— 所以这里可以安全
 * {@code Blocks.WOODEN_ZIPLINE_HOOK.get()} ✓。</p>
 */
public class Items {

    public static final RegistryRef<Item> PARCOOL_GUIDE = RegistryRef.of();
    public static final RegistryRef<Item> WOODEN_ZIPLINE_HOOK = RegistryRef.of();
    public static final RegistryRef<Item> IRON_ZIPLINE_HOOK = RegistryRef.of();
    public static final RegistryRef<Item> ZIPLINE_ROPE = RegistryRef.of();

    private static Item guide = null;

    /** 由 {@code RegistryHandler.onRegisterItems} 调用 ✓。 */
    public static void registerAll(IForgeRegistry<Item> registry) {
        if (PARCOOL_GUIDE.isPresent()) return;

        // ① 手册占位 ✓（P6 再换成真正的 Patchouli 书 ✓）
        if (guide == null) {
            guide = new Item()
                    .setMaxStackSize(1)
                    .setCreativeTab(ParCoolItemGroup.INSTANCE);
            guide.setRegistryName(new ResourceLocation(ParCool.MOD_ID, "parcool_guide"));
            // ⚠️ 1.12.2 的映射把 setUnlocalizedName 改名为 **setTranslationKey** ✗
            //    （老教程里的 setUnlocalizedName 在本工程映射下不存在 ✓）
            guide.setTranslationKey(ParCool.MOD_ID + ".parcool_guide");
        }
        registry.register(guide);
        PARCOOL_GUIDE.set(guide);

        // ②③ 两个钩子的 ItemBlock ✓
        // ⚠️ ItemBlock 必须与方块同名 ✗（见类注释）
        // ⚠️★ 不要假设「Register<Block> 一定先于 Register<Item>」✗★
        //    2026-10-11 的崩溃证明：Forge 对各个 RegistryEvent 的先后**没有保证** ✗
        //    → 方块尚未就位时直接跳过 ✓，不抛异常 ✓
        if (Blocks.WOODEN_ZIPLINE_HOOK.isPresent()) {
            registerItemBlock(registry, WOODEN_ZIPLINE_HOOK,
                    Blocks.WOODEN_ZIPLINE_HOOK.get(), "wooden_zipline_hook");
        }
        if (Blocks.IRON_ZIPLINE_HOOK.isPresent()) {
            registerItemBlock(registry, IRON_ZIPLINE_HOOK,
                    Blocks.IRON_ZIPLINE_HOOK.get(), "iron_zipline_hook");
        }

        // ④ 滑索绳 ✓
        ZiplineRopeItem rope = new ZiplineRopeItem();
        rope.setMaxStackSize(1);
        rope.setCreativeTab(ParCoolItemGroup.INSTANCE);
        rope.setRegistryName(new ResourceLocation(ParCool.MOD_ID, "zipline_rope"));
        rope.setTranslationKey(ParCool.MOD_ID + ".zipline_rope");
        registry.register(rope);
        ZIPLINE_ROPE.set(rope);
        // 1.12.2 没有 supplier 式注册 ✗ → 静态单例要手动填 ✓（物品自己的注释里有说明 ✓）
        ZiplineRopeItem.INSTANCE = rope;
    }

    /** 由一个方块造出它的 ItemBlock 并登记 ✓（名字必须与方块一致 ✗）。 */
    private static void registerItemBlock(IForgeRegistry<Item> registry, RegistryRef<Item> ref,
                                          net.minecraft.block.Block block, String name) {
        ItemBlock itemBlock = new ItemBlock(block);
        itemBlock.setRegistryName(new ResourceLocation(ParCool.MOD_ID, name));
        // ⚠️ 与方块同名 ✗ —— 这样 1.12.2 才能把「方块 ↔ 物品」关联上 ✓
        //    （getTranslationKey 是 1.12.2 映射里的名字 ✗，不是 getUnlocalizedName ✓）
        itemBlock.setTranslationKey(block.getTranslationKey());
        itemBlock.setCreativeTab(ParCoolItemGroup.INSTANCE);
        registry.register(itemBlock);
        ref.set(itemBlock);
    }

    /** 供创造标签页用 ✓（手册还没注册时返回空栈 ✓，避免 NPE ✗）。 */
    public static ItemStack getGuideStack() {
        if (PARCOOL_GUIDE.isPresent()) return new ItemStack(PARCOOL_GUIDE.get());
        return ItemStack.EMPTY;
    }

    private Items() {
    }
}
