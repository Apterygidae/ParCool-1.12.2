package com.alrex.parcool.utilities;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 向量工具（1.12.2 版）—— **被上游 19 个文件使用**，是动画器与动作的基础设施。
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code Vector3d}</td><td>{@link Vec3d}</td></tr>
 *   <tr><td>{@code vec.x() / y() / z()}</td><td>{@code vec.x / y / z}（公开字段）</td></tr>
 *   <tr><td>{@code Vector2f}</td><td><b>1.12.2 没有这个类型</b> —— 见下</td></tr>
 * </table>
 *
 * <h3>⚠️ 删掉了两个 {@code Vector2f} 重载</h3>
 * <p>上游有 {@code isZero(Vector2f)} 与 {@code getLength(Vector2f)}，只被
 * {@code KeyBindings.getCurrentMoveVector()} 使用：</p>
 * <pre>
 * Vector2f vector = Minecraft.getInstance().player.input.getMoveVector();
 * if (VectorUtil.isZero(vector)) return Vector3d.ZERO;
 * double length = VectorUtil.getLength(vector);
 * </pre>
 * <p>1.12.2 没有 {@code Vector2f}（那是 1.13+ 的），而且本工程的
 * {@code KeyBindings.getCurrentMoveVector()} 已经改成直接读
 * {@code movementInput.moveStrafe / moveForward} 两个 float，不需要这两个重载。
 * 若将来有调用点需要，用 {@code (float x, float y)} 参数对重建即可。</p>
 */
public class VectorUtil {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180D);

    private VectorUtil() {
    }

    public static double toYawDegree(Vec3d vec) {
        return (Math.atan2(vec.z, vec.x) * 180.0 / Math.PI - 90);
    }

    public static double toYawRadian(Vec3d vec) {
        return (Math.atan2(vec.z, vec.x) - Math.PI / 2.);
    }

    public static double toPitchDegree(Vec3d vec) {
        return -(Math.atan2(vec.y, Math.sqrt(vec.x * vec.x + vec.z * vec.z)) * 180.0 / Math.PI);
    }

    public static Vec3d fromYawDegree(double degree) {
        return new Vec3d(-Math.sin(Math.toRadians(degree)), 0, Math.cos(Math.toRadians(degree)));
    }

    public static Vec3d rotateYDegrees(Vec3d vector, float baseAngle) {
        float angle = baseAngle * DEG_TO_RAD;
        return new Vec3d(
                vector.x * MathHelper.cos(angle) - vector.z * MathHelper.sin(angle),
                vector.y,
                vector.x * MathHelper.sin(angle) + vector.z * MathHelper.cos(angle)
        );
    }

    public static float toYaw(Vec3d vector) {
        return (float) Math.toDegrees(Math.atan2(-vector.x, vector.z));
    }

    public static boolean isZero(Vec3d vector) {
        return vector.x == 0 && vector.y == 0 && vector.z == 0;
    }
}
