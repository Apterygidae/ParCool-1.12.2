package com.alrex.parcool.utilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;

/**
 * 实体操作小工具（1.12.2 版）。
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code entity.setDeltaMovement(getDeltaMovement().add(vec))}</td>
 *       <td>{@code motionX/Y/Z += vec.x/y/z}</td></tr>
 *   <tr><td>{@code entity.setYBodyRot(r)} / {@code setYHeadRot(r)} / {@code entity.yRot = r}</td>
 *       <td><b>1.12.2 的 {@code Entity} 上没有前两个</b>：<br>
 *           {@code renderYawOffset} / {@code rotationYawHead} 在 {@link EntityLivingBase} 上，
 *           而 {@code rotationYaw} 在 {@code Entity} 上 → 需要分情况设置</td></tr>
 * </table>
 */
public class EntityUtil {

    private EntityUtil() {
    }

    public static void addVelocity(Entity entity, Vec3d vec) {
        entity.motionX += vec.x;
        entity.motionY += vec.y;
        entity.motionZ += vec.z;
    }

    /**
     * 同时把「身体朝向」「头朝向」「实体朝向」都设成同一个值。
     *
     * <p>1.12.2 没有 {@code setYBodyRot}/{@code setYHeadRot} —— 那两个角度实际存在
     * {@link EntityLivingBase} 的 {@code renderYawOffset} / {@code rotationYawHead} 字段上，
     * 所以这里要做一次类型判断 ✓</p>
     */
    public static void setYRot(Entity entity, float yRot) {
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            living.renderYawOffset = yRot;
            living.rotationYawHead = yRot;
        }
        entity.rotationYaw = yRot;
    }
}
