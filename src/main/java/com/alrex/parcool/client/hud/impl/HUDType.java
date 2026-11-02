package com.alrex.parcool.client.hud.impl;

/**
 * 体力 HUD 的显示样式 ✓ —— 对应配置项 {@code stamina_hud_type}。
 *
 * <ul>
 *   <li>{@code Normal} —— 一条**横条**（见 {@link StaminaHUD} ✓）</li>
 *   <li>{@code Light} —— 10 个**小图标**，紧贴饥饿条上方（见 {@link LightStaminaHUD} ✓，**默认** ✓）</li>
 *   <li>{@code Hide} —— 不显示 ✓</li>
 * </ul>
 */
public enum HUDType {
    Normal, Light, Hide
}
