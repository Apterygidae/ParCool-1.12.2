package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.EasingFunctions;
import com.alrex.parcool.utilities.MathUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 从横杆上跳离的动画（1.12.2 版）—— **纯数学，无 MC API**，只换类型。
 *
 * <p>8 tick 内：双腿收起蹬一下、双臂前后摆、身体前倾 ✓</p>
 *
 * <p>{@code jumpFromBar} 的 {@code getFactor} 是个三段曲线：前段上升、中段回落、后段收尾 ——
 * 对应「蹬出去 → 空中舒展 → 落地准备」✓</p>
 */
@SideOnly(Side.CLIENT)
public class JumpFromBarAnimator extends Animator {

    private static final int MAX_TICK = 8;

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return getTick() >= MAX_TICK;
    }

    private static float getFactor(float phase) {
        if (phase < 0.333f) {
            return 0.5f + 0.5f * EasingFunctions.SinInOutBySquare(phase * 3);
        }
        if (phase < 0.666f) {
            return 1 - EasingFunctions.SinInOutBySquare((phase - 0.333f) * 3);
        } else {
            return 0.5f * EasingFunctions.SinInOutBySquare((phase - 0.666f) * 3);
        }
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float tick = getTick() + transformer.getPartialTick();
        float phase = tick / MAX_TICK;
        float factor = getFactor(phase);
        float armFactor = MathUtil.squaring(phase - 1);
        float armYAngleFactor = 1 - 4 * MathUtil.squaring(phase - 0.5f);
        float bodyFactor = getBodyAngleFactor(phase);
        float animationFactor = 1 - phase * phase * phase;
        transformer
                .rotateHeadPitch(bodyFactor * 40)
                .rotateRightArm(
                        (float) Math.toRadians(180 * armFactor), (float) Math.toRadians(-45 * armYAngleFactor), 0, animationFactor
                )
                .rotateLeftArm(
                        (float) Math.toRadians(180 * armFactor), (float) Math.toRadians(45 * armYAngleFactor), 0, animationFactor
                )
                .rotateRightLeg(
                        (float) Math.toRadians(30 - 60 * factor), 0, 0, animationFactor
                )
                .rotateLeftLeg(
                        (float) Math.toRadians(30 - 60 * factor), 0, 0, animationFactor
                )
                .makeLegsLittleMoving()
                .end();
    }

    private float getBodyAngleFactor(float phase) {
        return 1 - 4 * MathUtil.squaring(phase - 0.5f);
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float tick = getTick() + rotator.getPartialTick();
        float phase = tick / MAX_TICK;
        float factor = getBodyAngleFactor(phase);
        rotator.startBasedCenter()
                .rotatePitchFrontward(-factor * 40)
                .end();
    }
}
