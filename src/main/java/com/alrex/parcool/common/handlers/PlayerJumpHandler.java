package com.alrex.parcool.common.handlers;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.action.impl.BreakfallReady;
import com.alrex.parcool.common.action.impl.ChargeJump;
import com.alrex.parcool.common.action.impl.Dive;
import com.alrex.parcool.common.action.impl.Flipping;
import com.alrex.parcool.common.action.impl.Roll;
import com.alrex.parcool.common.action.impl.Tap;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.network.StartBreakfallMessage;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 垂直运动的接线（1.12.2 版）—— 对应上游的 {@code PlayerJumpHandler}
 * 与 {@code PlayerDamageHandler} 里的落地分支。
 *
 * <h3>为什么需要这个类</h3>
 * <p>有些动作的钩子<b>不是</b>由 {@code ActionProcessor} 每 tick 驱动的，而是绑定在
 * 「跳跃」和「落地」这两个瞬间上：{@code ChargeJump.onJump} / {@code ChargeJump.onLand}。
 * 上游用事件处理器接线，1.12.2 有同名事件 → <b>不需要 mixin</b> ✓</p>
 *
 * <h3>1.12.2 的对应</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code LivingEvent.LivingJumpEvent}</td><td>同名存在 ✓（{@code LivingEvent.java:86}）</td></tr>
 *   <tr><td>{@code LivingFallEvent}（上游在 PlayerDamageHandler 里用）</td>
 *       <td>同名存在 ✓</td></tr>
 *   <tr><td>{@code event.getEntity()}</td><td>{@code event.getEntityLiving()}</td></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td><td>{@code ParCool.PROXY.isLocalPlayer(player)}</td></tr>
 * </table>
 *
 * <h3>⚠️ 本类目前只接了 {@code ChargeJump}</h3>
 * <p>上游的跳/落处理器还会转调 {@code Dive.onJump} 与 {@code Flipping.onJump} ——
 * 那两个动作仍是骨架，等移植时在这里各加一行即可 ✓</p>
 *
 * <p>同样地，上游的 {@code PlayerDamageHandler} 除了落地还负责
 * <b>摔落伤害减免</b>与 <b>HideInBlock 的落地保护</b> —— 那些属于别的动作，
 * 本类只取其中与 {@code ChargeJump} 相关的那一段（已在注释里标明来源）✓</p>
 */
public class PlayerJumpHandler {

    private PlayerJumpHandler() {
    }

    /** 跳跃瞬间：给需要它的动作一个钩子。 */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        parkourability.getAdditionalProperties().onJump();

        if (!ParCool.PROXY.isLocalPlayer(player)) return;
        IStamina stamina = IStamina.get(player);
        if (stamina == null) return;

        // Dive / Flipping 都已移植 → 两行都放开 ✓
        // 缺了它们：「跳起后俯冲」「点移动+跳触发空翻（默认模式）」永远不会触发 ✗
        parkourability.get(Dive.class).onJump(player, parkourability, stamina);
        parkourability.get(Flipping.class).onJump(player, parkourability, stamina);
        parkourability.get(ChargeJump.class).onJump(player, parkourability, stamina);
    }

    /**
     * 落地瞬间。
     *
     * <p>对应上游 {@code PlayerDamageHandler.onFall}，但<b>只取了两段</b>：</p>
     * <ol>
     *   <li><b>服务端侧</b>（{@link EntityPlayerMP}）：判定「该不该落地缓冲」→ 发
     *       {@link StartBreakfallMessage} 通知那个玩家的客户端。判定需要<b>摔落距离</b>，
     *       那是服务端的权威数据，所以这一步必须在服务端做 ✓</li>
     *   <li><b>客户端侧</b>（本地玩家）：转调 {@code ChargeJump.onLand} ✓</li>
     * </ol>
     *
     * <p><b>TODO（尚未做的部分）</b>：上游的 {@code PlayerDamageHandler.onFall} 还负责
     * <b>摔落伤害的减免/取消</b>（按 {@code DamageCompleteRemovableHeightBreakfall} 等 6 个配置项）
     * 与 <b>{@code HideInBlock} 的落地保护</b>。这两件事属于「伤害处理」这个独立关注点，
     * 等实现 {@code HideInBlock} / 整理伤害处理时一起做 ✓</p>
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        // ── ① 服务端：落地缓冲的判定与通知 ──────────────────────────────────
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
            Parkourability parkourability = Parkourability.get(player);
            if (parkourability == null) return;

            if (parkourability.get(BreakfallReady.class).isDoing()
                    && (parkourability.getActionInfo().can(Tap.class)
                    || parkourability.getActionInfo().can(Roll.class))
            ) {
                // 「恰好时机」= 按下缓冲键后的前 N tick（N 取 client/server 里更严格的那个）
                boolean justTime = parkourability.get(BreakfallReady.class).getDoingTick()
                        < parkourability.getLimitedValue(
                        ParCoolConfig.Client.Integers.JustTimeBreakfallTick,
                        ParCoolConfig.Server.Integers.MaxJustTimeBreakfallTick
                );
                if (event.getDistance() > parkourability.getLimitedValue(
                        ParCoolConfig.Client.Doubles.LowestFallDistanceForBreakfall,
                        ParCoolConfig.Server.Doubles.MinLowestFallDistanceForBreakfall
                )) {
                    StartBreakfallMessage.send(player, justTime);
                }
            }
            return;
        }

        // ── ② 客户端：本地玩家的落地钩子（ChargeJump 的「落地即弹」）─────────
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();

        if (!ParCool.PROXY.isLocalPlayer(player)) return;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        if (parkourability.getAdditionalProperties().getNotLandingTick() > 5 && event.getDistance() < 0.4f) {
            parkourability.get(ChargeJump.class).onLand(player, parkourability);
        }
    }
}
