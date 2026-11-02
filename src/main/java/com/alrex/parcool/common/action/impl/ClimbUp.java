package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.ClimbUpAnimator;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * ClimbUp —— 从挂墙状态翻上去（1.12.2 版，**第十二个真实现的动作**）。
 *
 * <p><b>它不是独立触发的</b>：只有 {@code ClingToCliff} 已挂住 2 tick 以上、
 * 且是「面朝墙」姿态时，按跳跃键才会触发 ✓ 保持原有水平速度，给一个向上的冲量（0.6）✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code player.getDeltaMovement()}</td>
 *       <td>{@code new Vec3d(player.motionX, player.motionY, player.motionZ)}</td></tr>
 *   <tr><td>{@code setDeltaMovement(x, 0.6, z)}</td>
 *       <td>{@code motionX/motionY/motionZ} 赋值</td></tr>
 * </table>
 */
public class ClimbUp extends Action {

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        ClingToCliff cling = parkourability.get(ClingToCliff.class);
        return cling.isDoing()
                && cling.getDoingTick() > 2
                && cling.getFacingDirection() == ClingToCliff.FacingDirection.ToWall
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
        Vec3d speed = new Vec3d(player.motionX, player.motionY, player.motionZ);
        player.motionX = speed.x;
        player.motionY = 0.6;
        player.motionZ = speed.z;
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.CLING_TO_CLIFF_JUMP.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new ClimbUpAnimator());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.CLING_TO_CLIFF_JUMP.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new ClimbUpAnimator());
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }
}
