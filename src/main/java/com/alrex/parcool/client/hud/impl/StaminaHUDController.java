package com.alrex.parcool.client.hud.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.client.gui.ParCoolHUDEvent;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.capability.stamina.ParCoolStamina;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 体力 HUD 的**调度器** ✓ —— 决定这一帧画哪一种 ✓。
 *
 * <h3>★ 绘制的「闸门」顺序（照搬上游 ✓，缺一步都会出问题）</h3>
 * <ol>
 *   <li>有本地玩家吗 ✓（否则单机主菜单会 NPE ✓）</li>
 *   <li>ParCool 总开关（{@code parcool_activation} ✓）</li>
 *   <li><b>只在 {@code EXPERIENCE} 这个 overlay 层画</b> ✓ ——
 *       它是「饥饿条之后」的时机 ✓，这样 {@code GuiIngameForge.right_height} 才已就绪 ✓</li>
 *   <li>有 {@code Parkourability} 吗 ✓</li>
 *   <li><b>体力无限且配置要求隐藏 → 不画</b> ✗（⚠️ <b>创造模式会命中这一条</b> ✓）</li>
 *   <li>有 {@code IStamina} 吗 ✓ 且**必须是 ParCool 自家的实现** ✓
 *       （{@code HungerStamina} 用饥饿条代替体力条 ✓，不该再画一个 ✓）</li>
 *   <li>发 {@link ParCoolHUDEvent.RenderEvent} ✓ —— 被取消就不画 ✓</li>
 * </ol>
 *
 * <h3>🔍 内置诊断（排查"为什么看不到 HUD"用 ✓）</h3>
 * <p>在**每一个** {@code return} 前调 {@link #trace} ✓ ——
 * 它只在**原因发生变化**时才打一条日志 ✓，于是「看不到 HUD」时，
 * 日志会**直接指出卡在哪一步** ✓，不必再猜 ✗。</p>
 *
 * <h3>🔴★ 「上一次的原因」必须**按调用点分开记**（2026-10-12 修过一个刷屏 bug）</h3>
 * <p>原先只有一个共享的 {@code lastTrace} ✗，而 {@code trace()} 被
 * <b>两条频率不同的循环路径</b>调用：{@link #onTick}（每 tick 一次 ✓）、
 * {@link #render}（每帧一次 ✓）。创造模式下两条都在提前返回 → 两个原因<b>互相覆盖</b> ✗：</p>
 * <pre>
 *   onTick 写 A → render 写 B（A≠B，打）→ 下一 tick 又是 A（B≠A，打）→ 下一帧又是 B（打）…
 * </pre>
 * <p>→ <b>「只在变化时打一条」彻底失效 ✗，变成每 tick 两条</b> ✓
 * （症状：创造/旁观模式下 {@code [ParCool] StaminaHUD: tick skipped: creative mode …} 与
 * {@code … render: hidden because stamina is infinite …} 交替刷屏 ✓）。</p>
 * <p>⭐ <b>通用教训</b>：<b>去重/限流的「键」必须和被限流的事物的「粒度」对齐</b> ✗ ——
 * 一个共享键去重多个独立调用点，只要它们的原因不同，就会互相把对方的记录挤掉 ✓。</p>
 * <p>所以现在按 {@link #SITE_TICK} / {@link #SITE_RENDER} <b>分别</b>记上一次的原因 ✓ →
 * 每个调用点各自只在「自己这一路的原因变化」时打一条 ✓
 * （正常游玩共 2 条：{@code [tick]: running} + {@code [render]: rendering: Light} ✓；
 * 进创造模式再多 2 条，然后彻底安静 ✓）。</p>
 *
 * <h3>⚠️ 已知的"看着像 bug"的两种情况 ✗</h3>
 * <ul>
 *   <li><b>创造模式</b> ✗ —— {@code onTick} 与各 HUD 的 {@code render} 都会提前返回 ✓
 *       → 完全看不到体力条 ✓（上游同此设计 ✓：创造模式不需要关心体力 ✓）</li>
 *   <li><b>体力无限 + {@code hide_hud_if_stamina_infinite=true}</b> ✗ ——
 *       创造/旁观时 {@code isStaminaInfinite()} 返回 true ✓ → 隐藏 ✓</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class StaminaHUDController {

    /** 诊断站点：每 tick 一次的那条路 ✓。 */
    private static final String SITE_TICK = "tick";

    /** 诊断站点：每帧一次的那条路 ✓。 */
    private static final String SITE_RENDER = "render";

    /**
     * 诊断：<b>每个站点各自的</b>「上一次的原因」✓ —— ⚠️ 不能共用一个字段 ✗（见类注释 ★）。
     *
     * <p>只在客户端主线程读写 ✓，无需同步 ✓。</p>
     */
    private static final java.util.Map<String, String> lastTraceBySite = new java.util.HashMap<>(4);

    private final LightStaminaHUD lightStaminaHUD;
    private final StaminaHUD staminaHUD;

    public StaminaHUDController() {
        this.lightStaminaHUD = new LightStaminaHUD();
        this.staminaHUD = new StaminaHUD();
    }

    /** 只在「**该站点**的原因变化」时打一条 ✓（避免刷屏 ✗）。 */
    private static void trace(String site, String reason) {
        if (reason.equals(lastTraceBySite.get(site))) return;
        lastTraceBySite.put(site, reason);
        ParCool.LOGGER.info("[ParCool] StaminaHUD[{}]: {}", site, reason);
    }

    public void onTick(TickEvent.ClientTickEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            trace(SITE_TICK, "skipped: no local player");
            return;
        }
        if (player.isCreative()) {
            // ⚠️ 创造模式整个 HUD 都不跑 ✓（上游同此 ✓）
            trace(SITE_TICK, "skipped: creative mode (HUD is disabled in creative)");
            return;
        }

        lightStaminaHUD.onTick(event, player);
        staminaHUD.onTick(event, player);

        IStamina stamina = IStamina.get(player);
        if (stamina == null) {
            trace(SITE_TICK, "stamina capability is null");
            return;
        }
        // ★ 把「本 tick 的值」存为「上一 tick 的值」✓ —— HUD 的插值与变化检测都靠它 ✓
        stamina.updateOldValue();

        // ✅ 顺利跑完也要报一声 ✓ —— 否则「上一次的原因」会永远停在某个提前返回上 ✗，
        //    导致「离开创造模式后再进创造模式」不再打印（状态机缺一个 OK 态 ✓）
        trace(SITE_TICK, "running normally");
    }

    public void render(RenderGameOverlayEvent.Post event) {
        // ⚠️ 这个 overlay 层每帧有很多种类型 ✓ —— 先过滤，且**不打日志** ✗（会刷屏 ✓）
        if (event.getType() != RenderGameOverlayEvent.ElementType.EXPERIENCE) return;

        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            trace(SITE_RENDER, "no local player");
            return;
        }
        if (!ParCoolConfig.Client.Booleans.ParCoolIsActive.get()) {
            trace(SITE_RENDER, "ParCool is disabled (parcool_activation=false)");
            return;
        }

        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) {
            trace(SITE_RENDER, "Parkourability capability is null");
            return;
        }

        // 体力无限时可选隐藏 ✓ —— ⚠️ 创造/旁观会命中这里 ✓
        boolean infinite = parkourability.getActionInfo()
                .isStaminaInfinite(player.isCreative() || player.isSpectator());
        if (ParCoolConfig.Client.Booleans.HideStaminaHUDWhenStaminaIsInfinite.get() && infinite) {
            trace(SITE_RENDER, "hidden because stamina is infinite "
                    + "(hide_hud_if_stamina_infinite=true; creative/spectator or infinite_stamina on)");
            return;
        }

        IStamina stamina = IStamina.get(player);
        if (stamina == null) {
            trace(SITE_RENDER, "IStamina capability is null");
            return;
        }
        // 用饥饿条当体力条时，不该再画 ParCool 的体力条 ✓
        if (!(stamina instanceof ParCoolStamina)) {
            trace(SITE_RENDER, "stamina implementation is " + stamina.getClass().getSimpleName()
                    + " (set used_stamina=Default to get ParCool's own bar)");
            return;
        }

        // 对外事件：任何 mod 都能取消这一帧的 HUD ✓
        if (MinecraftForge.EVENT_BUS.post(new ParCoolHUDEvent.RenderEvent(event))) {
            trace(SITE_RENDER, "cancelled by another mod (ParCoolHUDEvent.RenderEvent)");
            return;
        }

        HUDType type = ParCoolConfig.Client.getStaminaHUDType();
        switch (type) {
            case Light:
                lightStaminaHUD.render(event, parkourability, stamina);
                trace(SITE_RENDER, "rendering: Light");
                break;
            case Normal:
                staminaHUD.render(event, parkourability, stamina);
                trace(SITE_RENDER, "rendering: Normal");
                break;
            case Hide:
            default:
                // ⚠️ 「Hide」是**配置选出来的** ✓ 不是 bug ✓
                trace(SITE_RENDER, "style is Hide (stamina_hud_type=Hide)");
                break;
        }
    }
}
