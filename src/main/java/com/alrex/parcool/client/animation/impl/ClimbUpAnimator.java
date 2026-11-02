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
 * 从抓墙状态「翻上去」的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>10 tick 内身体前倾翻上去、双臂上摆 ✓</p>
 *
 * <p>1.12.2 替换：{@code PlayerEntity} → {@link EntityPlayer} + {@code @SideOnly(Side.CLIENT)}，其余不动 ✓</p>
 */
@SideOnly(Side.CLIENT)
public class ClimbUpAnimator extends Animator {

    private static final int MAX_TICK = 10;

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return MAX_TICK <= getTick();
    }

    private float bodyAngleFactor(float phase) {
        if (phase <= 0.3) {
            return 1 - 11.1f * (phase - 0.3f) * (phase - 0.3f);
        } else {
            return 1 - EasingFunctions.SinInOutBySquare((phase - 0.3f) * 1.45f);
        }
    }

    private float armAngle(float phase) {
        if (phase <= 0.3) {
            return MathUtil.lerp(0, -170, 11.1f * (phase - 0.3f) * (phase - 0.3f));
        } else {
            return 0;
        }
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float phase = (getTick() + rotator.getPartialTick()) / MAX_TICK;
        float factor = bodyAngleFactor(phase);

        rotator
                .startBasedCenter()
                .rotatePitchFrontward(factor * 50)
                .end();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float phase = (getTick() + transformer.getPartialTick()) / MAX_TICK;
        float angleArm = armAngle(phase);
        float bodyAngleFactor = bodyAngleFactor(phase);
        transformer
                .rotateRightArm((float) Math.toRadians(angleArm), 0, 0)
                .rotateLeftArm((float) Math.toRadians(angleArm), 0, 0)
                .addRotateLeftLeg((float) Math.toRadians(bodyAngleFactor * -50), 0, 0)
                .addRotateRightLeg((float) Math.toRadians(bodyAngleFactor * -50), 0, 0)
                .makeArmsNatural()
                .end();
    }
}
