package com.alrex.parcool.common.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * ParCool 的创造模式物品栏 ✓ —— 1.12.2 版。
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code ItemGroup}</td><td>{@link CreativeTabs} ✓</td></tr>
 *   <tr><td>{@code super("ParCool")}（名字即翻译键 ✗）</td>
 *       <td>{@code super(label)} ✓ ——
 *           label 是**翻译键** ✗，需在语言文件里给 {@code itemGroup.ParCool} ✓</td></tr>
 *   <tr><td>{@code ItemStack makeIcon()}</td>
 *       <td><b>{@code ItemStack getTabIconItem()}</b> ✗（方法名不同 ✓）</td></tr>
 * </table>
 *
 * <p><b>⚠️ 图标项是「手册」</b> ✓（{@code Items.PARCOOL_GUIDE} ✓）——
 * 手册本体要到 P6 才做 ✓，所以这里<b>必须判空</b> ✗，
 * 否则物品栏在手册未注册时会 NPE 崩 ✗。</p>
 */
public class ParCoolItemGroup extends CreativeTabs {

    public static final ParCoolItemGroup INSTANCE = new ParCoolItemGroup();

    public ParCoolItemGroup() {
        // label 同时是翻译键 ✓ —— 语言文件里要有 itemGroup.ParCool ✓
        super("ParCool");
    }

    @Override
    public ItemStack createIcon() {
        // ⚠️ 1.12.2 的抽象方法叫 createIcon ✗（1.16 是 makeIcon ✗，
        //    中间某版本叫 getTabIconItem ✗ —— 都不是本工程映射的名字 ✓）
        // ⚠️ 手册尚未移植（P6 ✓）→ 空的话给个空栈 ✓，不能 NPE ✗
        if (Items.PARCOOL_GUIDE.isPresent()) {
            return new ItemStack(Items.PARCOOL_GUIDE.get());
        }
        if (Items.ZIPLINE_ROPE.isPresent()) {
            return new ItemStack(Items.ZIPLINE_ROPE.get());
        }
        return ItemStack.EMPTY;
    }

    /**
     * ★ 在创造栏里补出**所有染色变体** ✓ —— 共 17 种（默认色 + 16 种染料色 ✓）。
     *
     * <h3>为什么需要覆写</h3>
     * <p>默认实现只会列出「注册过的物品」✓ —— 而本 mod **只注册了一根绳索** ✗，
     * 颜色是**存在 NBT 里**的 ✓（{@code ZiplineRopeItem.setColor} ✓）。
     * 所以必须手工把每种颜色的成品栈塞进创造栏 ✓，
     * 否则玩家只看到一个「未染色」的绳索 ✗（上游是 17 个 ✓）。</p>
     *
     * <h3>⚠️ 必须先调 super ✗</h3>
     * <p>它负责把所有已注册物品都加进来 ✓ —— 漏掉的话整个 ParCool 页会空掉 ✗。</p>
     *
     * <p><b>⚠️ 不包含「已染色再染色」的混合色</b> ✗ —— 那是无限的 ✓，
     * 只能靠工作台现场合成 ✓（与上游一致 ✓）。</p>
     */
    @Override
    public void displayAllRelevantItems(@Nonnull net.minecraft.util.NonNullList<ItemStack> items) {
        super.displayAllRelevantItems(items);

        if (!Items.ZIPLINE_ROPE.isPresent()) return;
        // 默认色那个已经在 super 里 ✓ → 这里只补 16 种染料色 ✓（合计 17 ✓）
        for (net.minecraft.item.EnumDyeColor dye : net.minecraft.item.EnumDyeColor.values()) {
            ItemStack stack = new ItemStack(Items.ZIPLINE_ROPE.get());
            // ⚠️ EnumDyeColor.getColorValue() 返回 0xRRGGBB ✓，正是 setColor 要的格式 ✓
            com.alrex.parcool.common.item.zipline.ZiplineRopeItem.setColor(stack, dye.getColorValue());
            items.add(stack);
        }
    }
}
