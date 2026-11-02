package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.HideInBlock;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.Easing;
import com.alrex.parcool.utilities.MathUtil;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 藏进方块里的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>两种姿态：<b>站立</b>（站着缩进方块）与<b>趴伏</b>（整个人伏下去）✓
 * 过渡时长不同：从俯冲接进来只要 2 tick、正常进入要 5 tick ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td><b>{@code transformer.getRawModel().getHead().yRot}</b></td>
 *       <td><b>1.12.2 的模型字段是公开的</b> → {@code transformer.getRawModel().bipedHead.rotateAngleY} ✓</td></tr>
 *   <tr><td>{@code player.yRotO / player.yRot}</td>
 *       <td>{@code player.prevRotationYaw / player.rotationYaw}</td></tr>
 *   <tr><td>{@code MathHelper.clamp(d, d, d)}</td><td>同名存在 ✓</td></tr>
 *   <tr><td>{@code MathUtil.normalizeDegree(x)}</td>
 *       <td>{@code MathUtil.normalizeDegrees(x)}（<b>本工程的方法名多了个 s</b>）✓</td></tr>
 *   <tr><td><b>{@code MathHelper.lerp(t, a, b)}</b></td>
 *       <td><b>1.12.2 的 {@code MathHelper} 根本没有 {@code lerp}</b> ✗
 *           （只有 {@code clampedLerp(lower, upper, slide)}，<b>参数顺序也不同</b> ✗）<br>
 *           → 改用本工程的 {@code MathUtil.lerp} ✓</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class HideInBlockAnimator extends Animator {

    private final boolean standing;
    private final boolean startFromDiving;

    public HideInBlockAnimator(boolean standing, boolean startFromDiving) {
        this.standing = standing;
        this.startFromDiving = startFromDiving;
    }

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(HideInBlock.class).isDoing();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float phase = (getTick() + transformer.getPartialTick()) / (startFromDiving ? 2f : 5f);
        if (phase > 1f) phase = 1f;
        float factor = Easing.with(phase).sinInOut(0f, 1f, 0, 1f).get();
        if (standing) {
            transformer
                    .rotateHeadYawRadian((float)
                            (MathHelper.clamp(transformer.getRawModel().bipedHead.rotateAngleY, -Math.PI / 4., Math.PI / 4.) / 1.4f))
                    .rotateRightArm(0, 0, (float) Math.toRadians(6), factor)
                    .rotateLeftArm(0, 0, (float) Math.toRadians(-6), factor)
                    .makeArmsNatural()
                    .rotateRightLeg(0, 0, 0)
                    .rotateLeftLeg(0, 0, 0);
            // 注意：这里上游没有 .end() —— 与不站立分支共用结尾 ✓ 照搬
            transformer.end();
        } else {
            transformer.rotateHeadPitch(-40 * factor)
                    .rotateHeadYawRadian((float)
                            (MathHelper.clamp(transformer.getRawModel().bipedHead.rotateAngleY, -Math.PI / 5., Math.PI / 5.) / 2.))
                    .rotateLeftArm((float) Math.toRadians(-15), 0, (float) Math.toRadians(-160), factor)
                    .rotateRightArm((float) Math.toRadians(-15), 0, (float) Math.toRadians(160), factor)
                    .translateLeftLeg(0, -0.8f * factor, -0.7f * factor)
                    .translateRightLeg(0, -0.8f * factor, -0.7f * factor)
                    .rotateLeftLeg((float) Math.toRadians(-15), 0, (float) Math.toRadians(-10), factor)
                    .rotateRightLeg((float) Math.toRadians(-15), 0, (float) Math.toRadians(10), factor)
                    .end();
        }
    }

    @Override
    public boolean rotatePre(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float phase = (getTick() + rotator.getPartialTick()) / 5f;
        if (phase > 1f) phase = 1f;
        float factor = Easing.with(phase).sinInOut(0f, 1f, 0, 1f).get();
        Vec3d lookVec = parkourability.get(HideInBlock.class).getLookDirection();
        if (lookVec == null) return false;
        if (standing) {
            // 把身体「转正」到与视线相差 180° 的位置（面向方块里面）—— 只转差值的一部分，所以是平滑的 ✓
            float playerYRot = 180f + MathUtil.lerp(rotator.getPartialTick(), player.prevRotationYaw, player.rotationYaw);
            float yRot = (float) VectorUtil.toYawDegree(lookVec);
            rotator.rotateYawRightward(
                    playerYRot + MathUtil.normalizeDegrees((180f + yRot) - playerYRot) * factor);
            return true;
        } else {
            float yRot = (float) VectorUtil.toYawDegree(lookVec);
            rotator.rotateYawRightward(180f + yRot)
                    .rotatePitchFrontward(startFromDiving ? 180f - 90f * factor : (90f * factor))
                    .translate(0, -0.95f * factor, 0.3f * factor);
            return true;
        }
    }
}
