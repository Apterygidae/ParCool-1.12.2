package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.RideZipline;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.MathUtil;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 「滑滑索」的玩家姿态 ✓ —— 1.12.2 版。
 *
 * <h3>★ 姿态做什么</h3>
 * <ul>
 *   <li>双臂**向上举起** ✓（抓住头顶的索 ✓）</li>
 *   <li>双腿**随索的倾角摆动** ✓ —— 上坡时腿朝后 ✓、下坡时朝前 ✓</li>
 *   <li>整个身体**沿索的走向倾斜** ✓（沿水平面内的「垂直于索」的轴旋转 ✓）</li>
 *   <li>**身体朝向锁死为视线方向** ✗ → 转身时整个人跟着转 ✓（否则会拧麻花 ✓）</li>
 * </ul>
 *
 * <h3>🚨 1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code ForgeMod.ENTITY_GRAVITY.get()}</td><td>常量 {@code 0.08} ✗</td></tr>
 *   <tr><td>{@code MathHelper.lerp(factor, from, to)}</td>
 *       <td><b>不存在</b> ✗ → {@link MathUtil#lerpBy}（参数顺序正好对应 ✓）</td></tr>
 *   <tr><td>{@code player.yBodyRot / yBodyRotO}</td>
 *       <td>{@code renderYawOffset / prevRenderYawOffset} ✗</td></tr>
 *   <tr><td>{@code player.yRot / yRotO}</td>
 *       <td>{@code rotationYaw / prevRotationYaw} ✗</td></tr>
 *   <tr><td>{@code Vector3f}（旋转轴）</td>
 *       <td><b>没有</b> ✗ → {@link Vec3d} ✓</td></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code getLookVec()} ✗</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class RideZiplineAnimator extends Animator {

    /** 与动作里的常量保持一致 ✓（1.12.2 没有重力属性 ✗）。 */
    private static final double GRAVITY = 0.08;

    private double oldAngleRadian = 0;
    private double currentAngleRadian = 0;

    @Override
    public void tick(EntityPlayer player) {
        super.tick(player);

        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        RideZipline action = parkourability.get(RideZipline.class);
        if (!action.isDoing()) return;

        oldAngleRadian = currentAngleRadian;

        // ★ 算出「身体应该倾斜多少」✓
        //   思路：把「重力 + 沿索加速度」合成一个方向 ✓，
        //   这个方向与索的切线方向相反 → 就是身体的摆角 ✓
        double acceleration = action.getAcceleration();
        double slope = action.getSlope();
        double invSqrt = MathHelper.fastInvSqrt(slope * slope + 1);
        double alongTrack = -acceleration * invSqrt;          // 水平分量 ✓
        double vertical = GRAVITY + acceleration * slope * invSqrt;  // 竖直分量 ✓
        // ⚠️ 平滑过渡 ✓（0.1 的插值系数 ✓），并取 atan2 得到角度 ✓
        currentAngleRadian = MathUtil.lerpBy(0.1, oldAngleRadian, Math.atan2(alongTrack, vertical));
    }

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(RideZipline.class).isDoing();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        Vec3d offset = parkourability.get(RideZipline.class).getEndOffsetFromStart();
        if (offset == null) return;

        // ★ 视线方向与「索的水平走向」的夹角 ✓ —— 用来决定腿往哪边摆 ✓
        double angleDifference = VectorUtil.toYawRadian(player.getLookVec())
                - VectorUtil.toYawRadian(new Vec3d(offset.x, 0, offset.z));
        double angleCos = Math.cos(angleDifference);
        double angleSin = Math.sin(angleDifference);
        double angleCosAbs = Math.abs(angleCos);

        transformer
                // 双手举到头顶 ✓（平移 + 整条手臂后转 180° ✓）
                .translateRightArm(0.6f, -2.2f, 0)
                .translateLeftArm(-0.6f, -2.2f, 0)
                .rotateRightArm((float) -Math.PI, 0f,
                        (float) Math.toRadians(MathUtil.lerpBy((float) angleCosAbs, 10f, 15f)))
                .rotateLeftArm((float) -Math.PI, 0f,
                        (float) Math.toRadians(MathUtil.lerpBy((float) angleCosAbs, -10f, -15f)))
                // 双腿随身体摆角摆动 ✓（正面朝索时摆得最明显 ✓）
                .rotateRightLeg((float) (-currentAngleRadian * angleCos), 0,
                        (float) (-currentAngleRadian * angleSin))
                .rotateLeftLeg((float) (-currentAngleRadian * angleCos), 0,
                        (float) (-currentAngleRadian * angleSin))
                .makeLegsLittleMoving()
                .end();
    }

    /**
     * 把**身体朝向锁死为视线方向** ✗ ✓。
     *
     * <p>1.12.2 用 {@code renderYawOffset}（身体）与 {@code rotationYaw}（头）两个字段 ✓ ——
     * 平时身体会「跟着移动方向慢慢转」✓，滑索时那样会拧 ✗，
     * 所以直接让身体 = 视线 ✓。</p>
     */
    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        player.renderYawOffset = player.rotationYaw;
        player.prevRenderYawOffset = player.prevRotationYaw;
    }

    /**
     * 整体的**侧倾** ✓ —— 沿「垂直于索的水平轴」旋转 ✓。
     *
     * <p>旋转轴取「索的水平方向」绕 Y 转 90° ✓，
     * 于是身体会朝索的**垂直方向**倒 ✓（像吊在缆车上那样 ✓）。</p>
     */
    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        Vec3d offset = parkourability.get(RideZipline.class).getEndOffsetFromStart();
        if (offset == null) return;

        // ⚠️ 1.12.2 没有 Vector3f ✗ → 直接用 Vec3d 当轴 ✓
        Vec3d axis = new Vec3d(0, 0, 1)
                .rotateYaw((float) (Math.PI / 2
                        + VectorUtil.toYawRadian(player.getLookVec())
                        - VectorUtil.toYawRadian(new Vec3d(offset.x, 0, offset.z))))
                .normalize();
        // 用 partialTick 在两帧的摆角之间插值 ✓（避免抖动 ✓）
        double angle = MathUtil.lerpBy(rotator.getPartialTick(), oldAngleRadian, currentAngleRadian);

        rotator.startBasedTop()
                .rotate((float) angle, new Vec3d(axis.x, 0, axis.z))
                .end();
    }
}
