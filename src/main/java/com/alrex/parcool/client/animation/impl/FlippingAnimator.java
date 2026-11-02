package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.Flipping;
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
 * 空翻的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>12 tick 内<b>整体翻转一整圈</b>（`rotatePitchFrontward(±360 * factor)`）——
 * 前空翻是 +360、后空翻是 -360 ✓ 四肢姿态在两个方向上是两套不同的曲线 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code clientPlayer.isLocalPlayer()}</td>
 *       <td>{@link ParCool#PROXY}{@code .isLocalPlayer(...)}</td></tr>
 *   <tr><td>{@code mc.options.getCameraType().isFirstPerson()}</td>
 *       <td>{@code Minecraft.getMinecraft().gameSettings.thirdPersonView == 0}</td></tr>
 *   <tr><td><b>{@code clientPlayer.getViewXRot(partialTicks)}</b></td>
 *       <td><b>1.12.2 没有</b> → 手算
 *           {@code prevRotationPitch + (rotationPitch - prevRotationPitch) * partial}</td></tr>
 * </table>
 *
 * <p><b>⚠️ 第一人称镜头翻转里那个 {@code ±360} 与 {@code (phase > 0.5) ? 360 : 0} 的组合</b>
 * 是为了让<b>翻转跨越 180° 时镜头不突然翻面</b> ✓ 照搬是对的，别"简化"掉 ✗</p>
 */
@SideOnly(Side.CLIENT)
public class FlippingAnimator extends Animator {

    private final Flipping.Direction direction;

    public FlippingAnimator(Flipping.Direction direction) {
        this.direction = direction;
    }

    private int getMaxAnimationTick() {
        return 12;
    }

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(Flipping.class).isDoing() || getTick() >= getMaxAnimationTick();
    }

    private float angleFactor(float phase) {
        return EasingFunctions.SinInOutBySquare(phase);
    }

    private float armAngleXFactorFront(float phase) {
        if (phase < 0.1) {
            return 1 - 100 * (phase - 0.1f) * (phase - 0.1f);
        } else if (phase < 0.6) {
            return 1 - 0.8f * EasingFunctions.CubicInOut((phase - 0.1f) * 2);
        } else {
            return -0.5f * phase + 0.5f;
        }
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

    private float legAngleFactorFront(float phase) {
        return 1 - 4 * (phase - 0.5f) * (phase - 0.5f);
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float phase = (getTick() + transformer.getPartialTick()) / getMaxAnimationTick();

        if (direction == Flipping.Direction.Front) {
            float armAngleX = -180 * armAngleXFactorFront(phase) + 25;
            float armAngleZ = MathUtil.lerp(phase > 0.75 ? 0 : 14, 28, armAngleZFactor(phase));
            float legAngleX = MathUtil.lerp(0, -50, legAngleFactorFront(phase));
            float headAngle = MathUtil.lerp(0, 45, legAngleFactorFront(phase));
            transformer
                    .rotateAdditionallyHeadPitch(headAngle)
                    .rotateRightArm(
                            (float) Math.toRadians(armAngleX),
                            0,
                            (float) Math.toRadians(armAngleZ)
                    )
                    .rotateLeftArm(
                            (float) Math.toRadians(armAngleX),
                            0,
                            (float) -Math.toRadians(armAngleZ)
                    )
                    .makeArmsNatural()
                    .rotateRightLeg(
                            (float) Math.toRadians(legAngleX - 15),
                            0, 0
                    )
                    .rotateLeftLeg(
                            (float) Math.toRadians(legAngleX + 15),
                            0, 0
                    )
                    .makeLegsLittleMoving()
                    .end();
        } else {
            float armAngleX = MathUtil.lerp(20, -190, armAngleXFactorBack(phase));
            float armAngleZ = MathUtil.lerp(phase > 0.75 ? 0 : 14, 28, armAngleZFactor(phase));
            float legAngle = MathUtil.lerp(35, -35, legAngleFactorBack(phase));
            float headAngle = MathUtil.lerp(0, -45, 1 - 4 * (phase - 0.5f) * (phase - 0.5f));
            transformer
                    .rotateAdditionallyHeadPitch(headAngle)
                    .rotateRightArm(
                            (float) Math.toRadians(armAngleX),
                            0,
                            (float) Math.toRadians(armAngleZ)
                    )
                    .rotateLeftArm(
                            (float) Math.toRadians(armAngleX),
                            0,
                            (float) -Math.toRadians(armAngleZ)
                    )
                    .makeArmsNatural()
                    .rotateRightLeg(
                            (float) Math.toRadians(legAngle - 15),
                            0, 0
                    )
                    .rotateLeftLeg(
                            (float) Math.toRadians(legAngle + 15),
                            0, 0
                    )
                    .makeLegsLittleMoving()
                    .end();
        }
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float phase = (getTick() + rotator.getPartialTick()) / getMaxAnimationTick();
        float factor = angleFactor(phase);

        float angle;
        if (direction == Flipping.Direction.Front) {
            angle = factor * 360;
        } else {
            angle = factor * -360;
        }
        rotator
                .startBasedCenter()
                .rotatePitchFrontward(angle)
                .end();
    }

    /** 1.12.2 没有 {@code getViewXRot(partialTicks)} —— 手算插值后的俯仰角。 */
    private static float getViewXRot(EntityPlayer player, float partialTicks) {
        return player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
    }

    @Override
    public void onCameraSetUp(EntityViewRenderEvent.CameraSetup event, EntityPlayer clientPlayer,
                              Parkourability parkourability) {
        if (!ParCool.PROXY.isLocalPlayer(clientPlayer)
                || Minecraft.getMinecraft().gameSettings.thirdPersonView != 0
                || !ParCoolConfig.Client.Booleans.EnableCameraAnimationOfFlipping.get()
        ) return;
        float phase = (float) ((getTick() + event.getRenderPartialTicks()) / getMaxAnimationTick());
        float factor = angleFactor(phase);
        if (direction == Flipping.Direction.Front) {
            event.setPitch(getViewXRot(clientPlayer, (float) event.getRenderPartialTicks())
                    + factor * 360 - ((phase > 0.5) ? 360 : 0));
        } else {
            event.setPitch(getViewXRot(clientPlayer, (float) event.getRenderPartialTicks())
                    - factor * 360 + ((phase > 0.5) ? 360 : 0));
        }
    }
}
