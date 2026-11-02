package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.ClingToCliff;
import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 抓墙的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>三种朝向各有姿态：面朝墙（双臂上举抓住）、左/右侧身贴墙（身体倾斜 + 单臂抬起）✓</p>
 *
 * <p>1.12.2 替换：{@code PlayerEntity} → {@link EntityPlayer} + {@code @SideOnly(Side.CLIENT)}，其余不动 ✓</p>
 */
@SideOnly(Side.CLIENT)
public class ClingToCliffAnimator extends Animator {

    private static final float LEAN_ANGLE = 20;

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(ClingToCliff.class).isDoing();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        ClingToCliff cling = parkourability.get(ClingToCliff.class);
        switch (cling.getFacingDirection()) {
            case ToWall:
                // 手臂小幅摆动 = 抓握手感（用累计摆动量做正弦）
                double zAngle = 10 + 20 * Math.sin(24 * cling.getArmSwingAmount());
                transformer
                        .rotateLeftArm((float) Math.toRadians(-155), 0, (float) Math.toRadians(zAngle))
                        .rotateRightArm((float) Math.toRadians(-155), 0, (float) Math.toRadians(-zAngle))
                        .makeArmsNatural()
                        .rotateRightLeg(0, 0, 0)
                        .rotateLeftLeg(0, 0, 0)
                        .makeLegsLittleMoving()
                        .end();
                break;
            case RightAgainstWall:
                transformer
                        .rotateAdditionallyHeadPitch(-10)
                        .rotateRightArm(0, 0, (float) Math.toRadians(LEAN_ANGLE))
                        .makeArmsNatural()
                        .rotateLeftArm(0, 0, (float) Math.toRadians(-100))
                        .rotateLeftLeg(6, 0, (float) Math.toRadians(-15))
                        .rotateRightLeg(-6, 0, (float) Math.toRadians(-25))
                        .end();
                break;
            case LeftAgainstWall:
                transformer
                        .rotateAdditionallyHeadPitch(-10)
                        .rotateLeftArm(0, 0, (float) Math.toRadians(-LEAN_ANGLE))
                        .makeArmsNatural()
                        .rotateRightArm(0, 0, (float) Math.toRadians(100))
                        .rotateLeftLeg(-6, 0, (float) Math.toRadians(25))
                        .rotateRightLeg(6, 0, (float) Math.toRadians(15))
                        .end();
                break;
            default:
                break;
        }
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        ClingToCliff cling = parkourability.get(ClingToCliff.class);
        switch (cling.getFacingDirection()) {
            case RightAgainstWall:
                rotator.startBasedCenter()
                        .rotateRollRightward(LEAN_ANGLE)
                        .end();
                break;
            case LeftAgainstWall:
                rotator.startBasedCenter()
                        .rotateRollRightward(-LEAN_ANGLE)
                        .end();
                break;
            default:
                break;
        }
    }
}
