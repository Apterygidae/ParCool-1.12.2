package com.alrex.parcool.common.potion.effects;

import net.minecraft.potion.Potion;

/**
 * 「体力无限」效果（1.12.2 版）。
 *
 * <p>对应 1.16.5 的 {@code InexhaustibleEffect extends net.minecraft.potion.Effect}。
 * 1.12.2 里「效果」这个概念的类名是 {@link Potion}（{@code Effect} 是 1.13+ 才改的名）。</p>
 *
 * <p>注意事项：</p>
 * <ul>
 *   <li>1.12.2 的 {@code Potion(boolean isBadEffectIn, int liquidColorIn)} 是 <b>protected</b>
 *       （已在反编译源码 {@code Potion.java:63} 核对），所以必须子类化才能创建 —— 与上游写法一致</li>
 *   <li>{@code setPotionName(...)} 设置的字符串<b>就是本地化键本身</b>：
 *       {@code PotionEffect.getEffectName()} 直接返回 {@code potion.getName()}，不会再拼前缀
 *       （反编译源码 {@code PotionEffect.java:156-159}）。
 *       所以 P6 的 lang 文件里必须有同名条目，否则显示成原始 key</li>
 *   <li>1.12.2 的 {@code setIconIndex} 决定状态栏图标，不设则用默认</li>
 * </ul>
 */
public class InexhaustibleEffect extends Potion {

    public static final int COLOR = 0xEEEE11;

    public static final String LOCALIZATION_KEY = "effect.parcool.inexhaustible";

    public InexhaustibleEffect() {
        super(false, COLOR);
        setPotionName(LOCALIZATION_KEY);
        setBeneficial();
    }

    /**
     * 上游没有覆写 {@code performEffect}，也就是说这个效果<b>不靠 tick 生效</b> ——
     * 它是被 ParCool 的体力逻辑主动查询「玩家身上有没有这个效果」来实现无限体力的。
     *
     * <p>⚠️ 反过来说：1.12.2 的 {@code Potion#isReady(int duration, int amplifier)} 默认返回 false，
     * 所以本效果不会触发任何 tick 逻辑，这正是我们要的。</p>
     */
}
