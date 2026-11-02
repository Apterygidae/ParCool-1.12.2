package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.client.animation.impl.FlippingAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * Flipping —— 空翻（1.12.2 版，**第二十四个真实现的动作**）。
 *
 * <p>原地（或起跳时）翻一个整圈 ✓ <b>正如用户所说，它更偏「表演性质」</b> ——
 * 动作本身几乎不改物理（只在非「点移动+跳」模式下补一次起跳冲量 ✓），
 * <b>重心全在 {@link FlippingAnimator} 的 360° 旋转曲线里</b> ✓</p>
 *
 * <h3>三种触发方式（{@code flip_control} 配置，默认 {@code TapMovementAndJump}）</h3>
 * <table border="1">
 *   <tr><th>模式</th><th>操作</th></tr>
 *   <tr><td>{@code PressRightAndLeft}</td>
 *       <td><b>同时</b>按左+右，且两者都是「刚按下」3 tick 内 ✓</td></tr>
 *   <tr><td>{@code TapMovementAndJump}</td>
 *       <td>刚起跳 + 前/后键也是「刚按下」4 tick 内 ✓ <b>默认</b></td></tr>
 *   <tr><td>{@code PressFlippingKey}</td>
 *       <td>按空翻键（默认 F，见控制设置）✓</td></tr>
 * </table>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td><b>{@code player.jumpFromGround()}</b></td>
 *       <td><b>1.12.2 叫 {@code player.jump()}</b>（该方法声明在 {@code EntityLivingBase} 上）✓</td></tr>
 *   <tr><td>{@code player.isShiftKeyDown()}</td><td>{@code player.isSneaking()}</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code player.isInWater()}</td><td>同名 ✓</td></tr>
 *   <tr><td>{@code ParCoolConfig.Client.FlipControl.get()}</td>
 *       <td>{@code getFlipControl()}（字符串存 + 解析）✓</td></tr>
 * </table>
 *
 * <p><b>⚠️ 三个方法都读了客户端输入</b>（{@code KeyBindings}/{@code KeyRecorder}）或设置动画，
 * 所以 1.12.2 都要补 {@code @SideOnly(Side.CLIENT)} ✓
 * （上游只给枚举里的 {@code isInputDone} 标了 {@code @OnlyIn}，其余靠调用链——1.12.2 必须显式 ✓）</p>
 *
 * <h3>★ {@code onJump} 的接线（本批补上的）</h3>
 * <p>上游由 {@code PlayerJumpHandler.onJump} 转调 ✓ —— 本工程那行原本是注释掉的
 * （等这个动作实现）→ <b>本批已放开</b> ✓
 * 没有它，{@code TapMovementAndJump} 模式（默认模式）<b>永远不会触发</b> ✗
 * 因为 {@code justJumped} 恒 false ✓</p>
 *
 * <p><b>⚠️ {@code canContinue} 的写法</b>：{@code !onGround || doingTick <= 10} ——
 * 意思是「在空中就一直翻，落地后最多再翻 10 tick」✓ 照搬 ✓</p>
 */
public class Flipping extends Action {

    public enum ControlType {
        PressRightAndLeft, TapMovementAndJump, PressFlippingKey;

        @SideOnly(Side.CLIENT)
        public boolean isInputDone(boolean justJumped) {
            switch (this) {
                case PressRightAndLeft:
                    return KeyBindings.isKeyRightDown()
                            && KeyRecorder.keyRight.getTickKeyDown() < 3
                            && KeyBindings.isKeyLeftDown()
                            && KeyRecorder.keyLeft.getTickKeyDown() < 3;
                case PressFlippingKey:
                    return KeyRecorder.keyFlipping.isPressed();
                case TapMovementAndJump:
                    return justJumped && (
                            (KeyBindings.isKeyForwardDown() && KeyRecorder.keyForward.getTickKeyDown() < 4)
                                    || (KeyBindings.isKeyBackDown() && KeyRecorder.keyBack.getTickKeyDown() < 4)
                    );
                default:
                    return false;
            }
        }
    }

    public enum Direction {
        Front, Back
    }

    private boolean justJumped = false;

    /** 由 {@code PlayerJumpHandler.onJump} 转调 ✓ */
    public void onJump(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        justJumped = true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        Direction fDirection;
        if (KeyBindings.isKeyBackDown()) {
            fDirection = Direction.Back;   // 按着「后退」→ 后空翻
        } else {
            fDirection = Direction.Front;
        }
        ControlType control = ParCoolConfig.Client.getFlipControl();
        startInfo
                .putInt(control.ordinal())
                .putInt(fDirection.ordinal());
        boolean input = control.isInputDone(justJumped);
        justJumped = false;
        return (input
                && !player.isInWater()
                && !player.isSneaking()
                && !parkourability.get(Crawl.class).isDoing()
                && !parkourability.get(Dive.class).isDoing()
                && !parkourability.get(ChargeJump.class).isDoing()
                && !parkourability.get(HideInBlock.class).isDoing()
                && !parkourability.getBehaviorEnforcer().cancelJump()
                && !stamina.isExhausted()
                // 只在「刚落地/刚起跳」的瞬间允许
                && parkourability.getAdditionalProperties().getNotLandingTick() <= 1
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return !player.onGround || getDoingTick() <= 10;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        ControlType control = ControlType.values()[startData.getInt()];
        // 除「点移动+跳」外，其它模式要补一次起跳冲量（否则原地翻不起来）
        if (control != ControlType.TapMovementAndJump) player.jump();
        stamina.consume(parkourability.getActionInfo().getStaminaConsumptionOf(Flipping.class));
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new FlippingAnimator(
                    Direction.values()[startData.getInt()]
            ));
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        startData.position(4); // 跳过 control（int × 1）—— 直接读方向 ✓
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new FlippingAnimator(
                    Direction.values()[startData.getInt()]
            ));
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }
}
