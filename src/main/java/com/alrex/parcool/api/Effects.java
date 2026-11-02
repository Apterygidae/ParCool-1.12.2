package com.alrex.parcool.api;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.potion.effects.InexhaustibleEffect;
import com.alrex.parcool.utilities.RegistryRef;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * ParCool 的状态效果注册（1.12.2 版）。
 *
 * <p>对应 1.16.5 的 {@code api/Effects.java}，注册表从
 * {@code ForgeRegistries.POTIONS}（两个版本同名，1.12.2 侧已核对存在）取。</p>
 *
 * <p>⚠️ 命名陷阱：1.16.5 里 {@code ForgeRegistries.POTIONS} 存的是
 * {@code Effect}（状态效果），{@code POTION_TYPES} 存的是药水组合。
 * 1.12.2 的对应关系是 {@code ForgeRegistries.POTIONS} → {@link Potion}（效果）、
 * {@code ForgeRegistries.POTION_TYPES} → 药水组合（见 {@code common/potion/Potions.java}）。
 * 名字一样但语义要分清，否则会把效果注册成药水组合。</p>
 */
public class Effects {

    /**
     * 效果实例本身。
     *
     * <p><b>为什么把它单独暴露出来：</b>{@code Potions} 需要用它构造 {@code PotionEffect}，
     * 而 {@code PotionEffect} 只需要 {@code Potion} 的<b>实例引用</b>，不需要它已经进注册表。
     * 共享同一个实例可以彻底避免「POTIONS 事件必须先于 POTION_TYPES 事件触发」这种
     * 跨注册表顺序依赖 —— 那种依赖在 Forge 里没有明确保证，是很脆的写法。</p>
     */
    public static final InexhaustibleEffect INEXHAUSTIBLE_INSTANCE = new InexhaustibleEffect();

    /** 已注册的效果。类型是 {@link Potion}（1.12.2 的「效果」类）。 */
    public static final RegistryRef<Potion> INEXHAUSTIBLE = RegistryRef.of();

    private Effects() {
    }

    public static void registerAll(IForgeRegistry<Potion> registry) {
        INEXHAUSTIBLE.set(register(registry, INEXHAUSTIBLE_INSTANCE, "inexhaustible"));
    }

    private static Potion register(IForgeRegistry<Potion> registry, Potion potion, String name) {
        ResourceLocation location = new ResourceLocation(ParCool.MOD_ID, name);
        potion.setRegistryName(location);
        registry.register(potion);
        return potion;
    }
}
