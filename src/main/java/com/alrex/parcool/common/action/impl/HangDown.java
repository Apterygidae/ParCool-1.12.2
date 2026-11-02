package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.HangAnimator;
import com.alrex.parcool.client.input.KeyBindings;
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
 * HangDown —— 挂横杆（1.12.2 版，**第二十个真实现的动作**）。
 *
 * <p>头顶有横杆时按住悬挂键 → 挂上去；挂住后可在杆上横向/前后挪动，
 * 按跳跃键跳离（那是 {@link JumpFromBar}）✓</p>
 *
 * <h3>★ 「垂直于杆」这个概念（本动作的核心，决定动画与操作方式）</h3>
 * <p>{@code orthogonalToBar} 表示<b>玩家面向是否垂直于杆的轴向</b>：</p>
 * <table border="1">
 *   <tr><th></th><th>动画</th><th>移动键</th></tr>
 *   <tr><td><b>垂直于杆</b></td><td>双手向前抓</td><td>左右键（沿杆）</td></tr>
 *   <tr><td><b>顺着杆</b></td><td>双手一正一反抓</td><td>前后键（沿杆）</td></tr>
 * </table>
 * <p>两种情况下「沿杆移动」的方向轴不同 —— 所以动作里要把键映射到世界坐标轴，
 * 而世界坐标轴又由 {@code hangingBarAxis}（杆是 X 向还是 Z 向）决定 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code player.getLookVec()}</td></tr>
 *   <tr><td>{@code getDeltaMovement().y()}</td><td>{@code player.motionY}</td></tr>
 *   <tr><td>{@code getDeltaMovement()}</td><td>{@code new Vec3d(motionX, motionY, motionZ)}</td></tr>
 *   <tr><td>{@code getDeltaMovement().multiply(1,0,1).lengthSqr()}</td>
 *       <td>{@code motionX*motionX + motionZ*motionZ}</td></tr>
 *   <tr><td>{@code setDeltaMovement(x,y,z)}</td><td>{@code motionX/motionY/motionZ}</td></tr>
 *   <tr><td><b>{@code KeyBinding.getKey().equals(...)}</b></td>
 *       <td><b>{@code getKeyCode() == ...}</b>（int 比较，不是 equals）</td></tr>
 *   <tr><td>{@code player.isShiftKeyDown()}</td><td>{@code player.isSneaking()}</td></tr>
 *   <tr><td>{@code player.yBodyRot} / {@code setYBodyRot(x)}</td>
 *       <td>{@code player.renderYawOffset}（字段读写）</td></tr>
 *   <tr><td>{@code vec.dot(...)}</td><td>{@code vec.dotProduct(...)}</td></tr>
 *   <tr><td>{@code vec.yRot(r)}</td><td>{@code vec.rotateYaw(r)}</td></tr>
 * </table>
 *
 * <h3>⚠️ {@link BarAxis} 是<b>嵌套枚举</b>，且被 {@code WorldUtil} 引用</h3>
 * <p>1.16 的 {@code WorldUtil.getHangableBars} 返回 {@code HangDown.BarAxis} ——
 * 所以这个枚举必须定义在本类里（位置不能挪）✓ 它也是当初 {@code WorldUtil} 中
 * 「无法移植」的那两处之一 ✓ <b>本批把这条线收掉了</b> ✓</p>
 */
public class HangDown extends Action {

    /** 横杆的轴向。注意它是 {@code WorldUtil} 的返回类型 —— 位置不能挪 ✓ */
    public enum BarAxis {
        X, Z
    }

    private static final BehaviorEnforcer.ID ID_SNEAK_CANCEL = BehaviorEnforcer.newID();

    private double bodySwingAngleFactor = 0;
    private float armSwingAmount = 0;
    private boolean orthogonalToBar = false;

    @Nullable
    private BarAxis hangingBarAxis = null;

    public float getArmSwingAmount() {
        return armSwingAmount;
    }

    public double getBodySwingAngleFactor() {
        return bodySwingAngleFactor;
    }

    public boolean isOrthogonalToBar() {
        return orthogonalToBar;
    }

