package com.alrex.parcool.common.potion;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.Effects;
import com.alrex.parcool.utilities.RegistryRef;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * ParCool 的药水（1.12.2 版）。
 *
 * <p><b>⚠️ 这是「同名不同义」的典型陷阱，上游文件叫 {@code Potions} 且注册的是
 * {@code net.minecraft.potion.Potion}，但在 1.12.2 必须改成 {@link PotionType}。</b></p>
 *
 * <table border="1">
 *   <tr><th></th><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>单个状态效果</td><td>{@code Effect} → {@code ForgeRegistries.POTIONS}</td>
 *       <td>{@link net.minecraft.potion.Potion} → {@code ForgeRegistries.POTIONS}</td></tr>
 *   <tr><td>效果组合（可酿造的药水）</td><td>{@code Potion} → {@code ForgeRegistries.POTION_TYPES}</td>
 *       <td>{@link PotionType} → {@code ForgeRegistries.POTION_TYPES}</td></tr>
 * </table>
 *
 * <p>也就是说：上游的 {@code Potions.POOR_ENERGY_DRINK}（一个含 3 个 {@code EffectInstance} 的
 * {@code Potion}）在 1.12.2 对应的类型是 {@code PotionType}。
 * 直接照抄成 {@code Potion} 会编译不过（{@code Potion} 不接受 vararg 效果），
 * 或者更糟——编译过了但语义变成「一个只带 1 个效果的状态效果」。</p>
 *
 * <p>上游的「酿造配方」在 {@code PotionRecipeRegistry} 里，1.12.2 要用
 * {@code BrewingRecipeRegistry}，属于 P6 范围。</p>
 */
public class Potions {

    /** 对应上游 {@code POOR_ENERGY_DRINK}：无限体力 3 分钟 + 饥饿 + 中毒。 */
    public static final RegistryRef<PotionType> POOR_ENERGY_DRINK = RegistryRef.of();

    /** 对应上游 {@code ENERGY_DRINK}：无限体力 7 分钟，无副作用。 */
    public static final RegistryRef<PotionType> ENERGY_DRINK = RegistryRef.of();

    private Potions() {
    }

    public static void registerAll(IForgeRegistry<PotionType> registry) {
        POOR_ENERGY_DRINK.set(register(registry, "poor_energy_drink", new PotionType(
                "poor_energy_drink",
                new PotionEffect(Effects.INEXHAUSTIBLE_INSTANCE, 3600),
                new PotionEffect(MobEffects.HUNGER, 100),
                new PotionEffect(MobEffects.POISON, 100)
        )));
        ENERGY_DRINK.set(register(registry, "energy_drink", new PotionType(
                "energy_drink",
                new PotionEffect(Effects.INEXHAUSTIBLE_INSTANCE, 8400)
        )));
    }

    private static PotionType register(IForgeRegistry<PotionType> registry, String name, PotionType type) {
        ResourceLocation location = new ResourceLocation(ParCool.MOD_ID, name);
        type.setRegistryName(location);
        registry.register(type);
        return type;
    }
}
