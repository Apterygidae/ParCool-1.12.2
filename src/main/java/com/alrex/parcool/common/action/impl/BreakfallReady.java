package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * BreakfallReady —— 落地缓冲准备（1.12.2 版，**第九个真实现的动作**）。
 *
 * <p>从高处下落时按住「缓冲键」→ 落地瞬间由本动作决定接<b>翻滚（Roll）</b>还是<b>轻点（Tap）</b>：
 * 有移动输入就翻滚，否则轻点。还会按「是否恰好卡在落地窗口内（just-time）」给特效与体力折扣。</p>
 *
 * <h3>★ 本类与 Roll / Tap 的关系（重要）</h3>
 * <p>{@code Roll} 与 {@code Tap} 的 {@code canStart} 里只有 {@code return startRequired;} ——
 * <b>它们自己不会启动</b>。启动它们的唯一入口就是本类的
 * {@link #startBreakfall} → {@code Roll.startRoll(...)} / {@code Tap.startTap(...)} ✓</p>
 * <p>而 {@code startBreakfall} 由 <b>{@code StartBreakfallMessage}</b>（服务端 → 客户端）调用 ——
 * 服务端在 {@code LivingFallEvent} 里判定「该玩家正在缓冲」后发这个包 ✓
 * <b>所以完整触发链是：服务端落地判定 → 发包 → 客户端调 startBreakfall → 触发 Roll/Tap</b> ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code player.position()}</td><td>{@code player.getPositionVector()}</td></tr>
 *   <tr><td>{@code player.getBbHeight()}</td><td>{@code player.height}</td></tr>
 *   <tr><td>{@code player.getRandom()}</td><td>{@code player.getRNG()}</td></tr>
 *   <tr><td>{@code player.level.addParticle(ParticleTypes.END_ROD, ...)}</td>
 *       <td>{@code world.spawnParticle(EnumParticleTypes.END_ROD, x,y,z, vx,vy,vz)}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code KeyBinding.isDown()}</td><td>{@code KeyBinding.isKeyDown()}</td></tr>
 * </table>
 *
 * <p>本类依赖 {@code KeyBindings}（客户端专属）→ 三个方法都要 {@code @SideOnly(Side.CLIENT)} ✓</p>
 */
public class BreakfallReady extends Action {

    /**
     * 落地时决定接翻滚还是轻点，并把体力与音效一起处理。
     *
     * @param justTimed 是否落在「恰好时机」窗口内（服务端判定后随包传过来）
     */
    @SideOnly(Side.CLIENT)
    public void startBreakfall(EntityPlayer player, Parkourability parkourability, IStamina stamina, boolean justTimed) {
        boolean playSound = false;
        if (justTimed && ParCoolConfig.Client.Booleans.EnableJustTimeEffectOfBreakfall.get()) {
            if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
                player.playSound(SoundEvents.BREAKFALL_JUST_TIME.get(), 1, 1);
            }
            if (ParCoolConfig.Client.Booleans.EnableActionParticles.get()
                    && ParCoolConfig.Client.Booleans.EnableActionParticlesOfJustTimeBreakfall.get()
            ) {
                Vec3d pos = player.getPositionVector();
                Random rand = player.getRNG();
                for (int i = 0; i < 12; i++) {
                    player.world.spawnParticle(
                            EnumParticleTypes.END_ROD,
                            pos.x,
                            pos.y + player.height / 2,
                            pos.z,
                            (rand.nextDouble() - 0.5) * 0.5,
                            (rand.nextDouble() - 0.5) * 0.5,
                            (rand.nextDouble() - 0.5) * 0.5
                    );
                }
            }
        } else if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            playSound = true;
        }

        if ((KeyBindings.isAnyMovingKeyDown()
                && parkourability.getActionInfo().can(Roll.class))
                || !parkourability.getActionInfo().can(Tap.class)
        ) {
            stamina.consume((int) ((justTimed ? 0.25f : 1)
                    * parkourability.getActionInfo().getStaminaConsumptionOf(Roll.class)));
            if (playSound) player.playSound(SoundEvents.ROLL.get(), 1, 1);
            parkourability.get(Roll.class).startRoll(player);
        } else {
            stamina.consume((int) ((justTimed ? 0.25f : 1)
                    * parkourability.getActionInfo().getStaminaConsumptionOf(Tap.class)));
            if (playSound) player.playSound(SoundEvents.SAFETY_TAP.get(), 1, 1);
            parkourability.get(Tap.class).startTap(player);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        return canContinue(player, parkourability, stamina);
    }

    /**
     * 条件：按着缓冲键 + 体力没耗尽 + 不在爬行 + 不在水里 + （还在空中 或 刚落地的 3 tick 内）。
     *
     * <p>最后那条是「窗口」：空中整段都算，落地后只留 3 tick 的宽容期 ✓</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return (KeyBindings.getKeyBreakfall().isKeyDown()
                && !stamina.isExhausted()
                && !parkourability.get(Crawl.class).isDoing()
                && !ParCoolPlatform.isInWaterOrBubble(player)
                && (!player.onGround || parkourability.getAdditionalProperties().getLandingTick() < 3)
        );
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }
}
