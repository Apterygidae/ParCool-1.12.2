package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * QuickTurn —— 快速转身（1.12.2 版，**第十八个真实现的动作**）。
 *
 * <p>按一下快速转身键 → 4 tick 内把视线<b>原地转 180°</b>（左右交替，每按一次换一个方向）✓
 * 没有动画器 —— 它改的是<b>玩家朝向</b>而不是模型姿态 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code player.getLookVec()}</td></tr>
 *   <tr><td>{@code vec.yRot(弧度)}</td><td>{@code vec.rotateYaw(弧度)}（公式一致，已核实）</td></tr>
 *   <tr><td>{@code player.yRot = x}</td><td>{@code player.rotationYaw = x}</td></tr>
 *   <tr><td>{@code event.renderTickTime}</td>
 *       <td>同名 ✓（1.12.2 的 {@code RenderTickEvent.renderTickTime} 是 public 字段）</td></tr>
 * </table>
 *
 * <p><b>⚠️ 默认没有绑定按键</b>：{@code keyBindQuickTurn} 是 {@code KEY_NONE} ——
 * 上游如此（这是个可选功能）✓ 要用手动到「控制」里绑一个键 ✓</p>
 *
 * <p><b>⚠️ 本类三个方法都读了客户端输入（{@code KeyRecorder}）或是渲染期方法</b>，
 * 所以都要 {@code @SideOnly(Side.CLIENT)} —— 上游只标了 {@code onRenderTick}，
 * 1.12.2 必须把 {@code canStart}/{@code canContinue} 也补上 ✓</p>
 */
public class QuickTurn extends Action {

    private static final int ANIMATION_TICK_LENGTH = 4;

    /** 每按一次换一个方向（左右交替）—— 所以「转过去再转回来」很自然 ✓ */
    private boolean turnRightward = false;

    private Vec3d startAngle = null;

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        Vec3d angle = player.getLookVec();
        startInfo
                .putDouble(angle.x)
                .putDouble(angle.z);
        return KeyRecorder.keyQuickTurn.isPressed()
                && !parkourability.get(Vault.class).isDoing()
                && !parkourability.get(Roll.class).isDoing()
                && !parkourability.get(Flipping.class).isDoing()
                && !parkourability.get(ClingToCliff.class).isDoing();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return getDoingTick() < ANIMATION_TICK_LENGTH;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        turnRightward = !turnRightward;
        startAngle = new Vec3d(
                startData.getDouble(),
                0,
                startData.getDouble()
        ).normalize();
    }

    /**
     * 每帧把朝向插值着转过去 —— 用 {@code renderTickTime} 让旋转<b>在帧之间也平滑</b>
     * （否则 4 tick 只有 4 个离散角度，会看起来一跳一跳）✓
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        if (isDoing() && startAngle != null) {
            float renderTick = getDoingTick() + event.renderTickTime;
            float animationPhase = renderTick / ANIMATION_TICK_LENGTH;
            Vec3d rotatedAngle = startAngle.rotateYaw(
                    (float) (Math.PI * animationPhase * (turnRightward ? -1 : 1)));
            player.rotationYaw = (float) VectorUtil.toYawDegree(rotatedAngle);
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }
}
