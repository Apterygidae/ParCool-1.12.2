package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.EasingFunctions;
import com.alrex.parcool.utilities.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 「背朝墙」蹬墙跳的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>特征是 <b>整整后空翻一圈</b>（`rotatePitchFrontward(factor * -360)`），
 * 第一人称还有配套的镜头翻转 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code clientPlayer.isLocalPlayer()}</td><td>{@link ParCool#PROXY}{@code .isLocalPlayer(...)}</td></tr>
 *   <tr><td>{@code mc.options.getCameraType().isFirstPerson()}</td>
 *       <td>{@code Minecraft.getMinecraft().gameSettings.thirdPersonView == 0}</td></tr>
 *   <tr><td><b>{@code clientPlayer.getViewXRot(partialTicks)}</b></td>
 *       <td><b>1.12.2 没有</b> → 手算
 *           {@code prevRotationPitch + (rotationPitch - prevRotationPitch) * partial}</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class BackwardWallJumpAnimator extends Animator {

    private final int maxTick = 12;

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return getTick() >= maxTick;
    }

    private float armAngleXFactorBack(float phase) {
        if (phase < 0.1) {
            return 1 - 100 * (phase - 0.1f) * (phase - 0.1f);
        } else if (phase < 0.6) {
            return 1 - EasingFunctions.SinInOutBySquare((phase - 0.1f) * 2);
        } else {
            return 0.1f * EasingFunctions.SinInOutBySquare((phase - 0.6f) * 2.5f);
        }
    }

    private float legAngleFactorBack(float phase) {
        if (phase < 0.5) {
            return 8 * (phase - 0.25f) * (phase - 0.25f);
        } else {
            return 1 - 8 * (phase - 0.75f) * (phase - 0.75f);
        }
    }

    private float armAngleZFactor(float phase) {
        if (phase < 0.5) {
            return phase * 0.4f;
        } else if (phase < 0.75) {
            return 0.2f + 0.8f * EasingFunctions.CubicInOut((phase - 0.5f) * 4);
        } else {
            return 1 - EasingFunctions.CubicInOut((phase - 0.75f) * 4);
        }
    }

    private float angleFactor(float phase) {
        return EasingFunctions.SinInOutBySquare(phase);
    }

    private float getAnimationFactor(float phase) {
        if (phase < 0.3) {
            return EasingFunctions.SinInOutBySquare(phase * 3.33f);
        } else if (phase > 0.7) {
            return 1 - EasingFunctions.SinInOutBySquare((phase - 0.7f) * 3.33f);
        }
        return 1;
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float phase = (getTick() + transformer.getPartialTick()) / maxTick;
        float armAngleX = MathUtil.lerp(20, -190, armAngleXFactorBack(phase));
        float armAngleZ = MathUtil.lerp(phase > 0.75 ? 0 : 14, 28, armAngleZFactor(phase));
        float legAngle = MathUtil.lerp(35, -35, legAngleFactorBack(phase));
        float headAngle = MathUtil.lerp(0, -45, 1 - 4 * (phase - 0.5f) * (phase - 0.5f));
        float animationFactor = getAnimationFactor(phase);
        transformer
                .rotateAdditionallyHeadPitch(headAngle)
                .rotateRightArm(
                        (float) Math.toRadians(armAngleX),
                        0,
                        (float) Math.toRadians(armAngleZ),
                        animationFactor
                )
                .rotateLeftArm(
                        (float) Math.toRadians(armAngleX),
                        0,
                        (float) -Math.toRadians(armAngleZ),
                        animationFactor
                )
                .makeArmsNatural()
                .rotateRightLeg(
                        (float) Math.toRadians(legAngle - 15),
                        0, 0,
                        animationFactor
                )
                .rotateLeftLeg(
                        (float) Math.toRadians(legAngle + 15),
                        0, 0,
                        animationFactor
                )
                .makeLegsLittleMoving()
                .end();
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float phase = (getTick() + rotator.getPartialTick()) / maxTick;
        float factor = angleFactor(phase);

        rotator
                .startBasedCenter()
                .rotatePitchFrontward(factor * -360)
                .end();
    }

    /** 1.12.2 没有 {@code getViewXRot(partialTicks)} —— 手算插值后的俯仰角。 */
    private static float getViewXRot(EntityPlayer player, float partialTicks) {
        return player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
    }

    @Override
    public void onCameraSetUp(EntityViewRenderEvent.CameraSetup event, EntityPlayer clientPlayer,
                              Parkourability parkourability) {
        if (!(ParCool.PROXY.isLocalPlayer(clientPlayer)
                && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0
                && ParCoolConfig.Client.Booleans.EnableCameraAnimationOfBackWallJump.get()
        )) return;
        float phase = (float) ((getTick() + event.getRenderPartialTicks()) / maxTick);
        float factor = angleFactor(phase);
        event.setPitch(getViewXRot(clientPlayer, (float) event.getRenderPartialTicks())
                - factor * 360 + ((phase > 0.5) ? 360 : 0));
    }
}
