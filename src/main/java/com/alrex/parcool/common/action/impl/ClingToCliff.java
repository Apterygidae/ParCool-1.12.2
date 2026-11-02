package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.ClingToCliffAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.VectorUtil;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * ClingToCliff —— 抓住崖壁（1.12.2 版，**第十一个真实现的动作**）。
 *
 * <p>面朝墙下落时按住抓墙键 → 挂在墙上；挂住后可用左右键横向挪动，
 * 按跳跃键可以翻上去（那是 {@link ClimbUp}）✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getDeltaMovement().y()}</td><td>{@code player.motionY}</td></tr>
 *   <tr><td>{@code getDeltaMovement()}</td><td>{@code new Vec3d(motionX, motionY, motionZ)}</td></tr>
 *   <tr><td>{@code getDeltaMovement().multiply(1,0,1).lengthSqr()}</td>
 *       <td>{@code motionX*motionX + motionZ*motionZ}（直接算，少建对象）</td></tr>
 *   <tr><td>{@code setDeltaMovement(x,y,z)}</td><td>{@code motionX/motionY/motionZ}</td></tr>
 *   <tr><td><b>{@code KeyBinding.getKey()}</b>（{@code InputMappings.Input}）</td>
 *       <td><b>{@code KeyBinding.getKeyCode()}</b>（int）→ 用 {@code ==} 比较而不是 {@code equals}</td></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code player.getLookVec()}</td></tr>
 *   <tr><td>{@code player.isShiftKeyDown()}</td><td>{@code player.isSneaking()}</td></tr>
 *   <tr><td>{@code player.setYBodyRot(x)} / {@code yBodyRotO = yBodyRot = x}</td>
 *       <td>{@code renderYawOffset = x} / {@code prevRenderYawOffset = renderYawOffset = x}</td></tr>
 *   <tr><td>{@code vec.yRot(弧度)}</td><td>{@code vec.rotateYaw(弧度)}（公式一致）</td></tr>
 *   <tr><td>{@code vec.reverse()}</td><td>{@code vec.scale(-1)}</td></tr>
 *   <tr><td>{@code ParCoolConfig.Client.ClingToCliffControl.get()}</td>
 *       <td>{@code getClingToCliffControl()}（字符串存 + 解析）</td></tr>
 * </table>
 *
 * <p>{@code WorldUtil.getGrabbableWall} 已随本次移植就位 ✓</p>
 */
public class ClingToCliff extends Action {

    public enum ControlType {
        PressKey, Toggle
    }

    private static final BehaviorEnforcer.ID ID_SNEAK_CANCEL = BehaviorEnforcer.newID();
    private static final BehaviorEnforcer.ID ID_FALL_FLY_CANCEL = BehaviorEnforcer.newID();

    private float armSwingAmount = 0;
    private FacingDirection facingDirection = FacingDirection.ToWall;
    @Nullable
    private Vec3d clingWallDirection = null;

    public float getArmSwingAmount() {
        return armSwingAmount;
    }

    public FacingDirection getFacingDirection() {
        return facingDirection;
    }

