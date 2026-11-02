package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.Tap;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.Easing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 「轻点」（安全落地）的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>1.12.2 替换：{@code PlayerEntity} → {@link EntityPlayer}、
 * {@code player.getBbHeight()} → {@code player.height}、加 {@code @SideOnly(Side.CLIENT)}。其余不动 ✓</p>
 */
@SideOnly(Side.CLIENT)
public class TapAnimator extends Animator {

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return getTick() >= parkourability.get(Tap.class).getMaxTappingTick();
    }

    public float getAngleFactor(float phase) {
        return new Easing(phase)
                .squareOut(0, 0.25f, 0, 1)
                .sinInOut(0.25f, 1, 1, 0)
                .get();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float phase = (getTick() + transformer.getPartialTick()) / parkourability.get(Tap.class).getMaxTappingTick();
        if (phase > 1) phase = 1;
        float factor = getAngleFactor(phase);
        float animFactor = new Easing(phase)
                .squareOut(0, 0.25f, 0, 1)
                .linear(0.25f, 0.75f, 1, 1)
                .squareIn(0.75f, 1, 1, 0)
                .get();
        float angle = 80 * factor;
        transformer
                .translateRightLeg(0, -2f * factor, factor)
                .translateLeftLeg(0, -5f * factor, -1.5f * factor)
                .translateRightArm(0, 4f * factor, 0)
                .rotateLeftLeg(
                        (float) Math.toRadians(-30 * factor), 0, 0, animFactor
                )
                .rotateRightLeg(
                        (float) Math.toRadians(-15 * factor), 0, 0, animFactor
                )
                .rotateRightArm(
                        (float) Math.toRadians(20 * factor), 0, (float) Math.toRadians(13 * factor), animFactor
                )
                .rotateLeftArm(
                        (float) Math.toRadians(-angle), 0, 0, animFactor
                )
                .rotateAdditionallyHeadPitch(-40 * factor)
                .makeArmsNatural()
                .end();
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float phase = (getTick() + rotator.getPartialTick()) / parkourability.get(Tap.class).getMaxTappingTick();
        if (phase > 1) phase = 1;
        float factor = getAngleFactor(phase);
        float angle = 80 * factor;
        rotator
                .startBasedCenter()
                .translateY(-factor * player.height / 5)
                .rotatePitchFrontward(angle)
                .endEnabledLegGrounding();
    }
}
