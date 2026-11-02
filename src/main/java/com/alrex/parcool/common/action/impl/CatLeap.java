package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.CatLeapAnimator;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * CatLeap —— 猫跃（1.12.2 版，**第五个真实现的动作**）。
 *
 * <p>疾跑中「蹲一下再松开」→ 向前扑跳 + 踢起脚下方块的碎屑粒子。</p>
 *
 * <h3>1.12.2 的 API 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity} / {@code ClientPlayerEntity}</td>
 *       <td>{@link EntityPlayer} / {@link EntityPlayerSP}</td></tr>
 *   <tr><td>{@code player.getDeltaMovement()}</td>
 *       <td>{@code new Vec3d(player.motionX, player.motionY, player.motionZ)}</td></tr>
 *   <tr><td>{@code vec.lengthSqr()}</td><td>{@code vec.lengthSquared()}</td></tr>
 *   <tr><td>{@code vec.multiply(1, 0, 1)}</td><td>{@code new Vec3d(vec.x, 0, vec.z)}</td></tr>
 *   <tr><td>{@code player.setDeltaMovement(x,y,z)}</td><td>{@code player.motionX/Y/Z = ...}</td></tr>
 *   <tr><td><b>{@code player.jumpFromGround()}</b></td>
 *       <td><b>{@code player.jump()}</b>（1.12.2 的对应方法名）</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td><td>{@link ParCoolPlatform#isFallFlying}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code player.level}</td><td>{@code player.world}</td></tr>
 *   <tr><td>{@code player.position()}</td><td>{@code player.getPositionVector()}</td></tr>
 *   <tr><td>{@code player.getBbWidth()}</td><td>{@code player.width}</td></tr>
 *   <tr><td>{@code player.getRandom()}</td><td>{@code player.getRNG()}</td></tr>
 *   <tr><td>{@code level.isLoaded(pos)}</td><td>{@code level.isBlockLoaded(pos)}</td></tr>
 *   <tr><td>{@code state.getRenderShape() != BlockRenderType.INVISIBLE}</td>
 *       <td>{@code state.getRenderType() != }{@link EnumBlockRenderType}{@code .INVISIBLE}</td></tr>
 *   <tr><td><b>{@code level.addParticle(IParticleData, ...)}</b></td>
 *       <td><b>{@code level.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x,y,z, vx,vy,vz,
 *           }{@code Block.getStateId(state))}</b> —— 1.12.2 用「粒子类型 + 可变参数」，
 *           方块碎屑靠把 {@code stateId} 当参数传</td></tr>
 * </table>
 *
 * <p><b>依赖的降级</b>：{@code ChargeJump} 目前仍是骨架，它对外暴露的
 * {@code getChargingTick()} 恒返回 0 → 「蓄力过半就不许猫跃」那条判断暂时恒为真 ✓
 * 属可接受降级，等 {@code ChargeJump} 移植后自动生效。</p>
 */
public class CatLeap extends Action {

    private static final int MAX_COOL_TIME_TICK = 30;

    private int coolTimeTick = 0;
    private boolean ready = false;
    private int readyTick = 0;

    @Override
    public void onTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (coolTimeTick > 0) {
            coolTimeTick--;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (ParCool.PROXY.isLocalPlayer(player)) {
            if (KeyRecorder.keySneak.isPressed()
                    && parkourability.get(FastRun.class).getNotDashTick(parkourability.getAdditionalProperties()) < 10) {
                ready = true;
            }
            if (ready) {
                readyTick++;
            }
            if (readyTick > 10) {
                ready = false;
                readyTick = 0;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        Vec3d movement = new Vec3d(player.motionX, player.motionY, player.motionZ);
        if (movement.lengthSquared() < 0.001) return false;
        movement = new Vec3d(movement.x, 0, movement.z).normalize();
        startInfo.putDouble(movement.x).putDouble(movement.z);
        return (player.onGround
                && !player.isInWater()
                && !stamina.isExhausted()
                && coolTimeTick <= 0
                && readyTick > 0
                && parkourability.get(ChargeJump.class).getChargingTick() < ChargeJump.JUMP_MAX_CHARGE_TICK / 2
                && !parkourability.get(HideInBlock.class).isDoing()
                && !parkourability.get(Roll.class).isDoing()
                && !parkourability.get(Tap.class).isDoing()
                && KeyRecorder.keySneak.isReleased()
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return !((getDoingTick() > 1 && player.onGround)
                || ParCoolPlatform.isFallFlying(player)
                || ParCoolPlatform.isInWaterOrBubble(player)
                || player.isInLava()
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        Vec3d jumpDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.CATLEAP.get(), 1, 1);
        }
        coolTimeTick = MAX_COOL_TIME_TICK;
        spawnJumpEffect(player, jumpDirection);
        player.jump();
        Vec3d motionVec = new Vec3d(player.motionX, player.motionY, player.motionZ);
        player.motionX = jumpDirection.x;
        player.motionY = motionVec.y * 1.16667;
        player.motionZ = jumpDirection.z;
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new CatLeapAnimator());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        Vec3d jumpDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.CATLEAP.get(), 1, 1);
        }
        spawnJumpEffect(player, jumpDirection);
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new CatLeapAnimator());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean wantsToShowStatusBar(EntityPlayerSP player, Parkourability parkourability) {
        return coolTimeTick > 0;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getStatusValue(EntityPlayerSP player, Parkourability parkourability) {
        return coolTimeTick / (float) MAX_COOL_TIME_TICK;
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }

    /**
     * 起跳的碎屑粒子：在脚下方块表面撒一圈向外+向上的粒子。
     *
     * <p>1.12.2 没有 {@code BlockParticleData} —— 用
     * {@code EnumParticleTypes.BLOCK_CRACK} + 把 {@code Block.getStateId(state)} 当参数传 ✓</p>
     */
    @SideOnly(Side.CLIENT)
    private void spawnJumpEffect(EntityPlayer player, Vec3d jumpDirection) {
        if (!ParCoolConfig.Client.Booleans.EnableActionParticles.get()) return;
        World level = player.world;
        Vec3d pos = player.getPositionVector();
        BlockPos blockpos = new BlockPos(pos.add(0, -0.2, 0));
        if (!level.isBlockLoaded(blockpos)) return;
        float width = player.width;
        IBlockState blockstate = level.getBlockState(blockpos);
        if (blockstate.getRenderType() != EnumBlockRenderType.INVISIBLE) {
            int stateId = Block.getStateId(blockstate);
            for (int i = 0; i < 20; i++) {
                Vec3d particlePos = new Vec3d(
                        pos.x + (jumpDirection.x * -0.5 + player.getRNG().nextDouble() - 0.5D) * width,
                        pos.y + 0.1D,
                        pos.z + (jumpDirection.z * -0.5 + player.getRNG().nextDouble() - 0.5D) * width
                );
                // ⚠️ 1.16 的 Vector3d.add(dx, dy, dz) 在 1.12.2 没有 —— 直接构造新向量
                Vec3d baseSpeed = particlePos.subtract(pos).normalize()
                        .scale(2.5 + 8 * player.getRNG().nextDouble());
                Vec3d particleSpeed = new Vec3d(baseSpeed.x, baseSpeed.y + 1.5, baseSpeed.z);
                level.spawnParticle(
                        EnumParticleTypes.BLOCK_CRACK,
                        particlePos.x, particlePos.y, particlePos.z,
                        particleSpeed.x, particleSpeed.y, particleSpeed.z,
                        stateId
                );
            }
        }
    }
}