    @Nullable
    public BarAxis getHangingBarAxis() {
        return hangingBarAxis;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        // 把「视线与移动方向的一致性」传给服务端 → 决定起手时的身体摆动幅度
        Vec3d look = player.getLookVec();
        Vec3d lookFlat = new Vec3d(look.x, 0, look.z).normalize();
        Vec3d motion = new Vec3d(player.motionX, player.motionY, player.motionZ);
        startInfo.putDouble(Math.max(-1, Math.min(1, 3 * lookFlat.dotProduct(motion))));
        return (!stamina.isExhausted()
                && Math.abs(player.motionY) < 0.2
                && KeyBindings.getKeyHangDown().isKeyDown()
                && !parkourability.get(JumpFromBar.class).isDoing()
                && !parkourability.get(ClingToCliff.class).isDoing()
                && WorldUtil.getHangableBars(player) != null
                // 悬挂键与潜行键不同键时，需要没在潜行
                && (KeyBindings.getKeyHangDown().getKeyCode() == KeyBindings.getKeySneak().getKeyCode()
                || !player.isSneaking())
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return (!stamina.isExhausted()
                && KeyBindings.getKeyHangDown().isKeyDown()
                && parkourability.getActionInfo().can(HangDown.class)
                && !parkourability.get(JumpFromBar.class).isDoing()
                && !parkourability.get(ClingToCliff.class).isDoing()
                && WorldUtil.getHangableBars(player) != null
        );
    }

    @SideOnly(Side.CLIENT)
    private void setup(EntityPlayer player, ByteBuffer startData) {
        armSwingAmount = 0;
        bodySwingAngleFactor = startData.getDouble();
        hangingBarAxis = WorldUtil.getHangableBars(player);
        Vec3d bodyVec = VectorUtil.fromYawDegree(player.renderYawOffset);
        orthogonalToBar = (hangingBarAxis == BarAxis.X && Math.abs(bodyVec.x) < Math.abs(bodyVec.z))
                || (hangingBarAxis == BarAxis.Z && Math.abs(bodyVec.z) < Math.abs(bodyVec.x));
        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new HangAnimator());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        setup(player, startData);
        if (KeyBindings.getKeyHangDown().getKeyCode() != KeyBindings.getKeySneak().getKeyCode()) {
            parkourability.getBehaviorEnforcer().addMarkerCancellingSneak(ID_SNEAK_CANCEL, this::isDoing);
        }
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.HANG_DOWN.get(), 1.0f, 1.0f);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        setup(player, startData);
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.HANG_DOWN.get(), 1.0f, 1.0f);
        }
    }

    /**
     * 杆上挪动。
     *
     * <p>两种姿态的「沿杆方向」不同，所以映射到世界坐标轴的组合也不同：
     * 垂直于杆时用左右键、顺着杆时用前后键 ✓ 移动速度固定 0.1 ✓</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Vec3d bodyVec = VectorUtil.fromYawDegree(player.renderYawOffset);
        final double speed = 0.1;
        double xSpeed = 0, zSpeed = 0;
        if (orthogonalToBar) {
            if (hangingBarAxis == BarAxis.X) {
                xSpeed = (bodyVec.z > 0 ? 1 : -1) * speed;
            } else {
                zSpeed = (bodyVec.x > 0 ? 1 : -1) * speed;
            }
            if (KeyBindings.isKeyLeftDown()) {
                player.motionX = xSpeed;
                player.motionY = 0;
                player.motionZ = -zSpeed;
            } else if (KeyBindings.isKeyRightDown()) {
                player.motionX = -xSpeed;
                player.motionY = 0;
                player.motionZ = zSpeed;
            } else {
                player.motionX = 0;
                player.motionY = 0;
                player.motionZ = 0;
            }
        } else {
            if (hangingBarAxis == BarAxis.X) {
                xSpeed = (bodyVec.x > 0 ? 1 : -1) * speed;
            } else {
                zSpeed = (bodyVec.z > 0 ? 1 : -1) * speed;
            }
            if (KeyBindings.isKeyForwardDown()) {
                player.motionX = xSpeed;
                player.motionY = 0;
                player.motionZ = zSpeed;
            } else if (KeyBindings.isKeyBackDown()) {
                player.motionX = -xSpeed;
                player.motionY = 0;
                player.motionZ = -zSpeed;
            } else {
                player.motionX = 0;
                player.motionY = 0;
                player.motionZ = 0;
            }
        }
        armSwingAmount += (float) (player.motionX * player.motionX + player.motionZ * player.motionZ);
    }

    /** 每 tick 重算「垂直于杆」，并让身体摆动幅度慢慢衰减 ✓ */
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        hangingBarAxis = WorldUtil.getHangableBars(player);
        Vec3d bodyVec = VectorUtil.fromYawDegree(player.renderYawOffset);
        orthogonalToBar =
                (hangingBarAxis == BarAxis.X && Math.abs(bodyVec.x) < Math.abs(bodyVec.z))
                        || (hangingBarAxis == BarAxis.Z && Math.abs(bodyVec.z) < Math.abs(bodyVec.x));
        // 垂直于杆时衰减得更慢（摆动更持久）
        if (orthogonalToBar) {
            bodySwingAngleFactor /= 1.05;
        } else {
            bodySwingAngleFactor /= 1.5;
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

    /**
     * 把身体朝向「转正」：对齐到视线最近的那个主轴（X 或 Z），只转 1/4 的差值
     * —— 所以挂上后身体会慢慢摆正，而不是瞬间弹过去 ✓
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        if (isDoing()) {
            if (hangingBarAxis == null) return;
            Vec3d bodyVec = VectorUtil.fromYawDegree(player.renderYawOffset).normalize();
            Vec3d lookVec = player.getLookVec();
            Vec3d idealLookVec;
            if (Math.abs(lookVec.x) > Math.abs(lookVec.z)) {
                idealLookVec = new Vec3d(lookVec.x > 0 ? 1 : -1, 0, 0);
            } else {
                idealLookVec = new Vec3d(0, 0, lookVec.z > 0 ? 1 : -1);
            }
            double differenceAngle = Math.acos(bodyVec.dotProduct(idealLookVec));
            differenceAngle /= 4;
            player.renderYawOffset = (float) VectorUtil.toYawDegree(
                    idealLookVec.rotateYaw((float) differenceAngle));
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }
}
