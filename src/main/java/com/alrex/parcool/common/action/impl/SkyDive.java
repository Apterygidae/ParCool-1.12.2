package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * SkyDive —— 空中俯冲（1.12.2 版，**第二十三个真实现的动作**）。
 *
 * <p><b>它不是独立起手的</b>：只有 {@link Dive} 已经在做、且已持续 15 tick 以上时，
 * 按跳跃键才会切进「空中俯冲」✓
 * 此时玩家可以用方向键<b>微调姿态</b>，并让 Y 速度按固定比例衰减（飞得更久）✓</p>
 *
 * <h3>★ 它和 {@link Dive} 的动画是同一套</h3>
 * <p>两个动作<b>共用一个宿主动画器</b>（{@code DiveAnimationHostAnimator}）——
 * 它在两者之间做交叉淡入淡出 ✓ 所以本动作<b>自己不带动画器</b> ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code ClientPlayerEntity}</td><td>{@link EntityPlayerSP}</td></tr>
 *   <tr><td><b>{@code clientPlayer.input.getMoveVector()}</b>（{@code Vector2f}）</td>
 *       <td><b>1.12.2 没有 {@code Vector2f}</b> → 直接读
 *           {@code movementInput.moveStrafe / moveForward} 两个 float ✓</td></tr>
 *   <tr><td>{@code clientPlayer.input.leftImpulse / forwardImpulse}</td>
 *       <td>{@code movementInput.moveStrafe / moveForward}</td></tr>
 *   <tr><td>{@code player.yHeadRot} / {@code setYBodyRot(x)}</td>
 *       <td>{@code player.rotationYawHead} / {@code player.renderYawOffset = x}</td></tr>
 *   <tr><td>{@code vec.yRot(弧度)}</td><td>{@code vec.rotateYaw(弧度)}</td></tr>
 *   <tr><td>{@code setDeltaMovement(v)}</td><td>{@code motionX/motionY/motionZ}</td></tr>
 * </table>
 *
 * <p><b>⚠️ {@code canStart}/{@code canContinue}/{@code onRenderTick}</b> 都读客户端输入或改渲染朝向
 * → 都要 {@code @SideOnly(Side.CLIENT)} ✓（上游只标了部分）</p>
 *
 * <p><b>⚠️ 上游有一处「乘 0」的死代码</b>：{@code leftVec} 乘了 {@code leftImpulse * 0.0} ——
 * 结果恒为零向量 ✗ 本移植<b>照搬但标注</b>（保持行为一致，不擅自"修好"它）✓</p>
 */
public class SkyDive extends Action {

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        return parkourability.get(Dive.class).getDoingTick() > 15
                && !stamina.isExhausted()
                && getNotDoingTick() > 20
                && KeyRecorder.keyJumpState.isPressed();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return parkourability.get(Dive.class).isDoing() && !KeyRecorder.keyJumpState.isPressed();
    }

    /**
     * 空中俯冲期间的位移：Y 速度按衰减率
     * （客户端设置与服务端下限里<b>更严格</b>的那个）缩小，并叠加水平微调 ✓
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (!(player instanceof EntityPlayerSP)) {
            return;
        }
        EntityPlayerSP clientPlayer = (EntityPlayerSP) player;
        Vec3d forwardVec = VectorUtil.fromYawDegree(player.rotationYawHead);
        // ⚠️ 上游此处乘了 `* 0.0` —— 恒为零向量（死代码）。照搬但标注，保持行为一致 ✓
        Vec3d leftVec = forwardVec.rotateYaw((float) Math.PI / 2)
                .scale(clientPlayer.movementInput.moveStrafe * 0.0);
        forwardVec = forwardVec.scale(clientPlayer.movementInput.moveForward * 0.03);
        double rate = parkourability.getLimitedValue(
                ParCoolConfig.Client.Doubles.SkyDiveSpeedDecreaseRate,
                ParCoolConfig.Server.Doubles.MinSkyDiveSpeedDecreaseRate
        );
        Vec3d add = forwardVec.add(leftVec);
        player.motionX = player.motionX * 1 + add.x;
        player.motionY = player.motionY * rate + add.y;
        player.motionZ = player.motionZ * 1 + add.z;
    }

    /** 空中俯冲时上身跟着视角转 —— 所以看起来是「头朝下俯冲」✓ */
    @SideOnly(Side.CLIENT)
    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        if (isDoing()) player.renderYawOffset = player.rotationYawHead;
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }
}
