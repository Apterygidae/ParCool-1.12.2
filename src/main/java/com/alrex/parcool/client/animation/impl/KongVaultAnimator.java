package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.Vault;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.Easing;
import com.alrex.parcool.utilities.EasingFunctions;
import com.alrex.parcool.utilities.MathUtil;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static java.lang.Math.toRadians;

/**
 * 拱身翻越（Kong Vault）的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code Vector3d} / {@code vec.x()}</td><td>{@link Vec3d} / {@code vec.x}</td></tr>
 *   <tr><td>{@code player.yBodyRot / yBodyRotO}</td>
 *       <td><b>{@code player.renderYawOffset / prevRenderYawOffset}</b></td></tr>
 *   <tr><td>{@code player.getBbHeight()}</td><td>{@code player.height}</td></tr>
 *   <tr><td>{@code mc.options.getCameraType().isFirstPerson()}</td>
 *       <td>{@code Minecraft.getMinecraft().gameSettings.thirdPersonView == 0}</td></tr>
 *   <tr><td><b>{@code player.getViewXRot(partialTicks)}</b></td>
 *       <td><b>1.12.2 没有这个 getter</b> → 用
 *           {@code prevRotationPitch + (rotationPitch - prevRotationPitch) * partialTicks} 手算</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class KongVaultAnimator extends Animator {

    private float yRotDifference = 0;
    private float yRotDifferenceOld = 0;

    float getFactor(float phase) {
        if (phase < 0.5) {
            return EasingFunctions.SinInOutBySquare(phase * 2);
        } else {
            return EasingFunctions.SinInOutBySquare(2 - phase * 2);
        }
    }

    float getArmFactor(float phase) {
        return phase < 0.2 ?
                1 - 25 * (phase - 0.2f) * (phase - 0.2f) :
                1 - EasingFunctions.SinInOutBySquare((phase - 0.2f) * 1.25f);
    }

    /** 1.12.2 没有 {@code getViewXRot(partialTicks)} —— 手算插值后的俯仰角。 */
    private static float getViewXRot(EntityPlayer player, float partialTicks) {
        return player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
    }

    @Override
    public void tick(EntityPlayer player) {
        super.tick(player);
        yRotDifferenceOld = yRotDifference;
        Vec3d currentAngle = VectorUtil.fromYawDegree(player.renderYawOffset);
        Vec3d oldAngle = VectorUtil.fromYawDegree(player.prevRenderYawOffset);
        yRotDifference = (float) Math.atan(
                (oldAngle.x * currentAngle.z - currentAngle.x * oldAngle.z)
                        / (currentAngle.x * oldAngle.x + currentAngle.z * oldAngle.z)
        );
    }

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return getTick() >= Vault.MAX_TICK;
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float phase = (getTick() + transformer.getPartialTick()) / Vault.MAX_TICK;
        float armFactor = getArmFactor(phase);
        float factor = getFactor(phase);
        float animFactor = new Easing(phase)
                .sinInOut(0, 0.15f, 0, 1)
                .linear(0.15f, 0.85f, 1, 1)
                .sinInOut(0.85f, 1, 1, 0)
                .get();
        float difference = MathUtil.lerp(yRotDifferenceOld, yRotDifference, transformer.getPartialTick());
        transformer
                .translateLeftLeg(0, -0.7f * factor, -0.9f * factor)
                .translateRightLeg(0, -0.7f * factor, -0.9f * factor)
                .rotateAdditionallyHeadPitch(-40 * armFactor)
                .rotateRightArm((float) toRadians(30 - 195 * armFactor), 0, (float) toRadians(30 - 30 * armFactor), animFactor)
                .rotateLeftArm((float) toRadians(25 - 195 * armFactor), 0, (float) toRadians(-30 + 30 * armFactor), animFactor)
                .rotateRightLeg(
                        (float) toRadians(Easing.with(phase)
                                .squareOut(0, 0.1f, 5, -5)
                                .sinInOut(0.1f, 0.47f, -5, 25)
                                .sinInOut(0.47f, 0.9f, 25, -25)
                                .sinInOut(0.9f, 1f, -25, 0)
                                .get()
                        ),
                        0,
                        difference - (float) Math.toRadians(5. * factor),
                        animFactor
                )
                .rotateLeftLeg(
                        (float) toRadians(Easing.with(phase)
                                .sinInOut(0, 0.33f, -20, 20)
                                .sinInOut(0.33f, 0.79f, 20, -45)
                                .sinInOut(0.79f, 1f, -45, 0)
                                .get()
                        ),
                        0,
                        difference + (float) Math.toRadians(5. * factor),
                        animFactor)
                .makeLegsLittleMoving()
                .end();
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float phase = (getTick() + rotator.getPartialTick()) / Vault.MAX_TICK;
        float factor = getFactor(phase);
        float yFactor = new Easing(phase)
                .squareOut(0, 0.5f, 0, 1)
                .squareIn(0.5f, 1, 1, 0)
                .get();
        rotator
                .startBasedCenter()
                .translateY(-yFactor * player.height / 5)
                .rotatePitchFrontward(factor * 95)
                .end();
    }

    @Override
    public void onCameraSetUp(EntityViewRenderEvent.CameraSetup event, EntityPlayer clientPlayer,
                              Parkourability parkourability) {
        if (Minecraft.getMinecraft().gameSettings.thirdPersonView != 0
                || !ParCoolConfig.Client.Booleans.EnableCameraAnimationOfVault.get()) {
            return;
        }
        float phase = (float) ((getTick() + event.getRenderPartialTicks()) / Vault.MAX_TICK);
        float factor = getFactor(phase);
        event.setPitch(30 * factor + getViewXRot(clientPlayer, (float) event.getRenderPartialTicks()));
    }
}
