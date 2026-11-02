package com.alrex.parcool.common.zipline.impl;

import com.alrex.parcool.common.zipline.Zipline;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 直线滑索 ✓ —— **纯数学**，1.12.2 只需换 {@code Vector3d} → {@link Vec3d}。
 *
 * <p>两点之间拉一条直线，是最「紧」的滑索 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code Vector3d}（{@code x()/y()/z()}）</td>
 *       <td>{@link Vec3d}（{@code .x/.y/.z} 字段 ✓）</td></tr>
 *   <tr><td>{@code MathHelper.fastInvSqrt}</td>
 *       <td><b>同名存在 ✓</b>（{@code MathHelper.java:571}）</td></tr>
 * </table>
 *
 * <p><b>⚠️ 注意 {@code slope} 是「字段初始化式」</b> ✗ —— 它在构造期求值 ✓
 * （依赖父类已赋值的字段 ✓ 父类构造器先跑完 ✓ 所以安全 ✓），照搬 ✓</p>
 */
public class StraightZipline extends Zipline {

    public StraightZipline(Vec3d point1, Vec3d point2) {
        super(point1, point2);
    }

    /** 双精度版 —— 是{@code 重载}而非覆盖（基类只有 float 版 ✓），照搬上游 ✓ */
    public Vec3d getMidPoint(double t) {
        return getMidPointOffsetFromStart(t).add(getStartPos());
    }

    @Override
    public Vec3d getMidPointOffsetFromStart(float t) {
        return getOffsetToEndFromStart().scale(t);
    }

    public Vec3d getMidPointOffsetFromStart(double t) {
        return getOffsetToEndFromStart().scale(t);
    }

    private final float slope = (float) (getOffsetToEndFromStart().y * MathHelper.fastInvSqrt(
            getOffsetToEndFromStart().x * getOffsetToEndFromStart().x
                    + getOffsetToEndFromStart().z * getOffsetToEndFromStart().z
    ));

    @Override
    public float getSlope(float t) {
        return slope;
    }

    @Override
    public float getParameter(Vec3d position) {
        return (float) getParameterD(position);
    }

    public double getParameterD(Vec3d position) {
        double xOffset = getOffsetToEndFromStart().x;
        double yOffset = getOffsetToEndFromStart().y;
        double zOffset = getOffsetToEndFromStart().z;
        double baseXOffset = getStartPos().x - position.x;
        double baseYOffset = getStartPos().y - position.y;
        double baseZOffset = getStartPos().z - position.z;
        return -(xOffset * baseXOffset + yOffset * baseYOffset + zOffset * baseZOffset)
                / (xOffset * xOffset + yOffset * yOffset + zOffset * zOffset);
    }

    @Override
    public double getMovedPositionByParameterApproximately(float currentT, float movement) {
        // ⚠️ 必须显式 cast：`MathHelper.fastInvSqrt` 在 1.16 返回 **float** ✓，
        //    但在 1.12.2 返回 **double** ✗（`MathHelper.java:571`）
        //    → 上游写 `float deltaXZ = movement * fastInvSqrt(...)` 能过 ✓
        //    → 直接照搬会因 float×double=double 无法赋给 float 而编译失败 ✗
        float deltaXZ = (float) (movement * MathHelper.fastInvSqrt(slope * slope + 1));
        return currentT + deltaXZ / getHorizontalDistance();
    }

    @Override
    public double getSquaredDistanceApproximately(Vec3d position, double yDistanceScale) {
        double t = getParameterD(position);
        Vec3d mostNearPoint = getMidPoint(t);
        double xOffset = mostNearPoint.x - position.x;
        double zOffset = mostNearPoint.z - position.z;
        double yOffset = (mostNearPoint.y - position.y) * yDistanceScale;
        return xOffset * xOffset + zOffset * zOffset + yOffset * yOffset;
    }
}
