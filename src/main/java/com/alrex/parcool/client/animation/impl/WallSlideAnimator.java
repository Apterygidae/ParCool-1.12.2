package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.WallSlide;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 滑墙的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>只看一件事：身体朝向与墙面法向的相对关系决定<b>举哪只手</b>撑住墙
 * （二维叉积 `dividedVec.z` 的符号）✓</p>
 *
 * <p>1.12.2 替换：{@code PlayerEntity} → {@link EntityPlayer}、
 * {@code Vector3d} → {@link Vec3d}（字段访问）、{@code player.yBodyRot} → {@code renderYawOffset} ✓</p>
 */
@SideOnly(Side.CLIENT)
public class WallSlideAnimator extends Animator {

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(WallSlide.class).isDoing();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        Vec3d wall = parkourability.get(WallSlide.class).getLeanedWallDirection();
        if (wall == null) return;
        Vec3d bodyVec = VectorUtil.fromYawDegree(player.renderYawOffset);
        Vec3d vec = new Vec3d(bodyVec.x, 0, bodyVec.z).normalize();

        Vec3d dividedVec =
                new Vec3d(
                        vec.x * wall.x + vec.z * wall.z, 0,
                        -vec.x * wall.z + vec.z * wall.x
                ).normalize();
        if (dividedVec.z < 0) {
            transformer
                    .rotateRightArm(
                            (float) Math.toRadians(-160),
                            (float) -Math.toRadians(VectorUtil.toYawDegree(dividedVec) + 90),
                            0
                    )
                    .end();
        } else {
            transformer
                    .rotateLeftArm(
                            (float) Math.toRadians(-160),
                            (float) -Math.toRadians(VectorUtil.toYawDegree(dividedVec) + 90),
                            0
                    )
                    .end();
        }
    }
}
