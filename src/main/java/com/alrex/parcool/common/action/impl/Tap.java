package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.client.animation.impl.TapAnimator;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * Tap —— 安全落地「轻点」（1.12.2 版，**第七个真实现的动作**）。
 *
 * <p>从高处落下时用「轻点」代替翻滚：在空中把水平速度几乎清零、身体前倾撑住，
 * 短促 8 tick 后结束 ✓ 主要用途是它能让 {@code Dodge}/{@code Crawl} 等动作的判定保持正确
 * （那些动作会查 {@code !Tap.isDoing()}）。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code setDeltaMovement(getDeltaMovement().multiply(0.01, 1, 0.01))}</td>
 *       <td><b>{@code motionX *= 0.01; motionZ *= 0.01;}</b>（1.12.2 的位移是可变字段）</td></tr>
 * </table>
 *
 * <p>触发者：{@code BreakfallReady.startTap(player)}（即「落地缓冲准备」动作）。
 * 本类自身只提供 {@link #startTap} 入口 + 动作体 ✓</p>
 */
public class Tap extends Action {

    private static final BehaviorEnforcer.ID ID_JUMP_CANCEL = BehaviorEnforcer.newID();
    private boolean startRequired = false;

    /** 双端都跑（在其它客户端也要归零水平速度，否则看起来会滑出去）。 */
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        player.motionX *= 0.01;
        player.motionZ *= 0.01;
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        startRequired = false;
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new TapAnimator());
        parkourability.getBehaviorEnforcer().addMarkerCancellingJump(ID_JUMP_CANCEL, this::isDoing);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        startRequired = false;
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new TapAnimator());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        return startRequired;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return getDoingTick() < getMaxTappingTick();
    }

    /** 由 {@code BreakfallReady} 调用（见该类的 {@code startBreakfall}）。 */
    public void startTap(EntityPlayer player) {
        startRequired = true;
    }

    public int getMaxTappingTick() {
        return 8;
    }
}
