package com.alrex.parcool.api;

import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;

/**
 * ParCool 的自定义实体属性（1.12.2 版）。
 *
 * <p><b>⚠️ 与 1.16.5 的本质差异：1.12.2 没有属性注册表。</b></p>
 *
 * <p>上游用 {@code DeferredRegister.create(Attribute.class, MOD_ID)} 注册到
 * {@code ForgeRegistries.ATTRIBUTES}。经查 1.12.2 反编译源码，
 * {@code ForgeRegistries} <b>根本没有 ATTRIBUTES 字段</b>
 * （只有 BLOCKS / ITEMS / POTIONS / BIOMES / SOUND_EVENTS / POTION_TYPES / ENCHANTMENTS /
 * VILLAGER_PROFESSIONS / ENTITIES / RECIPES / DATA_SERIALIZERS），
 * 因为 1.12.2 的 {@code IAttribute} 不实现 {@code IForgeRegistryEntry}。</p>
 *
 * <p>因此这里采用 <b>Forge 官方在 1.12.2 里加自定义属性的同款做法</b>
 * —— 参见反编译源码 {@code net/minecraft/entity/player/EntityPlayer.java:103}，
 * Forge 给玩家加 {@code REACH_DISTANCE} 就是：</p>
 *
 * <pre>
 * public static final IAttribute REACH_DISTANCE =
 *     new RangedAttribute(null, "generic.reachDistance", 5.0D, 0.0D, 1024.0D).setShouldWatch(true);
 * // 并在 EntityPlayer#applyEntityAttributes 里：
 * this.getAttributeMap().registerAttribute(REACH_DISTANCE);
 * </pre>
 *
 * <p>所以本类只负责<b>创建</b>属性对象；把它挂到玩家身上由
 * {@link com.alrex.parcool.common.handlers.AddAttributesHandler} 在
 * {@code EntityConstructing} 事件里完成。</p>
 *
 * <p>{@code setShouldWatch(true)} 是必需的：它让属性值参与实体数据同步，
 * 否则客户端读到的永远是默认值。上游的 {@code setSyncable(true)} 是同一个作用。</p>
 */
public class Attributes {

    public static final IAttribute MAX_STAMINA = create("max_stamina", "parcool.max_stamina", 2000.0D, 10.0D, 10000.0D);

    public static final IAttribute STAMINA_RECOVERY = create("stamina_recovery", "parcool.stamina_recovery", 20.0D, 0.0D, 10000.0D);

    private Attributes() {
    }

    /**
     * @param unlocalizedName 1.12.2 的属性名是「无本地化」的英文名，惯例用小写点分（如 {@code generic.reachDistance}）
     */
    private static IAttribute create(String registryName, String unlocalizedName, double defaultValue, double min, double max) {
        // 注意：setShouldWatch 返回 BaseAttribute（不是 IAttribute 也不是 RangedAttribute），
        // 所以这里不能继续链式调用；返回值赋给 IAttribute 字段是合法的（BaseAttribute implements IAttribute）。
        return new RangedAttribute(null, unlocalizedName, defaultValue, min, max).setShouldWatch(true);
    }
}
