package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.FastSwim;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.BipedModelUtil;
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
 * 快速游泳的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>双臂交替划水（用 {@code sin(π·tick/15)} 的相位差），双腿反向摆动 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <p>与 {@code FastRunningAnimator} 同一套 —— 见该类的表。要点：
 * {@code attackTime} → {@code swingProgress}、
 * {@code BipedModel.ArmPose} → {@code ModelBiped.ArmPose}（且用本地 {@code isTwoHandedPose} 辅助，
 * 因为 1.12.2 的枚举没有 {@code isTwoHanded()}）、
 * {@code yBodyRotO/yBodyRot} → {@code prevRenderYawOffset/renderYawOffset}、
 * {@code mc.screen} → {@code currentScreen} ✓</p>
 */
@SideOnly(Side.CLIENT)
public class FastSwimAnimator extends Animator {

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(FastSwim.class).isDoing();
    }

    /** 见 {@code FastRunningAnimator#isTwoHandedPose} 的解释：1.12.2 里唯一的双手姿势是 BLOCK。 */
    private static boolean isTwoHandedPose(ModelBiped.ArmPose pose) {
        return pose == ModelBiped.ArmPose.BLOCK;
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float animationFactor = (getTick() + transformer.getPartialTick()) / 10;
        if (animationFactor > 1) animationFactor = 1;
        float tick = getTick() + transformer.getPartialTick();
        double armFactor = Math.cos(Math.PI * tick / 15.);

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

        double leftArmXAngle = 45 * armFactor;
        double rightArmXAngle = -leftArmXAngle;
        if (rightArmXAngle < 0) rightArmXAngle = -3 * Math.sqrt(-rightArmXAngle);
        if (leftArmXAngle < 0) leftArmXAngle = -3 * Math.sqrt(-leftArmXAngle);
        if (rightArmAnimatable) {
            transformer.translateRightArm(
                    (float) Math.max(0, 2.4 * Math.sin(Math.PI * tick / 15.)),
                    (float) Math.max(0, 1.2 * Math.sin(Math.PI * tick / 15.)),
                    (float) (-1.2 * Math.sin(Math.PI * tick / 15.))
            );
        }

        if (leftArmAnimatable) {
            transformer.translateLeftArm(
                    -(float) Math.max(0, -2.4 * Math.sin(Math.PI * tick / 15.)),
                    (float) Math.max(0, -1.2 * Math.sin(Math.PI * tick / 15.)),
                    (float) (1.2 * Math.sin(Math.PI * tick / 15.))
            );
        }

        transformer
                .translateHead(0, 0, 0.5f * animationFactor)
                .rotateAdditionallyHeadYaw((float) (-5 * Math.sin(Math.PI * tick / 15.)))
                .rotateRightArm((float) Math.toRadians(-190 + rightArmXAngle), 0, (float) Math.toRadians(-40 + 50 * Math.sin(Math.PI * tick / 15.)))
                .rotateLeftArm((float) Math.toRadians(-190 + leftArmXAngle), 0, (float) Math.toRadians(40 + 50 * Math.sin(Math.PI * tick / 15.)));
        if (rightArmAnimatable) {
            transformer.rotateRightLeg((float) Math.toRadians(-40 * Math.cos(Math.PI * tick / 9.)), 0, 0, animationFactor);
        }
        if (leftArmAnimatable) {
            transformer.rotateLeftLeg((float) Math.toRadians(40 * Math.cos(Math.PI * tick / 9.)), 0, 0, animationFactor);
        }
        transformer.end();
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        // 本地玩家开着 GUI 时视角朝向不可靠 → 不做侧倾
        if (ParCool.PROXY.isLocalPlayer(player) && Minecraft.getMinecraft().currentScreen != null) {
            return;
        }
        float tick = getTick() + rotator.getPartialTick();
        Vec3d lookAngle = player.getLookVec();
        Vec3d bodyAngle = VectorUtil.fromYawDegree(
                MathUtil.lerp(player.prevRenderYawOffset, player.renderYawOffset, rotator.getPartialTick()));
        Vec3d differenceVec =
                new Vec3d(
                        lookAngle.x * bodyAngle.x + lookAngle.z * bodyAngle.z, 0,
                        -lookAngle.x * bodyAngle.z + lookAngle.z * bodyAngle.x
                ).normalize();

        rotator.startBasedCenter()
                .rotateYawRightward((float) (-15. * Math.asin(differenceVec.z) + 12.0 * Math.sin(Math.PI * tick / 15.)))
                .end();
    }
}
