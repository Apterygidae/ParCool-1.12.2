package com.alrex.parcool.common.registries;

import com.alrex.parcool.api.Effects;
import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.common.potion.Potions;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionType;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 1.12.2 的注册表事件处理（替代上游的 {@code DeferredRegister}）。
 *
 * <p>上游每个注册类各自持有 {@code DeferredRegister} 并在 mod 构造器里
 * {@code X.register(modEventBus)}；1.12.2 没有延迟注册，改为集中订阅
 * {@code RegistryEvent.Register<T>}。集中放一处的好处是<b>注册顺序显式可见</b>。</p>
 *
 * <h3>已接入</h3>
 * <ul>
 *   <li>{@code SoundEvent} — 19 个音效</li>
 *   <li>{@code Potion} — 1 个状态效果（1.12.2 的「效果」就叫 Potion）</li>
 *   <li>{@code PotionType} — 2 个药水组合</li>
 * </ul>
 *
 * <h3>待接入（依赖内容类，见 P1-B / P6）</h3>
 * <ul>
 *   <li>{@code Block} — 滑索钩子 ×2（需要 {@code ZiplineHookBlock} 及其子类）</li>
 *   <li>{@code Item} — 手册 + 滑索相关（需要 {@code ZiplineRopeItem} / {@code ParCoolItemGroup}）</li>
 *   <li>{@code EntityEntry} — 滑索绳实体（1.12.2 用
 *       {@code EntityRegistry.registerModEntity}，不是 RegistryEvent）</li>
 *   <li>{@code IRecipe} — 滑索绳染色配方</li>
 * </ul>
 *
 * <h3>注意：属性没有注册表</h3>
 * <p>{@code ForgeRegistries} 在 1.12.2 <b>没有 ATTRIBUTES 字段</b>，
 * 所以 {@link com.alrex.parcool.api.Attributes} 不经由这里，
 * 而是由 {@link com.alrex.parcool.common.handlers.AddAttributesHandler} 在
 * {@code EntityConstructing} 时挂到玩家身上。</p>
 */
public class RegistryHandler {

    @SubscribeEvent
    public static void onRegisterSounds(RegistryEvent.Register<SoundEvent> event) {
        SoundEvents.registerAll(event.getRegistry());
    }

    // ── 方块 / 物品（P6 起接入：滑索钩 ×2 + 滑索绳 ✓）────────────────────
    // ⚠️ Forge 保证 Register<Block> 早于 Register<Item> ✓，
    //    所以物品那边能安全地拿 Blocks.WOODEN_ZIPLINE_HOOK.get() ✓

    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<net.minecraft.block.Block> event) {
        com.alrex.parcool.common.block.Blocks.registerAll(event.getRegistry());
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<net.minecraft.item.Item> event) {
        com.alrex.parcool.common.item.Items.registerAll(event.getRegistry());
    }

    // ── 配方（P6 ✓）──────────────────────────────────────────────────────
    // ⚠️ 1.12.2 的配方【没有】IRecipeSerializer 这一层 ✗ ——
    //    配方对象本身就是注册项 ✓（IRecipe extends IForgeRegistryEntry ✓）
    @SubscribeEvent
    public static void onRegisterRecipes(RegistryEvent.Register<net.minecraft.item.crafting.IRecipe> event) {
        com.alrex.parcool.common.item.recipe.Recipes.registerAll(event.getRegistry());
    }

    @SubscribeEvent
    public static void onRegisterPotions(RegistryEvent.Register<Potion> event) {
        Effects.registerAll(event.getRegistry());
    }

    @SubscribeEvent
    public static void onRegisterPotionTypes(RegistryEvent.Register<PotionType> event) {
        Potions.registerAll(event.getRegistry());
    }
}
