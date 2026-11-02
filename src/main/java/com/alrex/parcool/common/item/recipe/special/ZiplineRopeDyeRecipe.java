package com.alrex.parcool.common.item.recipe.special;

import com.alrex.parcool.common.item.Items;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.oredict.DyeUtils;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 滑索绳的**染色配方** ✓ —— 1.12.2 版。
 *
 * <p>工作台里放 <b>1 根滑索绳 + N 个染料</b>（其余格留空 ✓）→ 得到一根染色的绳 ✓。
 * 颜色是**所有染料与绳子原有色的平均值** ✓（与上游一致 ✓）。</p>
 *
 * <h3>🚨 1.12.2 与 1.16 的配方体系完全不同 ✗</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code SpecialRecipe} 基类</td>
 *       <td><b>没有</b> ✗ → 直接实现 {@link IRecipe} ✓</td></tr>
 *   <tr><td>{@code SpecialRecipeSerializer}</td>
 *       <td><b>没有 {@code IRecipeSerializer} 这一层</b> ✗ →
 *           配方对象**本身**就是注册项 ✓（{@code IRecipe extends IForgeRegistryEntry} ✓）</td></tr>
 *   <tr><td>{@code CraftingInventory}</td><td>{@link InventoryCrafting} ✓</td></tr>
 *   <tr><td>{@code DyeItem} + {@code getDyeColor().getColorValue()}</td>
 *       <td><b>{@code DyeUtils}</b> ✗（Forge 矿辞工具 ✓，支持矿辞染料 ✓）→
 *           {@code DyeUtils.colorFromStack(stack).get().getColorComponentValues()} ✓</td></tr>
 *   <tr><td>{@code stack.isEmpty()}</td><td>{@code stack.isEmpty()} ✓ 同名 ✓</td></tr>
 * </table>
 *
 * <h3>★ 注册方式</h3>
 * <p>原版的特殊配方由 {@code CraftingManager} 在静态块里 {@code register("armordye", ...)} ✓ ——
 * 那是**内部 API** ✗，mod 用不了 ✓。
 * mod 走 {@code RegistryEvent.Register<IRecipe>} ✓
 * （见 {@code RegistryHandler.onRegisterRecipes} ✓）。</p>
 *
 * <p><b>⭐ 实现参考</b>：原版的 {@code RecipesArmorDyes}（皮革盔甲染色 ✓）——
 * 同类的「物品 + 染料」配方 ✓，1.12.2 里现成的范式 ✓。</p>
 */
public class ZiplineRopeDyeRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    public ZiplineRopeDyeRecipe() {
    }

    public ZiplineRopeDyeRecipe(ResourceLocation name) {
        setRegistryName(name);
    }

    /** 配方形状检查 ✓：**恰好一根绳** + **至少一个染料** + **其余全空** ✓。 */
    @Override
    public boolean matches(@Nonnull InventoryCrafting inv, @Nonnull World worldIn) {
        boolean ropeFound = false;
        boolean dyeFound = false;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof ZiplineRopeItem) {
                // 两根绳不行 ✓（上游同此 ✓）
                if (ropeFound) return false;
                ropeFound = true;
            } else if (DyeUtils.isDye(stack)) {
                dyeFound = true;
            } else {
                // 出现任何第三种东西 → 不匹配 ✓
                return false;
            }
        }
        return ropeFound && dyeFound;
    }

    /** 把染料颜色（以及绳子原有的自定义色）**取平均** ✓。 */
    @Nonnull
    @Override
    public ItemStack getCraftingResult(@Nonnull InventoryCrafting inv) {
        ItemStack rope = ItemStack.EMPTY;
        List<ItemStack> dyes = new ArrayList<>();

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof ZiplineRopeItem) {
                rope = stack;
            } else if (DyeUtils.isDye(stack)) {
                dyes.add(stack);
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (rope.isEmpty() || dyes.isEmpty()) return ItemStack.EMPTY;
        // ⚠️ 用 isPresent() 守卫 ✗ —— orElse(null) 会让 new ItemStack((Item) null) 崩 ✗
        if (!Items.ZIPLINE_ROPE.isPresent()) return ItemStack.EMPTY;

        ItemStack result = new ItemStack(Items.ZIPLINE_ROPE.get());
        if (result.isEmpty()) return ItemStack.EMPTY;
        // 保留原 NBT ✓（缠绕信息等 ✓），只改颜色 ✓ —— 上游同此 ✓
        if (rope.getTagCompound() != null) {
            result.setTagCompound(rope.getTagCompound().copy());
        }

        int red = 0, green = 0, blue = 0;
        int divisor = dyes.size();

        for (ItemStack dye : dyes) {
            // ⚠️ 1.12.2 用 DyeUtils 取颜色分量 ✗ —— 返回 float[3]（0..1 ✓）
            float[] rgb = DyeUtils.colorFromStack(dye).get().getColorComponentValues();
            red += (int) (rgb[0] * 255f);
            green += (int) (rgb[1] * 255f);
            blue += (int) (rgb[2] * 255f);
        }

        // 已有的自定义色也参与平均 ✓（反复染色会逐渐变淡，上游如此 ✓）
        if (ZiplineRopeItem.hasCustomColor(result)) {
            divisor++;
            int color = ZiplineRopeItem.getColor(result);
            red += color >> 16 & 0xFF;
            green += color >> 8 & 0xFF;
            blue += color & 0xFF;
        }

        red = MathHelper.clamp(red / divisor, 0, 0xFF);
        green = MathHelper.clamp(green / divisor, 0, 0xFF);
        blue = MathHelper.clamp(blue / divisor, 0, 0xFF);

        ZiplineRopeItem.setColor(result, red << 16 | green << 8 | blue);
        return result;
    }

    /** 至少 2 格 ✓（1 绳 + 1 染料 ✓）。 */
    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    /**
     * 展示用产物 ✓ —— 本配方结果**取决于输入**（动态 ✗），所以返回空栈 ✓
     * （与上游一致 ✓，也让配方书不会把它当成固定配方 ✓）。
     */
    @Nonnull
    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public NonNullList<ItemStack> getRemainingItems(@Nonnull InventoryCrafting inv) {
        return ForgeHooks.defaultRecipeGetRemainingItems(inv);
    }
}
