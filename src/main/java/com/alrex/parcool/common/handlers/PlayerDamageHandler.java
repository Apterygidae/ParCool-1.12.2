package com.alrex.parcool.common.handlers;

import com.alrex.parcool.api.unstable.action.ParCoolActionEvent;
import com.alrex.parcool.common.action.impl.BreakfallReady;
import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.action.impl.HideInBlock;
import com.alrex.parcool.common.action.impl.Roll;
import com.alrex.parcool.common.action.impl.Tap;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.network.StartBreakfallMessage;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 伤害处理 —— 本工程对齐上游 {@code PlayerDamageHandler} ✓
 *
 * <h3>★ 背景</h3>
 * <p>上游这个类<b>同时承担五件事</b> ✗ —— 而本工程此前<b>整个类都不存在</b> ✗：
 * 闪避免伤 · 破坠减伤 · 翻滚受身 · 藏身接坠 · {@code ChargeJump.onLand} ✓</p>
 * <p>补齐过程分两步：
 * ① 闪避免伤（{@link #onAttack} ✓ 2026-10-04 先补 ✓，修「闪避不免疫伤害」✗）；
 * ② 落地相关（{@link #onFall} ✓ 本批补 ✓，修「落地缓冲不减免掉落伤害」✗）</p>
 *
 * <p><b>⚠️ 上游 {@code onFall} 的最后一个分支（{@code ChargeJump.onLand}）不在本类</b> ✗ ——
 * 它已经在 {@link PlayerJumpHandler} 里实现 ✓（那个类本来就是为「跳/落两个瞬间」建的 ✓）
 * → <b>本类只取上游 {@code onFall} 的服务端分支</b>，避免重复触发 ✗</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code LivingAttackEvent#getEntityLiving()}</td>
 *       <td>同名 ✓（在 {@code LivingEvent} 上 ✓）</td></tr>
 *   <tr><td>{@code event.getSource().isBypassArmor()}</td>
 *       <td><b>1.12.2 没有</b> ✗ → {@code DamageSource#isUnblockable()}（语义最接近 ✓）</td></tr>
 *   <tr><td>{@code LivingFallEvent#getEntity()}</td>
 *       <td>{@code getEntityLiving()} ✓</td></tr>
 *   <tr><td>{@code ServerPlayerEntity}</td><td>{@link EntityPlayerMP}</td></tr>
 *   <tr><td>{@code player.blockPosition().below()}</td>
 *       <td>{@code new BlockPos(player).down()}</td></tr>
 *   <tr><td>{@code player.getBbHeight()}</td><td>{@code player.height}</td></tr>
 *   <tr><td>{@code area.getB().getY()}</td>
 *       <td>{@code area.getSecond().getY()} ✓（1.12.2 的 Tuple 是 getFirst/getSecond ✗）</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td><td>{@code ParCool.PROXY.isLocalPlayer(player)}</td></tr>
 * </table>
 */
public class PlayerDamageHandler {

    private PlayerDamageHandler() {
    }

    /**
     * 闪避免疫伤害 ✓
     *
     * <p>闪避开始后前 10 tick 内，取消可被格挡的伤害（见 {@code Dodge.MAX_TICK = 11}）✓</p>
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (!(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;

        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        Dodge dodge = parkourability.get(Dodge.class);
        if (!dodge.isDoing()) return;

        // 服务端可禁用（受限同步项 ✓；未同步时 get() 返回默认值 ✓）
        if (!parkourability.getServerLimitation()
                .get(ParCoolConfig.Server.Booleans.DodgeProvideInvulnerableFrame)) {
            return;
        }
        // 能穿透防护的伤害不该被免掉 ✓
        if (event.getSource().isUnblockable()) return;
        // 只覆盖闪避的前 10 tick（MAX_TICK = 11 ✓）—— 与上游一致 ✓
        if (dodge.getDoingTick() <= 10) {
            event.setCanceled(true);
        }
    }

    /**
     * 落地时的伤害处理 ✓ —— **本批新增，修的正是用户报的「落地缓冲不减免掉落伤害」** ✗
     *
     * <h3>两个分支</h3>
     * <ol>
     *   <li><b>破坠（Breakfall）</b>：{@code BreakfallReady} 正在做，且 {@code Tap}/{@code Roll} 可用 ✓<br>
     *       —— 落差超过下限 → <b>先给客户端发 {@code StartBreakfallMessage}</b>（让它播动作 ✓）<br>
     *       —— 落差小于「完全免伤高度」→ <b>整个取消伤害</b> ✓<br>
     *       —— 否则按比例减免（「精准时机」时额外 ×0.66 ✓）</li>
     *   <li><b>藏身接坠（HideInBlock）</b>：处于「空中待命」且下方有可藏空间，且是<b>趴伏式</b>藏身处 ✓<br>
     *       —— 落差 &lt; 10 → 完全取消 ✓；否则只保留 40% 伤害 ✓</li>
     * </ol>
     *
     * <p><b>⚠️ 客户端分支（{@code ChargeJump.onLand}）不在这里</b> ✗ ——
     * 见 {@link PlayerJumpHandler} ✓（避免重复触发 ✗）</p>
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        // 只处理服务端玩家（与上游一致 ✓）—— 伤害是在服务端结算的 ✓
        if (!(entity instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) entity;

        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        BreakfallReady breakfall = parkourability.get(BreakfallReady.class);
        if (breakfall.isDoing()
                && (parkourability.getActionInfo().can(Tap.class)
                || parkourability.getActionInfo().can(Roll.class))) {
            // 「精准时机」= 在很短的窗口内触发 → 效果更好 ✓
            boolean justTime = breakfall.getDoingTick() < parkourability.getLimitedValue(
                    ParCoolConfig.Client.Integers.JustTimeBreakfallTick,
                    ParCoolConfig.Server.Integers.MaxJustTimeBreakfallTick
            );
            float distance = event.getDistance();
            if (distance > parkourability.getLimitedValue(
                    ParCoolConfig.Client.Doubles.LowestFallDistanceForBreakfall,
                    ParCoolConfig.Server.Doubles.MinLowestFallDistanceForBreakfall
            )) {
                StartBreakfallMessage.send(player, justTime);
            } else {
                return;
            }
            double damageRemoveHeight = parkourability.getLimitedValue(
                    ParCoolConfig.Client.Doubles.DamageCompleteRemovableHeightBreakfall,
                    ParCoolConfig.Server.Doubles.MaxDamageCompleteRemovableHeightBreakfall
            );
            if (distance < damageRemoveHeight || (justTime && distance < damageRemoveHeight * 1.34)) {
                // 完全免伤 ✓
                event.setCanceled(true);
            } else {
                float damageReductionRate = (float) parkourability.getLimitedValue(
                        ParCoolConfig.Client.Doubles.DamageReductionRateBreakfall,
                        ParCoolConfig.Server.Doubles.MaxDamageReductionRateBreakfall
                );
                // 「精准时机」额外再乘 0.66 ✓
                event.setDamageMultiplier(event.getDamageMultiplier()
                        * (justTime ? 0.66f * damageReductionRate : damageReductionRate));
            }
        } else {
            // ── 藏身接坠：从空中落下时直接钻进可藏方块 ✓ ──
            HideInBlock hideInBlock = parkourability.get(HideInBlock.class);
            if (hideInBlock.isStandbyInAir(parkourability)
                    && parkourability.getActionInfo().can(HideInBlock.class)
                    && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStartEvent(player, hideInBlock))
                    && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStart(player, hideInBlock))) {
                Tuple<BlockPos, BlockPos> area = WorldUtil.getHideAbleSpace(
                        player, new BlockPos(player).down());
                if (area != null) {
                    boolean stand = player.height < (Math.abs(
                            area.getSecond().getY() - area.getFirst().getY()) + 1);
                    // 只有「趴伏式」藏身处才吃免伤（站姿时人还露在外面 ✗）
                    if (!stand) {
                        if (event.getDistance() < 10) {
                            event.setCanceled(true);
                        } else {
                            event.setDamageMultiplier(event.getDamageMultiplier() * 0.4f);
                        }
                    }
                }
            }
        }
    }
}
