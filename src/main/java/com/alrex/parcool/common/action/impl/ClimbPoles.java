package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.common.action.StubAction;

/**
 * 「爬柱子」—— <b>⚠️ 上游 1.16.5 也是空壳，所以本类<b>不是</b>「待移植」，它已经等价了</b> ✓
 *
 * <p>核实结果（2026-10-01）：上游的 {@code ClimbPoles.canStart / canContinue}
 * 直接 {@code return false}、{@code getStaminaConsumeTiming} 返回 {@code None} ——
 * <b>三方法全空</b> ✓ 与本工程的 {@link StubAction} 行为完全一致 ✓</p>
 *
 * <h3>但「爬柱子」这个功能并没有完全缺席</h3>
 * <p>上游把它拆成两半：</p>
 * <ul>
 *   <li><b>动作类</b>（本文件）—— 空壳 ✗ <b>无需移植</b> ✓</li>
 *   <li><b>原版钩子</b> —— {@code mixin/common/LivingEntityMixin} 里覆写了
 *       {@code onClimbable()}，靠 {@code BlockTags.POLE_CLIMBABLE} 判定「这根柱子能不能爬」
 *       <b>这一半才是真功能</b>，也是「爬柱子」实际生效的地方 ✓</li>
 * </ul>
 * <p><b>→ 本类保持占位即可；真正待做的是那个 {@code onClimbable} 钩子 + 它用的标签</b>，
 * 而标签又依赖「1.12.2 没有方形标签系统」的替代方案（与 {@code HideInBlock} 的
 * {@code HIDE_ABLE} 是同一个设计问题）✓</p>
 *
 * <p>所以本类<b>从「待移植」清单移出</b>，改记为「上游即空壳、无需移植」✓
 * 待做的部分归到「原版钩子 + 标签替代方案」那条线 ✓</p>
 */
public class ClimbPoles extends StubAction {
}
