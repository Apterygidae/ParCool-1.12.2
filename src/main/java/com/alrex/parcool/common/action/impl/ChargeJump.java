package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.api.unstable.action.ParCoolActionEvent;
import com.alrex.parcool.client.animation.impl.ChargeJumpAnimator;
import com.alrex.parcool.client.animation.impl.JumpChargingAnimator;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * ChargeJump —— 蓄力跳（1.12.2 版，**第六个真实现的动作**）。
 *
 * <p>原地蹲住蓄力 → 松开跳跃键起跳，蓄力越久跳得越高（最高 18 tick）。</p>
 *
 * <h3>1.12.2 的 API 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code ClientPlayerEntity}</td><td>{@link EntityPlayerSP}</td></tr>
 *   <tr><td><b>{@code cp.input.up / down}</b></td>
 *       <td><b>1.12.2 的 {@code MovementInput} 没有这些布尔字段</b>（只有
 *           {@code moveForward} / {@code moveStrafe} 两个 float）
 *           → {@code cp.movementInput.moveForward == 0}（同时覆盖 up 与 down）</td></tr>
 *   <tr><td>{@code cp.input.right / left}</td>
 *       <td>{@code cp.movementInput.moveStrafe == 0}（同时覆盖 right 与 left）</td></tr>
 *   <tr><td>{@code cp.isVisuallyCrawling()}</td><td>{@link ParCoolPlatform#isVisuallySwimming}</td></tr>
 *   <tr><td>{@code cp.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code cp.isShiftKeyDown()}</td><td>{@code cp.isSneaking()}</td></tr>
 *   <tr><td>{@code player.yHeadRot / yBodyRot}</td>
 *       <td>{@code player.rotationYawHead / renderYawOffset}</td></tr>
 *   <tr><td>{@code player.setYBodyRot(x)}</td><td>{@code player.renderYawOffset = x}</td></tr>
 *   <tr><td><b>{@code Vector3d.yRot(radians)}</b></td>
 *       <td><b>{@code Vec3d.rotateYaw(radians)}</b> —— 实现公式逐字相同（都用 cos/sin 直接算），
 *           <b>单位都是弧度</b> ✓ 已核实 1.12.2 源码</td></tr>
 *   <tr><td>{@code player.setDeltaMovement(getDeltaMovement().add(0, v, 0))}</td>
 *       <td>{@code player.motionY += v;}</td></tr>
 * </table>
 *
 * <h3>⚠️ 两个外部钩子的接线</h3>
 * <p>{@link #onJump} 与 {@link #onLand} 不是事件订阅方法，需要由 mixin 在
 * 「玩家跳跃」与「玩家落地」时调用 —— 见本工程 {@code EntityPlayerMixin} 里的接线。</p>
 */
public class ChargeJump extends Action {

    public static final int JUMP_ANIMATION_TICK = 10;
    public static final int JUMP_MAX_CHARGE_TICK = 18;

    private int chargeTick = 0;
    private int lastChargeTick = 0;
    private int notChargeTick = 0;
    private int coolTimeTick = 0;
    private boolean started = false;

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        boolean start = started;
        started = false;
        return start;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return getDoingTick() < JUMP_ANIMATION_TICK;
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        coolTimeTick = 30;
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.CHARGE_JUMP.get(), 1, 1);
        }
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new ChargeJumpAnimator());
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.CHARGE_JUMP.get(), 1, 1);
        }
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new ChargeJumpAnimator());
        }
    }

    /**
     * 每 tick 维护蓄力进度；蓄力时还会把身体朝向慢慢转向视线方向（那个 {@code /2} 是「转到一半」）。
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (player instanceof EntityPlayerSP) {
            EntityPlayerSP cp = (EntityPlayerSP) player;
            if (cp.onGround
                    && coolTimeTick <= 0
                    && !stamina.isExhausted()
                    && parkourability.getActionInfo().can(ChargeJump.class)
                    && !ParCoolPlatform.isVisuallySwimming(cp)
                    && !cp.isSprinting()
                    && !ParCoolPlatform.isInWaterOrBubble(cp)
                    // 1.12.2 的 MovementInput 没有 up/down/left/right 布尔字段 ——
                    // moveForward 同时覆盖「前进/后退」，moveStrafe 同时覆盖「左/右」✓
                    && cp.movementInput.moveForward == 0
                    && cp.movementInput.moveStrafe == 0
                    && !parkourability.get(Crawl.class).isDoing()
                    && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStartEvent(player, this))
                    && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStart(player, this))
            ) {
                if (cp.isSneaking() && KeyRecorder.keySneak.getPreviousTickNotKeyDown() > 5) {
                    chargeTick++;
                    if (chargeTick > JUMP_MAX_CHARGE_TICK) chargeTick = JUMP_MAX_CHARGE_TICK;
                    lastChargeTick = chargeTick;
                    notChargeTick = 0;
                } else {
                    chargeTick--;
                    if (chargeTick < 0) chargeTick = 0;
                    notChargeTick++;
                }
                if (isCharging()) {
                    Vec3d targetAngle = VectorUtil.fromYawDegree(player.rotationYawHead);
                    Vec3d currentAngle = VectorUtil.fromYawDegree(player.renderYawOffset);
                    double differenceAngle = Math.atan(
                            (currentAngle.x * targetAngle.z - targetAngle.x * currentAngle.z)
                                    / (targetAngle.x * currentAngle.x + targetAngle.z * currentAngle.z)
                    );
                    player.renderYawOffset = (float) VectorUtil.toYawDegree(
                            currentAngle.rotateYaw((float) (-differenceAngle / 2)));
                }
            } else {
                if (coolTimeTick > 0) coolTimeTick--;
                chargeTick = 0;
                notChargeTick++;
            }
        }
        if (isCharging()) {
            Animation animation = Animation.get(player);
            if (animation != null && !animation.hasAnimator()) {
                animation.setAnimator(new JumpChargingAnimator());
            }
        }
    }

    /**
     * 由 mixin 在「玩家跳跃」时调用（见 {@code EntityPlayerMixin}）。
     *
     * <p>蓄力过半（或刚松手 5 tick 内且之前蓄过力）才真正起跳并额外给一个向上的速度。</p>
     */
    public void onJump(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        double power = chargeTick / (double) JUMP_MAX_CHARGE_TICK;
        if (power >= 0.5) {
            started = true;
        } else {
            power = lastChargeTick / (double) JUMP_MAX_CHARGE_TICK;
            if (power > 0.5 && notChargeTick < 5) {
                started = true;
            }
        }
        if (started) {
            player.motionY += 0.160 * power;
        }
    }

    /**
     * 由 mixin 在「玩家落地」时调用（见 {@code EntityPlayerMixin}）。
     *
     * <p>落地且没在移动、刚疾跑过 → 直接给一份满蓄力（相当于「落地即弹」）。</p>
     */
    public void onLand(EntityPlayer player, Parkourability parkourability) {
        if (ParCool.PROXY.isLocalPlayer(player) && player instanceof EntityPlayerSP) {
            EntityPlayerSP cp = (EntityPlayerSP) player;
            if (
                    parkourability.getActionInfo().can(ChargeJump.class)
                            && coolTimeTick <= 0
                            && cp.movementInput.moveForward == 0
                            && cp.movementInput.moveStrafe == 0
                            && (parkourability.get(FastRun.class)
                            .getNotDashTick(parkourability.getAdditionalProperties()) < 15)
            ) {
                chargeTick = JUMP_MAX_CHARGE_TICK + 5;
                lastChargeTick = chargeTick;
                notChargeTick = 0;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wantsToShowStatusBar(EntityPlayerSP player, Parkourability parkourability) {
        return isCharging();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getStatusValue(EntityPlayerSP player, Parkourability parkourability) {
        return ((float) getChargingTick()) / JUMP_MAX_CHARGE_TICK;
    }

    @Override
    public void saveSynchronizedState(ByteBuffer buffer) {
        buffer.putInt(chargeTick);
    }

    @Override
    public void restoreSynchronizedState(ByteBuffer buffer) {
        chargeTick = buffer.getInt();
    }

    public boolean isCharging() {
        return chargeTick > 0 && !isDoing();
    }

    /** 当前蓄力进度（**真实值**，不再是占位实现里的恒 0）✓ */
    public int getChargingTick() {
        return chargeTick;
    }
}