    /** 挂墙期间清零摔落距离（否则松手时会吃一次摔伤）。 */
    @Override
    public void onWorkingTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        player.fallDistance = 0;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        boolean value = (!stamina.isExhausted()
                && player.motionY < 0.2
                && !parkourability.get(HorizontalWallRun.class).isDoing()
                && KeyBindings.getKeyGrabWall().isKeyDown()
                // 抓墙键与潜行键不同键时，需要没在潜行（否则与潜行冲突）
                && (KeyBindings.getKeyGrabWall().getKeyCode() == KeyBindings.getKeySneak().getKeyCode()
                || !player.isSneaking())
        );
        if (!value) return false;
        Vec3d wallVec = WorldUtil.getGrabbableWall(player);
        if (wallVec == null) return false;
        startInfo.putDouble(wallVec.x).putDouble(wallVec.z);
        // 必须是「面朝这堵墙」才算能抓 —— 点积 > 0.5 约等于夹角 < 60°
        Vec3d look = player.getLookVec();
        return 0.5 < wallVec.normalize().dotProduct(new Vec3d(look.x, 0, look.z).normalize());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return (!stamina.isExhausted()
                && isGrabbing()
                && !parkourability.get(HorizontalWallRun.class).isDoing()
                && !parkourability.get(ClimbUp.class).isDoing()
                && WorldUtil.getGrabbableWall(player) != null
        );
    }

    /**
     * {@code PressKey} 模式：按住才挂住；
     * {@code Toggle} 模式：<b>再按一次就松手</b> —— 注意是 {@code !isPressed()}，
     * 也就是「没按的时候算抓着」（因为按下那一下是「开始」，之后靠这个维持）✓
     */
    @SideOnly(Side.CLIENT)
    private boolean isGrabbing() {
        return ParCoolConfig.Client.getClingToCliffControl() == ControlType.PressKey
                ? KeyBindings.getKeyGrabWall().isKeyDown()
                : !KeyRecorder.keyGrabWall.isPressed();
    }

    @Override
    public void onStart(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        parkourability.getBehaviorEnforcer().addMarkerCancellingFallFlying(ID_FALL_FLY_CANCEL, this::isDoing);
        armSwingAmount = 0;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        clingWallDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        facingDirection = FacingDirection.ToWall;
        armSwingAmount = 0;
        // 抓墙键不是潜行键时，挂墙期间要屏蔽潜行（否则身体会缩下去）
        if (KeyBindings.getKeyGrabWall().getKeyCode() != KeyBindings.getKeySneak().getKeyCode()) {
            parkourability.getBehaviorEnforcer().addMarkerCancellingSneak(ID_SNEAK_CANCEL, this::isDoing);
        }
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.CLING_TO_CLIFF.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new ClingToCliffAnimator());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        clingWallDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        facingDirection = FacingDirection.ToWall;
        armSwingAmount = 0;
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.CLING_TO_CLIFF.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new ClingToCliffAnimator());
    }

    /**
     * 挂墙期间：横向移动（左右键沿墙面平移），并累计「摆动量」供动画用。
     *
     * <p>左右键同时按下 → 不动（避免方向打架）✓</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        armSwingAmount += (float) (player.motionX * player.motionX + player.motionZ * player.motionZ);
        if (KeyBindings.isLeftAndRightDown()) {
            player.motionX = 0;
            player.motionY = 0;
            player.motionZ = 0;
        } else {
            if (clingWallDirection != null && facingDirection == FacingDirection.ToWall) {
                // 沿墙面切向偏移 0.1（把墙的法向转 90°）
                Vec3d vec = clingWallDirection.rotateYaw((float) (Math.PI / 2)).normalize().scale(0.1);
                if (KeyBindings.isKeyLeftDown()) {
                    player.motionX = vec.x;
                    player.motionY = vec.y;
                    player.motionZ = vec.z;
                } else if (KeyBindings.isKeyRightDown()) {
                    Vec3d rev = vec.scale(-1);
                    player.motionX = rev.x;
                    player.motionY = rev.y;
                    player.motionZ = rev.z;
                } else {
                    player.motionX = 0;
                    player.motionY = 0;
                    player.motionZ = 0;
                }
            } else {
                player.motionX = 0;
                player.motionY = 0;
                player.motionZ = 0;
            }
        }
    }

    /**
     * 每 tick 重算「玩家相对墙的朝向」，分三种：面朝墙 / 右侧贴墙 / 左侧贴墙。
     *
     * <p>判据是把墙的法向与视线方向做二维叉积，看夹角落在哪一侧 ✓</p>
     */
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        clingWallDirection = WorldUtil.getGrabbableWall(player);
        if (clingWallDirection == null) return;
        clingWallDirection = clingWallDirection.normalize();
        Vec3d look = player.getLookVec();
        Vec3d lookingAngle = new Vec3d(look.x, 0, look.z).normalize();
        Vec3d angle =
                new Vec3d(
                        clingWallDirection.x * lookingAngle.x + clingWallDirection.z * lookingAngle.z, 0,
                        -clingWallDirection.x * lookingAngle.z + clingWallDirection.z * lookingAngle.x
                ).normalize();
        if (angle.x > 0.342) {
            facingDirection = FacingDirection.ToWall;
        } else if (angle.z < 0) {
            facingDirection = FacingDirection.RightAgainstWall;
        } else {
            facingDirection = FacingDirection.LeftAgainstWall;
        }
    }

    @Override
    public void saveSynchronizedState(ByteBuffer buffer) {
        buffer.putFloat(armSwingAmount);
    }

    @Override
    public void restoreSynchronizedState(ByteBuffer buffer) {
        armSwingAmount = buffer.getFloat();
    }

    /** 挂墙时把身体朝向对齐到墙面法向（或侧向 90°）✓ */
    @SideOnly(Side.CLIENT)
    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        if (isDoing() && clingWallDirection != null) {
            switch (facingDirection) {
                case ToWall:
                    player.renderYawOffset = (float) VectorUtil.toYawDegree(clingWallDirection);
                    break;
                case RightAgainstWall: {
                    float yaw = (float) VectorUtil.toYawDegree(
                            clingWallDirection.rotateYaw((float) (-Math.PI / 2)));
                    player.prevRenderYawOffset = yaw;
                    player.renderYawOffset = yaw;
                    break;
                }
                case LeftAgainstWall: {
                    float yaw = (float) VectorUtil.toYawDegree(
                            clingWallDirection.rotateYaw((float) (Math.PI / 2)));
                    player.prevRenderYawOffset = yaw;
                    player.renderYawOffset = yaw;
                    break;
                }
                default:
                    break;
            }
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }

    public enum FacingDirection {
        ToWall, RightAgainstWall, LeftAgainstWall
    }
}
