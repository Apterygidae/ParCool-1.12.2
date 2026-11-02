package com.alrex.parcool.common.zipline;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 滑索的**几何模型**（抽象基类）✓ —— 1.12.2 版。
 *
 * <h3>★ 它是什么</h3>
 * <p>一条滑索由**两个挂钩点**决定 ✓，本类负责回答几何问题：</p>
 * <ul>
 *   <li>{@code getMidPoint(t)} —— 参数 {@code t} 处的空间点 ✓（{@code t=0} 起点、{@code t=1} 终点 ✓）</li>
 *   <li>{@code getSlope(t)} —— 斜率（{@code dy/d(水平距离)} ✓）→ 用来算重力加速 ✓</li>
 *   <li>{@code getParameter(position)} —— 反过来：给定空间点求 {@code t} ✓</li>
 *   <li>{@code getMovedPositionByParameterApproximately(t, movement)} —— **沿索前进** ✓
 *       （直线可线性算 ✓；曲线要沿弧长 ✗ 见子类 ✓）</li>
 *   <li>{@code getSquaredDistanceApproximately(position, yScale)} —— 估算「离索多近」✓
 *       （用来判定玩家能否抓住 ✓）</li>
 * </ul>
 *
 * <h3>★ 一个重要的构造约定</h3>
 * <p>构造时**保证 {@code startPos.y <= endPos.y}** ✗→✓（两点按高度交换 ✓）。
 * 所以 {@code t} 的方向是「**从低到高**」✓，不是「从挂的那头到另一头」✓。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><td>{@code Vector3d}（{@code x()/y()/z()}）</td><td>{@link Vec3d}（{@code .x/.y/.z} ✓）</td></tr>
 *   <tr><td>{@code AxisAlignedBB#inflate(d)}</td><td>{@code grow(d)} ✓</td></tr>
 *   <tr><td>{@code world.noCollision(aabb)}</td>
 *       <td>{@code world.getCollisionBoxes(null, aabb).isEmpty()} ✓
 *           （1.12.2 的 {@code noCollision} 要带实体 ✗，这里没有实体 → 传 null ✓）</td></tr>
 * </table>
 *
 * <p><b>⚠️ {@code getHangableZipline} 那两个静态方法本批<b>未移植</b></b> ✗ ——
 * 它们依赖 {@code ZiplineRopeEntity} ✓（下一批 ✓）。目前 {@code RideZipline} 动作也用不到 ✓。</p>
 */
public abstract class Zipline {

    public static final double MAXIMUM_HORIZONTAL_DISTANCE = 115.;
    public static final double MAXIMUM_VERTICAL_DISTANCE = MAXIMUM_HORIZONTAL_DISTANCE * 0.51;

    private final Vec3d startPos;
    private final Vec3d endPos;
    private final Vec3d endOffsetFromStart;
    private final double horizontalDistance;

    protected Zipline(Vec3d point1, Vec3d point2) {
        // ★ 保证起点更低 ✓ —— 于是 t 的方向是「从低到高」✓
        if (point1.y <= point2.y) {
            this.startPos = point1;
            this.endPos = point2;
        } else {
            this.startPos = point2;
            this.endPos = point1;
        }
        endOffsetFromStart = endPos.subtract(startPos);
        horizontalDistance = Math.hypot(endOffsetFromStart.x, endOffsetFromStart.z);
    }

    public Vec3d getStartPos() {
        return startPos;
    }

    public Vec3d getEndPos() {
        return endPos;
    }

    public Vec3d getOffsetToEndFromStart() {
        return endOffsetFromStart;
    }

    public double getHorizontalDistance() {
        return horizontalDistance;
    }

    /** 滑索中段有没有被方块挡住 ✓（挡住的索不能架 ✓）。 */
    public boolean conflictsWithSomething(World world) {
        int count = (int) Math.floor(getHorizontalDistance());
        for (int i = 1; i < count - 1; i++) {
            Vec3d midPoint = getMidPoint(((float) i / count));
            final double d = 0.2;
            AxisAlignedBB box = new AxisAlignedBB(
                    midPoint.x - d, midPoint.y - d, midPoint.z - d,
                    midPoint.x + d, midPoint.y + d, midPoint.z + d);
            if (!world.getCollisionBoxes(null, box).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ── 参数 t 的语义 ─────────────────────────────────────────────────────
    // t=0 → startPos（低的那端）✓   t=1 → endPos（高的那端）✓
    // x/z 在 t 上线性插值 ✓   y 由子类的曲线形状决定 ✓

    /** 参数 {@code t} 处的空间点 ✓ */
    public Vec3d getMidPoint(float t) {
        return getMidPointOffsetFromStart(t).add(getStartPos());
    }

    /** 参数 {@code t} 处、**相对起点**的偏移 ✓（子类实现曲线 ✓） */
    public abstract Vec3d getMidPointOffsetFromStart(float t);

    /** 斜率 = {@code dy / d(t * 水平距离)} ✓ —— 算重力加速用 ✓ */
    public abstract float getSlope(float t);

    /** 给定空间点，反求参数 {@code t} ✓ */
    public abstract float getParameter(Vec3d position);

    /** 沿索前进 {@code movement} 后的新参数 ✓（近似 ✓ —— 曲线需迭代 ✓） */
    public abstract double getMovedPositionByParameterApproximately(float currentT, float movement);

    public double getSquaredDistanceApproximately(Vec3d position) {
        return getSquaredDistanceApproximately(position, 1);
    }

    /**
     * 「离索多远」的估算（平方值 ✓ 省一次开方 ✓）。
     *
     * <p>{@code yDistanceScale} 用来**压低竖直方向的权重** ✓ ——
     * 玩家下落速度快时，会放宽对高度差的容忍 ✓（见上游的调用处 ✓）</p>
     */
    public abstract double getSquaredDistanceApproximately(Vec3d position, double yDistanceScale);

    /** 该位置是否「可能挂得上」✓ —— 用一个包住整条索的盒子粗判 ✓ */
    public boolean isPossiblyHangable(Vec3d position) {
        return new AxisAlignedBB(
                getStartPos().x, getStartPos().y, getStartPos().z,
                getEndPos().x, getEndPos().y, getEndPos().z)
                .grow(1d)
                .contains(position);
    }

    // ── ★ 找出玩家身边「抓得到」的滑索（RideZipline 的入口 ✓）────────────
    //
    // ⚠️ 这两个方法在 B1 批次里**刻意留空** ✗ —— 它们依赖 ZiplineRopeEntity ✓，
    //    那个类当时还没移植 ✓。现在实体已就位 ✓，补齐 ✓。

    /**
     * 找一条玩家**抓得到**的滑索 ✓。
     *
     * @return 符合条件的绳索实体 ✓；没有则 {@code null} ✓
     */
    @Nullable
    public static com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity getHangableZipline(
            World world, net.minecraft.entity.player.EntityPlayer player) {
        return getHangableZipline(world, player, null);
    }

    /**
     * 找一条玩家抓得到的滑索 ✓（可排除某一条 ✓）。
     *
     * <h3>★ 判定逻辑（照搬上游 ✓）</h3>
     * <ol>
     *   <li>在玩家周围搜绳索实体 ✓ —— 水平半径 {@code 0.52 × 最大跨度 + 1} ✓，
     *       竖直范围 {@code 最大竖直距离 + 1} ✓</li>
     *   <li>**抓取点**取在玩家**头顶再高一点**的位置 ✗
     *       （{@code 身高 × 1.11} ✓）—— 对应「举手抓索」✓</li>
     *   <li>**竖直方向的距离权重会被压低** ✗：
     *       {@code yDistanceScale = clamp(0.7 / (|下落速度| + 0.7), 0.4, 1)} ✓
     *       —— 下落越快，对高度差越宽容 ✓（否则高速掠过时抓不住 ✓）</li>
     *   <li>判定距离小于**玩家宽度**即算够得着 ✓</li>
     * </ol>
     *
     * @param except 要排除的绳索 ✓（例如「刚刚从这条上掉下来」✓），可为 {@code null} ✓
     */
    @Nullable
    public static com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity getHangableZipline(
            World world, net.minecraft.entity.player.EntityPlayer player,
            @Nullable com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity except) {
        final double radius = MAXIMUM_HORIZONTAL_DISTANCE * 0.52 + 1;
        List<com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity> entities =
                world.getEntitiesWithinAABB(
                        com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity.class,
                        player.getEntityBoundingBox().grow(radius, MAXIMUM_VERTICAL_DISTANCE + 1, radius));

        double catchRange = player.width;
        double fallSpeed = player.motionY;
        // ⚠️ 竖直权重：下落越快 → 压得越低（更宽容）✓
        double yDistanceScale = net.minecraft.util.math.MathHelper.clamp(
                0.7 / (Math.abs(fallSpeed) + 0.7), 0.4, 1.0);
        // 抓取点：头顶再高一点 ✓
        Vec3d grabPos = player.getPositionVector().add(0, player.height * 1.11, 0);

        for (com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity ropeEntity : entities) {
            if (except == ropeEntity) continue;
            // 尚未初始化好的绳索跳过 ✓（两端都是 ORIGIN ✓）
            if (ropeEntity.getStartPos().equals(net.minecraft.util.math.BlockPos.ORIGIN)
                    && ropeEntity.getEndPos().equals(net.minecraft.util.math.BlockPos.ORIGIN)) {
                continue;
            }
            Zipline zipline = ropeEntity.getZipline();
            if (zipline.isPossiblyHangable(grabPos)) {
                double distSqr = zipline.getSquaredDistanceApproximately(grabPos, yDistanceScale);
                if (distSqr < catchRange * catchRange) {
                    return ropeEntity;
                }
            }
        }
        return null;
    }
}
