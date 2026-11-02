package com.alrex.parcool.common.zipline.impl;

import com.alrex.parcool.common.zipline.Zipline;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/**
 * 一般二次曲线滑索 ✓ —— **纯数学**，1.12.2 只换 {@code Vector3d} → {@link Vec3d}
 * 与 {@code inflate} → {@code grow}。
 *
 * <p>与直线版的区别：曲线在中间下垂（{@code lowestPointOffset} 越大越松 ✓），
 * 所以移动距离需要**沿弧长**算，而不能简单按参数线性推进 ✓</p>
 *
 * <h3>★ 本类的数学核心</h3>
 * <ul>
 *   <li>曲线：{@code y(t) = a * t * (t - 2*tAtVertex)} ✓（一条以 {@code tAtVertex} 为顶点的抛物线 ✓）</li>
 *   <li>斜率：{@code dy/d(t*水平距离)} ✓</li>
 *   <li>**弧长**：解析式 {@code getDistance(xzLen, a)} ✓ ——
 *       含 {@code log(...)}，是「斜边积分」的闭式解 ✓</li>
 *   <li>**按弧长反推参数**：没有闭式解 ✗ → 用**牛顿迭代**（最多 20 次 ✓，收敛阈值 0.001 ✓）✓</li>
 * </ul>
 *
 * <p><b>⚠️ {@code |a| < 0.005} 时退化为线性</b> ✗ —— 因为弧长公式里 {@code a} 在分母上 ✗
 * （{@code getDistance} 的 {@code log / (2a)} ✗）→ 趋近直线时直接按直线处理 ✓ <b>照搬 ✓</b></p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><td>{@code Vector3d} 的 {@code x()/y()/z()}</td><td>{@code .x/.y/.z} 字段 ✓</td></tr>
 *   <tr><td>{@code AxisAlignedBB#inflate(d)}</td><td>{@code grow(d)} ✓（1.12.2 名字不同 ✗）</td></tr>
 * </table>
 */
public class GeneralQuadraticCurveZipline extends Zipline {

    private final double tAtVertex;
    private final double distOfXZToVertex;

    private final double getMidPointOffsetFromStart$a;
    private final double getMovedPositionByParameterApproximately$a;
    private final double getDistanceFrom0$offset;

    public GeneralQuadraticCurveZipline(Vec3d point1, Vec3d point2, double lowestPointOffset) {
        super(point1, point2);
        double yOffsetAtVertex = Math.abs(lowestPointOffset);

        tAtVertex = Math.abs(getOffsetToEndFromStart().y) < 0.005
                ? 0.5
                : (Math.sqrt(yOffsetAtVertex * (yOffsetAtVertex + getOffsetToEndFromStart().y)) - yOffsetAtVertex)
                / getOffsetToEndFromStart().y;
        distOfXZToVertex = tAtVertex * getHorizontalDistance();
        getMidPointOffsetFromStart$a = Math.abs(tAtVertex - 0.5) < 0.005
                ? 4 * yOffsetAtVertex
                : getOffsetToEndFromStart().y / (1 - 2 * tAtVertex);
        getMovedPositionByParameterApproximately$a =
                getOffsetToEndFromStart().y / (getHorizontalDistance() * getHorizontalDistance());
        getDistanceFrom0$offset = getDistance(-distOfXZToVertex, getMovedPositionByParameterApproximately$a);
    }

    @Override
    public Vec3d getMidPointOffsetFromStart(float t) {
        return new Vec3d(
                getOffsetToEndFromStart().x * t,
                getMidPointOffsetFromStart$a * t * (t - 2 * tAtVertex),
                getOffsetToEndFromStart().z * t
        );
    }

    @Override
    public float getSlope(float t) {
        return (float) (2 * (t - tAtVertex) * getMidPointOffsetFromStart$a / getHorizontalDistance());
    }

    @Override
    public float getParameter(Vec3d position) {
        double offsetX = getOffsetToEndFromStart().x;
        double offsetZ = getOffsetToEndFromStart().z;
        return (float) (((position.x - getStartPos().x) * offsetX + (position.z - getStartPos().z) * offsetZ)
                / (getHorizontalDistance() * getHorizontalDistance()));
    }

    /** 从 {@code xzLen=0} 起的**弧长**闭式解 ✓ */
    private double getDistance(double xzLen, double a) {
        double r = Math.sqrt(1 + 4 * a * a * xzLen * xzLen);
        return 0.5 * (xzLen * r + Math.log(Math.abs(2 * a * xzLen + r)) / (2 * a));
    }

    /** 从顶点起算的弧长 ✓ */
    private double getDistanceFrom0(double xzLen, double a) {
        return getDistance(xzLen - distOfXZToVertex, a) - getDistanceFrom0$offset;
    }

    /** 弧长对 xzLen 的导数 = 弧长微元 ✓ —— 牛顿迭代要用 ✓ */
    private double getDistanceFrom0Derivative(double xzLen, double a) {
        xzLen = xzLen - distOfXZToVertex;
        return Math.sqrt(1 + 4 * a * a * xzLen * xzLen);
    }

    @Override
    public double getMovedPositionByParameterApproximately(float currentT, float movement) {
        // 沿二次曲线的「按弧长前进」没有闭式反函数 ✗ → 牛顿迭代 ✓
        // （上游注释提到悬链线可以弧长参数化 ✓ 但这里没走那条路 ✓ 照搬 ✓）
        double xzLength = getHorizontalDistance();
        double a = getMovedPositionByParameterApproximately$a;

        // 趋近直线时直接线性推进 —— 同时避免 a 在分母上的除零 ✗
        if (Math.abs(a) < 0.005) {
            return (movement / xzLength) + currentT;
        }

        double destination = getDistanceFrom0(currentT * xzLength, a) + movement;
        double oldInterim;
        double interim = currentT * xzLength;
        for (int i = 0; i < 20; i++) {
            oldInterim = interim;
            interim -= (getDistanceFrom0(interim, a) - destination) / getDistanceFrom0Derivative(interim, a);
            if (Math.abs(oldInterim - interim) < 0.001) {
                return interim / xzLength;
            }
        }
        return interim / xzLength;
    }

    @Override
    public double getSquaredDistanceApproximately(Vec3d position, double yDistanceScale) {
        float t = getParameter(position);
        Vec3d simplifiedNearestPoint = getMidPoint(t);
        double xOffset = simplifiedNearestPoint.x - position.x;
        double zOffset = simplifiedNearestPoint.z - position.z;
        double yOffset = (simplifiedNearestPoint.y - position.y) * yDistanceScale;
        return xOffset * xOffset + zOffset * zOffset + yOffset * yOffset;
    }

    /** 曲线版要用**下垂最低点**作为盒子下界（直线版用起点 ✓）—— 否则挂在弧底时判不到 ✓ */
    @Override
    public boolean isPossiblyHangable(Vec3d position) {
        return new AxisAlignedBB(
                getStartPos().x, getMidPoint((float) tAtVertex).y, getStartPos().z,
                getEndPos().x, getEndPos().y, getEndPos().z)
                .grow(1d)
                .contains(position);
    }
}
