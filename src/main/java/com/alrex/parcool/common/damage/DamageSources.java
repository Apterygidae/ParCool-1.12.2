package com.alrex.parcool.common.damage;

import net.minecraft.util.DamageSource;

/**
 * ParCool 自定义伤害源（1.12.2 版）。
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code new DamageSource("x").bypassArmor()}</td>
 *       <td>{@code new DamageSource("x").setDamageBypassesArmor()}（1.12.2 的方法名更长）</td></tr>
 * </table>
 *
 * <p><b>翻译键</b>：{@code parcool.wall_slide} 就是死亡消息的语言键前缀 ——
 * 上游没有为它提供 1.12.2 的 lang 条目，所以死亡消息会显示成
 * {@code death.attack.parcool.wall_slide}。<b>属已知的外观缺口</b>，
 * 补 lang 时一并加上即可（不影响功能）✓</p>
 */
public class DamageSources {

    private DamageSources() {
    }

    /** 滑墙擦伤（`WallSlide` 每 tick 扣 0.3 点）。 */
    public static final DamageSource WALL_SLIDE =
            new DamageSource("parcool.wall_slide").setDamageBypassesArmor();
}
