package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.JumpFromBarAnimator;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.EntityUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * JumpFromBar —— 从横杆上跳离（1.12.2 版，**第二十一个真实现的动作**）。
 *
 * <p><b>不是独立触发的</b>：只有 {@link HangDown} 已挂住 2 tick 以上时按跳跃键才会触发 ✓
 * 效果是沿视线方向（水平分量）给一个冲量，大小与玩家宽度成正比 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getLookAngle().multiply(1,0,1).normalize()}</td>
 *       <td>{@code new Vec3d(look.x, 0, look.z).normalize()}</td></tr>
 *   <tr><td>{@code player.getBbWidth()}</td><td>{@code player.width}</td></tr>
 *   <tr><td>{@code EntityUtil.addVelocity(player, vec)}</td>
 *       <td>同名 ✓（本工程的 {@code EntityUtil} 已按 1.12.2 的可变 motion 字段实现）</td></tr>
 * </table>
 *
 * <p><b>⚠️ 上游的 {@code canStart} 没标 {@code @OnlyIn(CLIENT)}，但它读了 {@code KeyRecorder}</b>
 * （客户端专属）→ 1.12.2 必须补 {@code @SideOnly(Side.CLIENT)} ✓</p>
 */
public class JumpFromBar extends Action {

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        HangDown hangDown = parkourability.get(HangDown.class);
        return hangDown.isDoing()
                && hangDown.getDoingTick() > 2
                && KeyRecorder.keyJumpState.isPressed();
    }

    /** 只持续 2 tick —— 是个「瞬间冲量」而不是持续动作 ✓ */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return getDoingTick() < 2;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        Vec3d look = player.getLookVec();
        Vec3d horizontal = new Vec3d(look.x, 0, look.z).normalize();
        EntityUtil.addVelocity(player, horizontal.scale(player.width * 0.75));
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.HANG_DOWN_JUMP.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new JumpFromBarAnimator());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.HANG_DOWN_JUMP.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new JumpFromBarAnimator());
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }
}
