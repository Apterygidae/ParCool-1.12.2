package com.alrex.parcool.client.animation;

import com.alrex.parcool.api.unstable.animation.AnimationOption;
import com.alrex.parcool.platform.ParCoolPlatform;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 整个玩家模型的朝向/位移控制（1.12.2 版）。
 *
 * <h3>与 1.16.5 的根本差异：没有 {@code MatrixStack}</h3>
 * <p>1.16 的 {@code MatrixStack} 是<b>显式矩阵对象</b>（可传递、可离屏操作）；
 * 1.12.2 是<b>立即模式</b> —— {@code GlStateManager} 维护一份全局矩阵栈，
 * 调用即生效。所以本类去掉了 {@code MatrixStack} 字段，全部改为直接调 {@code GlStateManager}。</p>
 *
 * <p><b>因此 {@code getRawStack()} 在 1.12.2 没有对应物，恒返回 {@code null}。</b>
 * 用到它的 Animator 在 P5 移植时需要改写（这是为数不多的必须改 API 的地方）。</p>
 *
 * <h3>坐标轴</h3>
 * <p>上游用 {@code Vector3f.XN/YN/ZN}（负轴常量）。1.12.2 的 MC 没有 {@code Vector3f}，
 * 这里改用 {@link Vec3d} 并自带 {@link #AXIS_XN}/{@link #AXIS_YN}/{@link #AXIS_ZN} 三个常量。
 * <b>P5 移植 Animator 时把 {@code Vector3f.XN} 换成 {@link #AXIS_XN} 即可</b>（机械替换）。</p>
 *
 * <h3>姿态</h3>
 * <p>上游读 {@code player.getPose()}（1.16 原生）；1.12.2 由 Aqua Acrobatics 提供 Pose，
 * 统一走 {@link ParCoolPlatform#getPose}（本工程唯一的 Aqua API 收口点）。</p>
 */
@SideOnly(Side.CLIENT)
public class PlayerModelRotator {

    /** 负 X 轴（对应上游 {@code Vector3f.XN}）。 */
    public static final Vec3d AXIS_XN = new Vec3d(-1, 0, 0);
    /** 负 Y 轴。 */
    public static final Vec3d AXIS_YN = new Vec3d(0, -1, 0);
    /** 负 Z 轴。 */
    public static final Vec3d AXIS_ZN = new Vec3d(0, 0, -1);

    private final EntityPlayer player;
    private final float partial;
    @SuppressWarnings("unused")
    private AnimationOption option = new AnimationOption();
    private double playerHeight = 1.8;
    private final float givenXRot;
    private final float givenYRot;
    private final float givenZRot;

    private boolean basedCenter = false;
    private boolean basedTop = false;

    public PlayerModelRotator(EntityPlayer player, float partial, float xRot, float yRot, float zRot) {
        this.player = player;
        this.partial = partial;
        this.givenXRot = xRot;
        this.givenYRot = yRot;
        this.givenZRot = zRot;
        // 躺姿/匍匐/游泳时身高压到 0.6 —— 用姿态判断，走 Aqua 收口
        switch (ParCoolPlatform.getPose(player)) {
            case SWIMMING:
            case CROUCHING:
            case SLEEPING:
                playerHeight = 0.6;
                break;
            default:
                break;
        }
    }

    public float getXRot() {
        return givenXRot;
    }

    public float getYRot() {
        return givenYRot;
    }

    public float getZRot() {
        return givenZRot;
    }

    public float getPartialTick() {
        return partial;
    }

    public void setOption(AnimationOption option) {
        this.option = option;
    }

    public PlayerModelRotator start() {
        return this;
    }

    public PlayerModelRotator startBasedCenter() {
        basedCenter = true;
        GlStateManager.translate(0, playerHeight / 2, 0);
        return this;
    }

    public PlayerModelRotator startBasedTop() {
        basedTop = true;
        GlStateManager.translate(0, playerHeight, 0);
        return this;
    }

    /**
     * 1.12.2 没有 {@code MatrixStack}，恒返回 {@code null}。
     * 见类注释 —— 用到它的 Animator 需要在 P5 改写。
     */
    public Object getRawStack() {
        return null;
    }

    public PlayerModelRotator translateY(float offset) {
        GlStateManager.translate(0, offset, 0);
        return this;
    }

    public PlayerModelRotator translate(float offsetX, float offsetY, float offsetZ) {
        GlStateManager.translate(offsetX, offsetY, offsetZ);
        return this;
    }

    // ── 三个命名旋转（上游的主力接口，不需要坐标轴常量） ────────────────────

    public PlayerModelRotator rotatePitchFrontward(float angleDegree) {
        GlStateManager.rotate(angleDegree, -1f, 0f, 0f);
        return this;
    }

    public PlayerModelRotator rotateRollRightward(float angleDegree) {
        GlStateManager.rotate(angleDegree, 0f, 0f, -1f);
        return this;
    }

    public PlayerModelRotator rotateYawRightward(float angleDegree) {
        GlStateManager.rotate(angleDegree, 0f, -1f, 0f);
        return this;
    }

    // ── 通用轴旋转 ──────────────────────────────────────────────────────────

    /** {@code angle} 为<b>弧度</b>（上游签名如此，勿改成角度）。 */
    public PlayerModelRotator rotate(float angle, Vec3d axis) {
        GlStateManager.rotate((float) Math.toDegrees(angle), (float) axis.x, (float) axis.y, (float) axis.z);
        return this;
    }

    public PlayerModelRotator rotateDegrees(float angleDegree, Vec3d axis) {
        GlStateManager.rotate(angleDegree, (float) axis.x, (float) axis.y, (float) axis.z);
        return this;
    }

    public void end() {
        if (basedCenter) {
            GlStateManager.translate(0, -playerHeight / 2, 0);
        }
        if (basedTop) {
            GlStateManager.translate(0, -playerHeight, 0);
        }
    }

    public void endEnabledLegGrounding() {
        end();
    }
}
