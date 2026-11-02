package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.Slide;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.Easing;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 滑铲的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>特征是「侧身低伏」：身体绕滚动轴侧倾、头低下、一条腿伸出、手臂前撑 ✓</p>
 *
 * <p>1.12.2 替换：{@code PlayerEntity} → {@link EntityPlayer}、{@code Vector3d} → {@link Vec3d}，
 * 加 {@code @SideOnly(Side.CLIENT)} ✓ <b>本类不含任何 {@code MathHelper} 调用，无需改浮点字面量</b> ✓</p>
 */
@SideOnly(Side.CLIENT)
public class SlidingAnimator extends Animator {

    private static final int MAX_TRANSITION_TICK = 5;

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(Slide.class).isDoing();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float animFactor = (getTick() + transformer.getPartialTick()) / MAX_TRANSITION_TICK;
        if (animFactor > 1) animFactor = 1;
        animFactor = new Easing(animFactor)
                .sinInOut(0, 1, 0, 1)
                .get();

        transformer
                .translateLeftLeg(
                        0,
                        -1.2f * animFactor,
                        -2f * animFactor
                )
                .translateRightArm(
                        0,
                        1.2f * animFactor,
                        1.2f * animFactor
                )
                .translateHead(0, 0, -animFactor)
                .rotateHeadPitch(50 * animFactor)
                .rotateAdditionallyHeadYaw(50 * animFactor)
                .rotateAdditionallyHeadRoll(-10 * animFactor)
                .rotateRightArm((float) Math.toRadians(50), (float) Math.toRadians(-40), 0, animFactor)
                .rotateLeftArm((float) Math.toRadians(20), 0, (float) Math.toRadians(-100), animFactor)
                .rotateRightLeg((float) Math.toRadians(-30), (float) Math.toRadians(40), 0, animFactor)
                .rotateLeftLeg((float) Math.toRadians(40), (float) Math.toRadians(-30), (float) Math.toRadians(15), animFactor)
                .makeLegsLittleMoving()
                .makeArmsNatural()
                .end();
    }

    /** 前 5 tick 从「站立」过渡到「侧伏」，之后保持 ✓ */
    @Override
    public boolean rotatePre(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        Vec3d vec = parkourability.get(Slide.class).getSlidingVector();
        if (vec == null) return false;
        float animFactor = (getTick() + rotator.getPartialTick()) / MAX_TRANSITION_TICK;
        float yRot = (float) VectorUtil.toYawDegree(vec);
        if (animFactor > 1) animFactor = 1;
        animFactor = new Easing(animFactor)
                .sinInOut(0, 1, 0, 1)
                .get();
        rotator
                .rotateYawRightward(180f + yRot)
                .rotatePitchFrontward(-55f * animFactor)
                .translate(0.35f * animFactor, 0, 0)
                .rotateYawRightward(-55f * animFactor)
                .translate(0, -0.7f * animFactor, -0.3f * animFactor);
        return true;
    }
}
