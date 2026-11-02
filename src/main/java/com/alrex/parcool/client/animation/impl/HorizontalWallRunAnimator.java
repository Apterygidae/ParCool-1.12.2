package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.HorizontalWallRun;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 横向跑墙的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>「墙在左 / 墙在右」两个分支互为镜像：贴墙那侧的手臂撑墙、外侧手臂摆动，
 * 双腿也朝贴墙侧倾斜 ✓ 腿的摆动直接读 {@code limbSwing}，所以能和跑步动画自然衔接 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code mc.options.getCameraType().isFirstPerson()}</td>
 *       <td>{@code Minecraft.getMinecraft().gameSettings.thirdPersonView == 0}</td></tr>
 *   <tr><td>{@code MathHelper}</td><td>同名存在 ✓（{@code net.minecraft.util.math.MathHelper}）</td></tr>
 *   <tr><td>{@code MathUtil.squaring}</td><td>已在本工程补齐 ✓</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class HorizontalWallRunAnimator extends Animator {

    final boolean wallIsRightSide;

    public HorizontalWallRunAnimator(boolean wallIsRightSide) {
        this.wallIsRightSide = wallIsRightSide;
    }

    private float limbSwing = 0;

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(HorizontalWallRun.class).isDoing();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        limbSwing = transformer.getLimbSwing();
        float factor = getFactor(getTick() + transformer.getPartialTick());
        float angle = factor * 15f * (wallIsRightSide ? -1f : 1f);
        float armSwingPhase = limbSwing * 0.6662f;
        transformer
                .rotateAdditionallyHeadPitch(-15 * factor)
                .rotateAdditionallyHeadRoll(angle);
        if (wallIsRightSide) {
            transformer
                    .addRotateLeftArm(0, 0, (float) Math.toRadians(-30))
                    .makeArmsNatural()
                    .rotateRightArm(
                            (float) Math.toRadians(20 - 8d * MathHelper.cos(armSwingPhase)),
                            0,
                            (float) Math.toRadians(110)
                    )
                    .translateRightArm(-1f, 0, 0.8f - 0.5f * MathHelper.cos(armSwingPhase))
                    .addRotateLeftArm(
                            (float) Math.toRadians(-10), 0,
                            (float) -Math.toRadians(35 + 5 * MathHelper.sin(armSwingPhase)), factor
                    )
                    .translateLeftArm(0, 1.0f, 0)
                    .addRotateRightLeg(0, 0, (float) Math.toRadians(17))
                    .addRotateLeftLeg(0, 0, (float) Math.toRadians(25))
                    .rotateAdditionallyHeadYaw(-5f + 8f * MathHelper.cos(armSwingPhase))
                    .end();
        } else {
            transformer
                    .addRotateRightArm(0, 0, (float) Math.toRadians(30))
                    .makeArmsNatural()
                    .rotateLeftArm(
                            (float) Math.toRadians(20 - 8d * MathHelper.cos(armSwingPhase)),
                            0,
                            (float) Math.toRadians(-110)
                    )
                    .translateLeftArm(1f, 0, 0.8f - 0.5f * MathHelper.cos(armSwingPhase))
                    .addRotateRightArm(
                            (float) Math.toRadians(-10), 0,
                            (float) Math.toRadians(35 + 5 * MathHelper.sin(armSwingPhase)), factor
                    )
                    .translateRightArm(0, 1.0f, 0)
                    .addRotateRightLeg(0, 0, (float) Math.toRadians(-25))
                    .addRotateLeftLeg(0, 0, (float) Math.toRadians(-17))
                    .rotateAdditionallyHeadYaw(5f - 8f * MathHelper.cos(armSwingPhase))
                    .end();
        }
    }

    /** 前 5 tick 由 0 爬到 1，之后保持 —— 即「迅速进入跑墙姿态」✓ */
    private float getFactor(float tick) {
        return tick < 5 ? 1 - MathUtil.squaring((5 - tick) / 5) : 1;
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float factor = getFactor(getTick() + rotator.getPartialTick());
        float sign = wallIsRightSide ? -1 : 1;
        float angle = factor * 30f * sign;
        float yOffset = 0.145f * (float) Math.pow(MathHelper.cos(limbSwing * 0.6662f), 2.);
        rotator.translateY(yOffset)
                .startBasedCenter()
                .rotateRollRightward(angle)
                .rotatePitchFrontward(20 * factor)
                .rotateYawRightward(sign * (-5f + 8f * MathHelper.cos(limbSwing * 0.66662f)))
                .end();
    }

    @Override
    public void onCameraSetUp(EntityViewRenderEvent.CameraSetup event, EntityPlayer clientPlayer,
                              Parkourability parkourability) {
        if (Minecraft.getMinecraft().gameSettings.thirdPersonView != 0
                || !ParCoolConfig.Client.Booleans.EnableCameraAnimationOfHWallRun.get()) {
            return;
        }
        float factor = getFactor((float) (getTick() + event.getRenderPartialTicks()));
        float angle = factor * 20 * (wallIsRightSide ? -1 : 1);
        event.setRoll(angle);
    }
}
