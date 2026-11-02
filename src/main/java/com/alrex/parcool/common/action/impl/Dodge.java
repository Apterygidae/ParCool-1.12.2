package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.DodgeAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ActionInfo;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.extern.AdditionalMods;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.EntityUtil;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * Dodge —— 闪避（1.12.2 版，**第十个真实现的动作**）。
 *
 * <p>按闪避键（默认 R）并配合方向键 → 朝那个方向翻滚出去。四方向的动画各不相同
 * （前空翻 / 后手翻 / 左右侧滚），并带冷却与「连续闪避」计数。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity} / {@code ClientPlayerEntity}</td>
 *       <td>{@link EntityPlayer} / {@link EntityPlayerSP}</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td><td>{@link ParCoolPlatform#isFallFlying}</td></tr>
 *   <tr><td>{@code player.isShiftKeyDown()}</td><td>{@code player.isSneaking()}</td></tr>
 *   <tr><td>{@code player.abilities.flying}</td><td>{@code player.capabilities.isFlying}</td></tr>
 *   <tr><td><b>{@code Minecraft.getInstance().getCameraEntity().yRot}</b></td>
 *       <td><b>{@code Minecraft.getMinecraft().getRenderViewEntity().rotationYaw}</b></td></tr>
 *   <tr><td>{@code player.setDeltaMovement(vec)}</td><td>{@code motionX/motionY/motionZ}</td></tr>
 *   <tr><td>{@code KeyBinding.isDown()}</td><td>{@code isKeyDown()}</td></tr>
 *   <tr><td>{@code EntityUtil.setYRot} 内部用 {@code setYBodyRot/setYHeadRot/yRot}</td>
 *       <td>本工程的 {@code EntityUtil} 已按 1.12.2 的字段改写（含类型判断）✓</td></tr>
 *   <tr><td>{@code AdditionalMods}（相机 mod 集成）</td>
 *       <td><b>刻意做成透传的空实现</b> —— 见该类的注释（BetterThirdPerson 1.12.2 不存在）✓</td></tr>
 * </table>
 *
 * <h3>两种触发方式</h3>
 * <ol>
 *   <li><b>双敲方向键</b>（需开 {@code enable_double_tapping_for_dodge}）</li>
 *   <li><b>闪避键 + 方向键</b>（默认行为）</li>
 * </ol>
 *
 * <p><b>⚠️ 尚未接入</b>：上游的 {@code PlayerDamageHandler.onAttack} 会让闪避前 10 tick
 * <b>免疫伤害</b>（受 {@code enable_dodge_invulnerable_time} 控制）。那一小段属「伤害处理」，
 * 等整理伤害处理时一起做 ✓</p>
 */
public class Dodge extends Action {

    public static final int MAX_TICK = 11;

    private static final BehaviorEnforcer.ID ID_JUMP_CANCEL = BehaviorEnforcer.newID();
    private static final BehaviorEnforcer.ID ID_DESCEND_EDGE = BehaviorEnforcer.newID();

    private static int getMaxCoolTime(ActionInfo info) {
        return Math.max(
                info.getClientSetting().get(ParCoolConfig.Client.Integers.DodgeCoolTime),
                info.getServerLimitation().get(ParCoolConfig.Server.Integers.DodgeCoolTime)
        );
    }

    private static int getMaxSuccessiveDodge(ActionInfo info) {
        return Math.min(
                info.getClientSetting().get(ParCoolConfig.Client.Integers.MaxSuccessiveDodgeCount),
                info.getServerLimitation().get(ParCoolConfig.Server.Integers.MaxSuccessiveDodgeCount)
        );
    }

    private static int getSuccessiveCoolTime(ActionInfo info) {
        return Math.max(
                info.getClientSetting().get(ParCoolConfig.Client.Integers.SuccessiveDodgeCoolTime),
                info.getServerLimitation().get(ParCoolConfig.Server.Integers.SuccessiveDodgeCoolTime)
        );
    }

    public enum DodgeDirection {
        Front, Back, Left, Right;

        public DodgeDirection inverse() {
            switch (this) {
                case Front:
                    return Back;
                case Back:
                    return Front;
                case Left:
                    return Right;
                case Right:
                    return Left;
            }
            return Front;
        }

        public DodgeDirection right() {
            switch (this) {
                case Front:
                    return Right;
                case Right:
                    return Back;
                case Back:
                    return Left;
                case Left:
                    return Front;
            }
            return Front;
        }

        public DodgeDirection left() {
            switch (this) {
                case Front:
                    return Left;
                case Left:
                    return Back;
                case Back:
                    return Right;
                case Right:
                    return Front;
            }
            return Front;
        }
    }

    private DodgeDirection dodgeDirection = null;
    private int coolTime = 0;
    private int successivelyCount = 0;
    private int successivelyCoolTick = 0;

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (coolTime > 0) coolTime--;
        if (successivelyCoolTick > 0) {
            successivelyCoolTick--;
        } else {
            successivelyCount = 0;
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }

    public double getSpeedModifier(ActionInfo info) {
        return Math.min(
                info.getClientSetting().get(ParCoolConfig.Client.Doubles.DodgeSpeedModifier),
                info.getServerLimitation().get(ParCoolConfig.Server.Doubles.MaxDodgeSpeedModifier)
        );
    }

    /**
     * 方向判定：优先「双敲方向键」，否则「闪避键 + 方向键」。
     *
     * <p>两者都会把「身体朝向」和「相机朝向」一起考虑 —— 所以下面还要过一遍
     * {@code AdditionalMods} 的相机校正（本工程是透传）✓</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        boolean enabledDoubleTap = ParCoolConfig.Client.Booleans.EnableDoubleTappingForDodge.get();
        DodgeDirection direction = null;
        Vec3d dodgeVec = KeyRecorder.getLastMoveVector();
        if (enabledDoubleTap) {
            if (KeyRecorder.keyBack.isDoubleTapped()) direction = DodgeDirection.Back;
            if (KeyRecorder.keyLeft.isDoubleTapped()) direction = DodgeDirection.Left;
            if (KeyRecorder.keyRight.isDoubleTapped()) direction = DodgeDirection.Right;
        }
        if (direction == null && KeyRecorder.keyDodge.isPressed()) {
            if (KeyBindings.isKeyBackDown()) direction = DodgeDirection.Back;
            if (KeyBindings.isKeyForwardDown()) direction = DodgeDirection.Front;
            if (KeyBindings.isKeyLeftDown()) direction = DodgeDirection.Left;
            if (KeyBindings.isKeyRightDown()) direction = DodgeDirection.Right;
            if (direction != null) dodgeVec = KeyBindings.getCurrentMoveVector();
        }
        if (direction == null || dodgeVec == null) return false;
        // 相机 mod 的朝向校正（本工程是透传，见 AdditionalMods 注释）
        direction = AdditionalMods.betterThirdPerson().handleCustomCameraRotationForDodge(direction);
        direction = AdditionalMods.shoulderSurfingManager().handleCustomCameraRotationForDodge(direction);
        startInfo.putInt(direction.ordinal());
        startInfo.putDouble(dodgeVec.x);
        startInfo.putDouble(dodgeVec.z);
        return ((parkourability.getAdditionalProperties().getLandingTick() > 5
                || parkourability.getAdditionalProperties().getPreviousNotLandingTick() < 2)
                && player.onGround
                && !isInSuccessiveCoolDown(parkourability.getActionInfo())
                && coolTime <= 0
                && !ParCoolPlatform.isInWaterOrBubble(player)
                && !player.isSneaking()
                && !stamina.isExhausted()
                && !parkourability.get(Crawl.class).isDoing()
                && !parkourability.get(Roll.class).isDoing()
                && !parkourability.get(Tap.class).isDoing()
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return !(parkourability.get(Roll.class).isDoing()
                || parkourability.get(ClingToCliff.class).isDoing()
                || getDoingTick() >= MAX_TICK
                || ParCoolPlatform.isInWaterOrBubble(player)
                || ParCoolPlatform.isFallFlying(player)
                || player.capabilities.isFlying
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        dodgeDirection = DodgeDirection.values()[startData.getInt()];
        Vec3d dodgeVec = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        coolTime = getMaxCoolTime(parkourability.getActionInfo());
        if (successivelyCount < getMaxSuccessiveDodge(parkourability.getActionInfo())) {
            successivelyCount++;
        }
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.DODGE.get(), 1f, 1f);
        }
        successivelyCoolTick = getSuccessiveCoolTime(parkourability.getActionInfo());

        if (!player.onGround) return;
        float cameraYRot = Minecraft.getMinecraft().getRenderViewEntity().rotationYaw;
        dodgeVec = VectorUtil.rotateYDegrees(dodgeVec, cameraYRot);
        dodgeVec = dodgeVec.scale(0.9 * getSpeedModifier(parkourability.getActionInfo()));
        if (AdditionalMods.isCameraDecoupled()) {
            EntityUtil.setYRot(player, VectorUtil.toYaw(dodgeVec));
        }
        player.motionX = dodgeVec.x;
        player.motionY = dodgeVec.y;
        player.motionZ = dodgeVec.z;

        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new DodgeAnimator(dodgeDirection));
        parkourability.getBehaviorEnforcer().addMarkerCancellingJump(ID_JUMP_CANCEL, this::isDoing);
        if (!parkourability.getClientInfo().get(ParCoolConfig.Client.Booleans.CanGetOffStepsWhileDodge)) {
            parkourability.getBehaviorEnforcer().addMarkerCancellingDescendFromEdge(ID_DESCEND_EDGE, this::isDoing);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        dodgeDirection = DodgeDirection.values()[startData.getInt()];
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.DODGE.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new DodgeAnimator(dodgeDirection));
    }

    public int getCoolTime() {
        return coolTime;
    }

    public int getSuccessivelyCoolTick() {
        return successivelyCoolTick;
    }

    public boolean isInSuccessiveCoolDown(ActionInfo info) {
        return successivelyCount >= getMaxSuccessiveDodge(info);
    }

    public float getCoolDownPhase(ActionInfo info) {
        int maxCoolTime = getMaxCoolTime(info);
        int successiveMaxCoolTime = getSuccessiveCoolTime(info);
        return Math.min(
                (float) (maxCoolTime - getCoolTime()) / maxCoolTime,
                isInSuccessiveCoolDown(info)
                        ? (float) (successiveMaxCoolTime - getSuccessivelyCoolTick()) / (successiveMaxCoolTime)
                        : 1
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wantsToShowStatusBar(EntityPlayerSP player, Parkourability parkourability) {
        return coolTime > 0 || isInSuccessiveCoolDown(parkourability.getActionInfo());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getStatusValue(EntityPlayerSP player, Parkourability parkourability) {
        ActionInfo info = parkourability.getActionInfo();
        int maxCoolTime = getMaxCoolTime(info);
        int successiveMaxCoolTime = getSuccessiveCoolTime(info);
        return Math.max(
                (float) getCoolTime() / maxCoolTime,
                isInSuccessiveCoolDown(info) ? (float) (getSuccessivelyCoolTick()) / (successiveMaxCoolTime) : 0
        );
    }
}
