package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.KongVaultAnimator;
import com.alrex.parcool.client.animation.impl.SpeedVaultAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Vault —— 翻越（1.12.2 版，**第四个真实现的动作**）。
 *
 * <p>对着「可翻越的台阶」按翻越键 → 撑起身子越过去。有两种动画：侧身翻越（Speed Vault）与
 * 拱身翻越（Kong Vault），由配置的 {@code vault_animation_mode} 决定用哪个。</p>
 *
 * <h3>1.12.2 的 API 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code player.getLookVec()}</td></tr>
 *   <tr><td>{@code lookVec.x() / .y() / .z()}</td><td>{@code .x / .y / .z}（公开字段）</td></tr>
 *   <tr><td>{@code Vector3d}</td><td>{@link Vec3d}</td></tr>
 *   <tr><td>{@code player.getBbHeight()}</td><td>{@code player.height}</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code player.isInWater()}</td><td>同名 ✓</td></tr>
 *   <tr><td>{@code player.setDeltaMovement(x,y,z)}</td>
 *       <td>{@code player.motionX = x; player.motionY = y; player.motionZ = z;}</td></tr>
 *   <tr><td>{@code player.playSound(SoundEvent, vol, pitch)}</td><td>同名 ✓（{@code EntityPlayer} 有该重载）</td></tr>
 *   <tr><td>{@code ParCoolConfig.Client.VaultAnimationMode.get()}</td>
 *       <td>{@code ParCoolConfig.Client.getVaultAnimationMode()}（字符串存 + 解析）</td></tr>
 * </table>
 *
 * <h3>依赖的 {@code WorldUtil}</h3>
 * <p>{@code getVaultableStep} / {@code getWallHeight} 已随本次移植就位 ✓
 * 注意本工程的 {@code WorldUtil} 是<b>分功能批次移植</b>的（上游那 534 行里有一部分依赖
 * 1.12.2 不存在的方形标签系统，需要重新设计）—— 见该类的注释。</p>
 */
public class Vault extends Action {

    public enum TypeSelectionMode {
        SpeedVault, KongVault, Dynamic
    }

    public static final int MAX_TICK = 11;

    public enum AnimationType {
        SPEED_VAULT_RIGHT, SPEED_VAULT_LEFT, KONG_VAULT
    }

    // 仅客户端使用
    private double stepHeight = 0;
    @Nullable
    private Vec3d stepDirection = null;
    @Nullable
    private AnimationType currentAnimation;

    @Nullable
    public AnimationType getCurrentAnimation() {
        if (!isDoing()) return null;
        return currentAnimation;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        Vec3d lookVec = player.getLookVec();
        lookVec = new Vec3d(lookVec.x, 0, lookVec.z).normalize();
        Vec3d step = WorldUtil.getVaultableStep(player);
        if (step == null) return false;
        step = step.normalize();
        // 把「视线方向 / 台阶方向」当成复数 (x + z i) 来算两者的夹角差
        Vec3d dividedVec =
                new Vec3d(
                        lookVec.x * step.x + lookVec.z * step.z, 0,
                        -lookVec.x * step.z + lookVec.z * step.x
                ).normalize();
        if (dividedVec.x < 0.707106) {
            return false;
        }
        AnimationType animationType;
        switch (ParCoolConfig.Client.getVaultAnimationMode()) {
            case KongVault:
                animationType = AnimationType.KONG_VAULT;
                break;
            case SpeedVault:
                animationType = dividedVec.z > 0 ? AnimationType.SPEED_VAULT_RIGHT : AnimationType.SPEED_VAULT_LEFT;
                break;
            default:
                if (dividedVec.x > 0.99) {
                    animationType = AnimationType.KONG_VAULT;
                } else {
                    animationType = dividedVec.z > 0 ? AnimationType.SPEED_VAULT_RIGHT : AnimationType.SPEED_VAULT_LEFT;
                }
                break;
        }
        double wallHeight = WorldUtil.getWallHeight(player);
        startInfo
                .put((byte) animationType.ordinal())
                .putDouble(step.x)
                .putDouble(step.y)
                .putDouble(step.z)
                .putDouble(wallHeight);

        return (!stamina.isExhausted()
                && !player.isInWater()
                && !(ParCoolConfig.Client.Booleans.VaultKeyPressedNeeded.get() && !KeyBindings.getKeyVault().isKeyDown())
                && parkourability.get(FastRun.class).canActWithRunning(player)
                && (player.onGround || ParCoolConfig.Client.Booleans.EnableVaultInAir.get())
                && wallHeight > player.height * 0.44 /* 约 0.8 */
        );
    }

    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return getDoingTick() < MAX_TICK;
    }

    private int getVaultAnimateTime() {
        return 2;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        currentAnimation = AnimationType.values()[startData.get()];
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.VAULT.get(), 1f, 1f);
        }
        stepDirection = new Vec3d(startData.getDouble(), startData.getDouble(), startData.getDouble());
        stepHeight = startData.getDouble();
        setAnimator(player, parkourability);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        currentAnimation = AnimationType.values()[startData.get()];
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.VAULT.get(), 1f, 1f);
        }
        setAnimator(player, parkourability);
    }

    @SideOnly(Side.CLIENT)
    private void setAnimator(EntityPlayer player, Parkourability parkourability) {
        Animation animation = Animation.get(player);
        if (animation == null || currentAnimation == null) return;
        switch (currentAnimation) {
            case SPEED_VAULT_RIGHT:
                animation.setAnimator(new SpeedVaultAnimator(SpeedVaultAnimator.Type.Right));
                break;
            case SPEED_VAULT_LEFT:
                animation.setAnimator(new SpeedVaultAnimator(SpeedVaultAnimator.Type.Left));
                break;
            case KONG_VAULT:
                animation.setAnimator(new KongVaultAnimator());
                break;
            default:
                break;
        }
    }

    /**
     * 翻越的位移曲线：前 2 tick 是「撑起+前推」，第 3 tick 给一个向前的初速，之后交给物理。
     *
     * <p>注意 Y 速度除以 {@code height / 1.8} —— 那是在按玩家身高缩放，
     * 让高个（Aqua 姿态系统下会变尺寸）也能翻过同样高的东西。</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (stepDirection == null) return;
        if (getDoingTick() < getVaultAnimateTime()) {
            player.motionX = stepDirection.x / 10;
            player.motionY = ((stepHeight + 0.02) / this.getVaultAnimateTime()) / (player.height / 1.8);
            player.motionZ = stepDirection.z / 10;
        } else if (getDoingTick() == getVaultAnimateTime()) {
            stepDirection = stepDirection.normalize();
            player.motionX = stepDirection.x * 0.45;
            player.motionY = 0.075 * (player.height / 1.8);
            player.motionZ = stepDirection.z * 0.45;
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInLocalClient(EntityPlayer player) {
    }
}
