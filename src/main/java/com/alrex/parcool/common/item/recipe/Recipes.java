package com.alrex.parcool.common.item.recipe;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.item.recipe.special.ZiplineRopeDyeRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * 本 mod 的配方登记 ✓ —— 1.12.2 版。
 *
 * <h3>🚨 1.12.2 与 1.16 的配方体系差别很大 ✗</h3>
 * <ul>
 *   <li><b>没有 {@code IRecipeSerializer} 这一层</b> ✗ ——
 *       1.16 里配方要「{@code IRecipe} 实现 + {@code SpecialRecipeSerializer} 序列化器」两件套 ✓；
 *       1.12.2 **只有前者** ✓，{@code IRecipe} 本身继承
 *       {@code IForgeRegistryEntry<IRecipe>} ✓，直接注册即可 ✓</li>
 *   <li>因此**没有 {@code DeferredRegister}** ✗，走 {@code RegistryEvent.Register<IRecipe>} ✓</li>
 *   <li>**不需要 JSON 文件** ✗ —— 1.12.2 允许纯代码注册的配方 ✓
 *       （原版自己的 {@code RecipesArmorDyes} 等也是如此 ✓）</li>
 * </ul>
 *
 * <h3>★ 为什么配方的 registryName 要显式给</h3>
 * <p>其他注册项（物品/方块）靠 {@code setRegistryName} 定名字 ✓，
 * 这里同理 ✓ —— 名字用于**配方书去重**与日志 ✓。</p>
 */
public class Recipes {

    /** 由 {@code RegistryHandler.onRegisterRecipes} 调用 ✓。 */
    public static void registerAll(IForgeRegistry<IRecipe> registry) {
        registry.register(new ZiplineRopeDyeRecipe(
                new ResourceLocation(ParCool.MOD_ID, "zipline_rope_dye")));
    }

    private Recipes() {
    }
}
