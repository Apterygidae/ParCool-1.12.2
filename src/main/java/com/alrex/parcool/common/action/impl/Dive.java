package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.impl.DiveAnimationHostAnimator;
import com.alrex.parcool.client.animation.impl.DiveIntoWaterAnimator;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.BufferUtil;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * Dive —— 俯冲（1.12.2 版，**第二十二个真实现的动作**）。
 *
 * <p>两种起手方式：</p>
 * <ol>
 *   <li><b>空中起手</b>：下落中、且下方有足够空间、且跳跃键已按住 10 tick 以上 → 直接进入俯冲 ✓</li>
 *   <li><b>跳起后起手</b>：刚跳起（{@code justJumped}）且前方有可俯冲空间、且处于疾跑状态 → 跳水式俯冲 ✓</li>
 * </ol>
 *
 * <h3>★ 它的动画是一个「宿主 + 两个子动画器」的结构</h3>
 * <p>见 {@link DiveAnimationHostAnimator} —— 本动作只负责状态，动画的调度全在那个宿主里 ✓</p>
 *
 * <h3>★ {@code onJump} 的接线（本批补上的）</h3>
 * <p>上游由 {@code PlayerJumpHandler.onJump} 转调：
 * {@code parkourability.get(Dive.class).onJump(...)} ✓
 * <b>本工程的 {@code PlayerJumpHandler} 里那一行原本是注释掉的</b>
 * （等这个动作实现）→ <b>本批已放开</b> ✓
 * 没有它的话「跳起后俯冲」这条路径<b>永远不会触发</b> ✗（{@code justJumped} 恒 false）✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getDeltaMovement().y()}</td><td>{@code player.motionY}</td></tr>
 *   <tr><td>{@code player.getVehicle()}</td><td>{@code player.getRidingEntity()}</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code player.abilities.flying}</td><td>{@code player.capabilities.isFlying}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble() / isSwimming() / isFallFlying()}</td>
 *       <td>{@link ParCoolPlatform} 的三个包装 ✓</td></tr>
 *   <tr><td>{@code player.isInLava()}</td><td>同名 ✓</td></tr>
 *   <tr><td><b>{@code player.isVisuallyCrawling()}</b></td>
 *       <td><b>1.12.2 没有</b> → 用 {@link ParCoolPlatform#isVisuallySwimming}（最接近的语义）✓
 *           注意前面已经单独判过 {@code Crawl.isDoing()}，所以这只是二次保险 ✓</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td><td>{@link ParCool#PROXY}{@code .isLocalPlayer(...)}</td></tr>
 *   <tr><td>{@code MathHelper.lerp(partial, old, cur)}</td>
 *       <td><b>1.12.2 的 {@code MathHelper} 没有 {@code lerp}</b> ✗
 *           → 用 {@code MathUtil.lerp(t, a, b)}（本工程自有）✓</td></tr>
 *   <tr><td>{@code WorldUtil.existsSpaceBelow / existsDivableSpace}</td>
 *       <td>本批已补进 {@code WorldUtil} ✓</td></tr>
 * </table>
 *
 * <p><b>⚠️ {@code canStart}/{@code canContinue}/{@code onStart*}/{@code onStop*}</b> 都读了
 * 客户端输入或动画 → 都要 {@code @SideOnly(Side.CLIENT)} ✓</p>
 *
 * <p><b>⚠️ {@code fallingTick} 的用途</b>：记录「已经持续下落几 tick」——
 * 用于 {@code canContinue} 里「下落超过 5 tick 但摔落距离仍不到 0.1」时结束俯冲
 * （那说明是卡在什么地方了，不是在真的下落）✓</p>
 */
public class Dive extends Action {

    private boolean justJumped = false;
    private double initialYVelocityOfLastJump = 0.42;
    private double playerYSpeedOld = 0;
    private double playerYSpeed = 0;
    private int fallingTick = 0;

    /**
     * 插值后的当前下落速度 —— 供动画器用（避免帧间抖动）✓
     *
     * <p>⚠️ 这里<b>手写插值</b>而不是用 {@code MathHelper.lerp} ——
     * <b>1.12.2 的 {@code MathHelper} 根本没有 {@code lerp}</b> ✗
     * （只有一个 {@code clampedLerp(lowerBnd, upperBnd, slide)}，<b>参数顺序也不同</b> ✗）
     * → 本工程统一的对应物是 {@code MathUtil.lerp(t, a, b)} ✓</p>
     */
    public double getPlayerYSpeed(float partialTick) {
        return playerYSpeedOld + (playerYSpeed - playerYSpeedOld) * partialTick;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        playerYSpeedOld = playerYSpeed;
        playerYSpeed = player.motionY;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (isDoing() && (playerYSpeed < 0 || fallingTick > 0)) {
            fallingTick++;
        } else {
            fallingTick = 0;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        if (player.getRidingEntity() != null) return false;
        boolean startInAir = player.motionY < 0
                && parkourability.getAdditionalProperties().getNotLandingTick() > 10
                && parkourability.getAdditionalProperties().getNotInWaterTick() > 30
                // 跳跃键要已按住 10 tick 以上 —— 避免与普通跳跃抢触发
                && KeyRecorder.keyJumpState.getTickKeyDown() > 10
                && !parkourability.get(CatLeap.class).isDoing()
                && !parkourability.get(RideZipline.class).isDoing()
                && WorldUtil.existsSpaceBelow(player);
        if (!(startInAir || (justJumped
                && WorldUtil.existsDivableSpace(player)
                && parkourability.get(FastRun.class).canActWithRunning(player)))) {
            justJumped = false;
            return false;
        }

        startInfo.putDouble(initialYVelocityOfLastJump);
        BufferUtil.wrap(startInfo).putBoolean(startInAir);

        justJumped = false;
        return parkourability.getActionInfo().can(Dive.class)
                && !stamina.isExhausted()
                && !parkourability.get(Crawl.class).isDoing()
                && !ParCoolPlatform.isVisuallySwimming(player);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return !(ParCoolPlatform.isFallFlying(player)
                || player.capabilities.isFlying
                || ParCoolPlatform.isInWaterOrBubble(player)
                || player.isInLava()
                || ParCoolPlatform.isSwimming(player)
                || player.onGround
                // 下落很久但几乎没落地过 —— 说明卡住了，不是在俯冲
                || (fallingTick > 5 && player.fallDistance < 0.1)
                || stamina.isExhausted()
                || parkourability.get(RideZipline.class).isDoing()
        );
    }

    /** 由 {@code PlayerJumpHandler.onJump} 转调 —— 记录起跳初速度并置「刚跳起」标记 ✓ */
    public void onJump(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (!ParCool.PROXY.isLocalPlayer(player)) return;
        initialYVelocityOfLastJump = player.motionY;
        justJumped = true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        double initialYSpeed = startData.getDouble();
        playerYSpeedOld = playerYSpeed = initialYSpeed;
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new DiveAnimationHostAnimator(initialYSpeed, BufferUtil.getBoolean(startData)));
        }
    }

    /**
     * 结束时的「入水」收尾：只有结束瞬间<b>在水里</b>、且下落着，才播入水动画 ✓
     * （所以「俯冲落地」不会播入水动画，只有「俯冲入水」会 ✓）
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInLocalClient(EntityPlayer player) {
        if (ParCoolPlatform.isInWaterOrBubble(player)) {
            Animation animation = Animation.get(player);
            Parkourability parkourability = Parkourability.get(player);
            if (animation != null
                    && parkourability != null
                    && parkourability.getAdditionalProperties().getNotLandingTick() >= 5
                    && player.motionY < 0
            ) {
                animation.setAnimator(new DiveIntoWaterAnimator(parkourability.get(SkyDive.class).isDoing()));
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInOtherClient(EntityPlayer player) {
        if (ParCoolPlatform.isInWaterOrBubble(player)) {
            Animation animation = Animation.get(player);
            Parkourability parkourability = Parkourability.get(player);
            if (animation != null
                    && parkourability != null
                    && parkourability.getAdditionalProperties().getNotLandingTick() >= 5
                    && player.motionY < 0
            ) {
                animation.setAnimator(new DiveIntoWaterAnimator(parkourability.get(SkyDive.class).isDoing()));
            }
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }

    @Override
    public void saveSynchronizedState(ByteBuffer buffer) {
        buffer.putDouble(playerYSpeed).putDouble(playerYSpeedOld);
    }

    @Override
    public void restoreSynchronizedState(ByteBuffer buffer) {
        playerYSpeed = buffer.getDouble();
        playerYSpeedOld = buffer.getDouble();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        double initialYSpeed = startData.getDouble();
        playerYSpeedOld = playerYSpeed = initialYVelocityOfLastJump = initialYSpeed;
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new DiveAnimationHostAnimator(initialYSpeed, BufferUtil.getBoolean(startData)));
        }
    }
}
