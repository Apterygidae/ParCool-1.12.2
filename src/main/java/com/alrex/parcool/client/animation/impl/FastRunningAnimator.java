package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.FastRun;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.BipedModelUtil;
import com.alrex.parcool.utilities.Easing;
import com.alrex.parcool.utilities.MathUtil;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * FastRun 的第三人称动画（1.12.2 版）—— 数学逻辑 1:1 保留，只替换模型/玩家 API。
 *
 * <h3>1.12.2 的替换（本类碰到的全部）</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code getRawModel().attackTime}</td>
 *       <td><b>{@code getRawModel().swingProgress}</b>（1.12.2 的名字）</td></tr>
 *   <tr><td>{@code BipedModel.ArmPose}</td>
 *       <td><b>{@code ModelBiped.ArmPose}</b>（内部枚举，1.12.2 挂在 ModelBiped 上）</td></tr>
 *   <tr><td>{@code renderer.xRot}</td><td>{@code renderer.rotateAngleX}</td></tr>
 *   <tr><td>{@code HandSide}</td><td>{@link EnumHandSide}</td></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code player.getLookVec()}</td></tr>
 *   <tr><td>{@code lookAngle.x() / .y() / .z()}</td><td>{@code .x / .y / .z}（公开字段）</td></tr>
 *   <tr><td>{@code player.yBodyRotO / yBodyRot}</td>
 *       <td><b>{@code player.prevRenderYawOffset / player.renderYawOffset}</b></td></tr>
 *   <tr><td>{@code Minecraft.getInstance().screen}</td>
 *       <td>{@code Minecraft.getMinecraft().currentScreen}</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td><td>{@link ParCool#PROXY}{@code .isLocalPlayer(player)}</td></tr>
 * </table>
 *
 * <p>{@code BipedModelUtil} / {@code Easing} / {@code VectorUtil} / {@code MathUtil}
 * 都已在本工程就位 —— 本类正是它们的第一个消费者。</p>
 */
@SideOnly(Side.CLIENT)
public class FastRunningAnimator extends Animator {

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(FastRun.class).isDoing();
    }

    private float bodyAngleFactor(float phase) {
        return new Easing(phase)
                .squareOut(0, 1, 0, 1)
                .get();
    }

    /**
     * 该手臂姿势是不是「双手持械」（因此不该被疾跑动画动它）。
     *
     * <h3>⚠️ 这是 1.12.2 的一处降级替换</h3>
     * <p>上游用 {@code ArmPose.isTwoHanded()} —— 那是 1.16 的方法。而 1.12.2 的
     * {@code ModelBiped.ArmPose} <b>只有 4 个成员，且没有任何方法</b>：</p>
     * <pre>
     * // ModelBiped.java:332
     * public static enum ArmPose { EMPTY, ITEM, BLOCK, BOW_AND_ARROW; }
     * </pre>
     * <p>1.16 的 {@code isTwoHanded()} 对 {@code BLOCK} / {@code CROSSBOW_CHARGE} / {@code SPYGLASS}
     * 返回 true —— 后两者是「弩」和「望远镜」的姿势，<b>1.12.2 根本没有这两个概念</b>
     * （1.14 才加入）。所以 1.12.2 里唯一的双手姿势就是 {@code BLOCK} ✓</p>
     * <p>这个降级是准确的，不是近似。</p>
     */
    private static boolean isTwoHandedPose(ModelBiped.ArmPose pose) {
        return pose == ModelBiped.ArmPose.BLOCK;
    }

    private float limbSwing = 0;

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        limbSwing = transformer.getLimbSwing();
        float phase = (getTick() + transformer.getPartialTick()) / 10;
        if (phase > 1) phase = 1;
        float bodyAngleFactor = bodyAngleFactor(phase);
        double rightXRotFactor = Math.cos(limbSwing * 0.6662 + Math.PI);
        double leftXRotFactor = Math.cos(limbSwing * 0.6662);
        EnumHandSide attackHand = BipedModelUtil.getAttackArm(player);
        boolean leftArmAnimatable = attackHand != EnumHandSide.LEFT || transformer.getRawModel().swingProgress <= 0f;
        boolean rightArmAnimatable = attackHand != EnumHandSide.RIGHT || transformer.getRawModel().swingProgress <= 0f;
        if (leftArmAnimatable && ((
                transformer.getRawModel().leftArmPose != ModelBiped.ArmPose.EMPTY
                        && transformer.getRawModel().leftArmPose != ModelBiped.ArmPose.ITEM
        )
                || isTwoHandedPose(transformer.getRawModel().rightArmPose)
        )
        ) {
            leftArmAnimatable = false;
        }
        if (rightArmAnimatable && ((
                transformer.getRawModel().rightArmPose != ModelBiped.ArmPose.EMPTY
                        && transformer.getRawModel().rightArmPose != ModelBiped.ArmPose.ITEM
        )
                || isTwoHandedPose(transformer.getRawModel().leftArmPose)
        )
        ) {
            rightArmAnimatable = false;
        }

        if (leftArmAnimatable) {
            transformer.translateLeftArm(
                    (float) (-Math.abs(leftXRotFactor)),
                    bodyAngleFactor * 0.8f,
                    (float) (leftXRotFactor * 2.)
            );
        }

        if (rightArmAnimatable) {
            transformer.translateRightArm(
                    (float) (Math.abs(rightXRotFactor)),
                    bodyAngleFactor * 0.8f,
                    (float) (rightXRotFactor * 2)
            );
        }

        transformer
                .translateRightLeg(
                        0,
                        (float) (Math.min(0, transformer.getRawModel().bipedRightLeg.rotateAngleX / (Math.PI / 2.))),
                        (float) (transformer.getRawModel().bipedRightLeg.rotateAngleX / (Math.PI / 3.))
                )
                .translateLeftLeg(
                        0,
                        (float) (Math.min(0, transformer.getRawModel().bipedLeftLeg.rotateAngleX / (Math.PI / 2.))),
                        (float) (transformer.getRawModel().bipedLeftLeg.rotateAngleX / (Math.PI / 3.))
                );

        float bodyYaw = (float) (10. * Math.cos(limbSwing * 0.6662));
        float tick = getTick() + transformer.getPartialTick();
        if (leftArmAnimatable) {
            transformer
                    .rotateLeftArm(
                            (float) (Math.toRadians(-25 * bodyAngleFactor) + 1.2 * leftXRotFactor * transformer.getLimbSwingAmount()),
                            0,
                            (float) Math.toRadians(
                                    bodyAngleFactor * -15
                                            + (0.65 + Math.cos(limbSwing * 1.3324)) / 2. * Math.sin(limbSwing * 1.3324) * -30
                            )
                    );
        }
        if (rightArmAnimatable) {
            transformer
                    .rotateRightArm(
                            (float) (Math.toRadians(-25 * bodyAngleFactor) + 1.2 * rightXRotFactor * transformer.getLimbSwingAmount()),
                            0,
                            (float) Math.toRadians(
                                    bodyAngleFactor * 15
                                            + (0.65 + Math.cos(limbSwing * 1.3324 + Math.PI)) / 2. * Math.sin(limbSwing * 1.3324 + Math.PI) * 30
                            )
                    );
        }
        transformer
                .rotateAdditionallyHeadPitch(bodyAngleFactor * -30 - 5f * (float) Math.sin(Math.PI * tick / 10))
                .rotateAdditionallyHeadYaw(bodyYaw)
                .addRotateRightLeg(
                        (float) Math.toRadians(-15 * bodyAngleFactor),
                        (float) Math.toRadians(bodyYaw),
                        0
                )
                .addRotateLeftLeg(
                        (float) Math.toRadians(-15 * bodyAngleFactor),
                        (float) Math.toRadians(bodyYaw),
                        0
                )
                .end();
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float tick = getTick() + rotator.getPartialTick();
        float phase = tick / 10;
        if (phase > 1) phase = 1;
        float bodyYaw = (float) (-10. * Math.cos(limbSwing * 0.6662));
        float pitch = bodyAngleFactor(phase) * 30 + 5f * (float) Math.sin(Math.PI * tick / 10);
        float yOffset = 0.145f * (float) Math.pow(Math.cos(limbSwing * 0.6662), 2.);
        rotator.translateY(yOffset);
        if (parkourability.getClientInfo().get(ParCoolConfig.Client.Booleans.EnableLeanAnimationOfFastRun)) {
            if (ParCool.PROXY.isLocalPlayer(player) && Minecraft.getMinecraft().currentScreen != null) {
                // 本地玩家开着 GUI 时，视角朝向不可靠 → 只做俯仰，不做侧倾
                rotator
                        .startBasedCenter()
                        .rotatePitchFrontward(pitch)
                        .end();
            } else {
                Vec3d lookAngle = player.getLookVec();
                Vec3d bodyAngle = VectorUtil.fromYawDegree(
                        MathUtil.lerp(player.prevRenderYawOffset, player.renderYawOffset, rotator.getPartialTick()));
                Vec3d differenceVec =
                        new Vec3d(
                                lookAngle.x * bodyAngle.x + lookAngle.z * bodyAngle.z, 0,
                                -lookAngle.x * bodyAngle.z + lookAngle.z * bodyAngle.x
                        ).normalize();
                rotator
                        .startBasedCenter()
                        .rotatePitchFrontward(pitch)
                        .rotateYawRightward(bodyYaw)
                        .rotateRollRightward((float) (30. * phase * Math.asin(differenceVec.z)))
                        .end();
            }
        } else {
            rotator
                    .startBasedCenter()
                    .rotatePitchFrontward(pitch)
                    .end();
        }
    }
}
