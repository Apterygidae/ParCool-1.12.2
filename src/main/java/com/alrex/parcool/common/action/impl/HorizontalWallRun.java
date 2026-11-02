package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.HorizontalWallRunAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ActionInfo;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.BufferUtil;
import com.alrex.parcool.utilities.VectorUtil;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * HorizontalWallRun —— 横向跑墙（1.12.2 版，**第十六个真实现的动作**）。
 *
 * <p>贴着墙、顺着墙面横向奔跑 —— 只在下滑速度很小时成立（{@code |motionY| < 0.5}），
 * 跑的过程里纵向速度会按光滑度和剩余时间逐渐衰减 ✓ 结束后有 10 tick 冷却 ✓</p>
 *
 * <h3>与 {@link WallJump} 的配合（这是它最主要的用法）</h3>
 * <p>横向跑墙结束时，玩家通常正处于贴墙状态 → 此时按蹬墙键就能蹬出去 ✓
 * 两者通过 {@code WallJump.justJumped()} 互相排斥：<b>刚蹬完墙的那一帧不许开始跑墙</b>，
 * 而<b>跑墙期间不许蹬墙</b> —— 避免两个动作在同一帧抢同一面墙 ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code ClientPlayerEntity}</td><td>{@link EntityPlayerSP}</td></tr>
 *   <tr><td><b>{@code localPlayer.input.getMoveVector()}（{@code Vector2f}）</b></td>
 *       <td><b>1.12.2 没有 {@code Vector2f}</b> → 用
 *           {@code movementInput.moveStrafe} 与 {@code moveForward} 两个 float 直接构造</td></tr>
 *   <tr><td>{@code player.yRot}</td><td>{@code player.rotationYaw}</td></tr>
 *   <tr><td>{@code player.yBodyRot / yBodyRotO}</td>
 *       <td>{@code player.renderYawOffset / prevRenderYawOffset}</td></tr>
 *   <tr><td>{@code player.getYHeadRot()}</td><td>{@code player.rotationYawHead}</td></tr>
 *   <tr><td>{@code player.getAttribute(Attributes.MOVEMENT_SPEED)}</td>
 *       <td>{@code player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED)}</td></tr>
 *   <tr><td>{@code ModifiableAttributeInstance}</td><td>{@link IAttributeInstance}</td></tr>
 *   <tr><td>{@code player.getBbWidth() / getBbHeight()}</td><td>{@code player.width / player.height}</td></tr>
 *   <tr><td>{@code player.getX() / getZ()}</td><td>{@code player.posX / player.posZ}</td></tr>
 *   <tr><td>{@code player.getBoundingBox().minY}</td><td>{@code player.getEntityBoundingBox().minY}</td></tr>
 *   <tr><td>{@code player.level} / {@code isLoaded}</td>
 *       <td>{@code player.world} / {@code isBlockLoaded}</td></tr>
 *   <tr><td>{@code state.getSlipperiness(level,pos,player)}</td>
 *       <td>{@code block.getSlipperiness(state,level,pos,player)}</td></tr>
 *   <tr><td>{@code setDeltaMovement(x,y,z)}</td><td>{@code motionX/Y/Z} 赋值</td></tr>
 *   <tr><td>{@code getDeltaMovement()}</td><td>{@code new Vec3d(motionX, motionY, motionZ)}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code vec.yRot(r)} / {@code vec.reverse()}</td>
 *       <td>{@code vec.rotateYaw(r)} / {@code vec.scale(-1)}</td></tr>
 *   <tr><td>{@code vec.add(x,y,z)}</td><td>同名 ✓（**1.12.2 有这个重载**）</td></tr>
 *   <tr><td>{@code level.addParticle(BlockParticleData, ...)}</td>
 *       <td>{@code level.spawnParticle(BLOCK_CRACK, ..., Block.getStateId(state))}</td></tr>
 *   <tr><td>{@code ParCoolConfig.Client.HWallRunControl.get()}</td>
 *       <td>{@code getHWallRunControl()}（字符串存 + 解析）</td></tr>
 * </table>
 *
 * <h3>★ 「墙在左还是右」怎么判</h3>
 * <p>把「墙向」与「身体朝向」当复数 {x + z·i} 做二维叉积，取 {@code dividedVec.z}：
 * <b>大于 0 表示墙在右侧</b> ✓ 这个布尔值随包发出去，供动画与他自己选择镜像分支 ✓</p>
 *
 * <h3>★ 一个反直觉的输入判定</h3>
 * <p>{@code canContinue} 里若「输入方向<b>几乎正对</b>墙面法向」（点积 &lt; -0.86）
 * 就结束跑墙 —— 也就是<b>把摇杆推向墙里会中断跑墙</b> ✓
 * 因为那说明玩家想贴上去而不是横向跑 ✓</p>
 */
public class HorizontalWallRun extends Action {

    public enum ControlType {
        PressKey, Auto
    }

    private int coolTime = 0;
    private float bodyYaw = 0;
    private boolean wallIsRightward = false;
    private Vec3d runningWallDirection = null;
    private Vec3d runningDirection = null;

    private int getMaxRunningTick(ActionInfo info) {
        Integer value = info.getClientSetting().get(ParCoolConfig.Client.Integers.WallRunContinuableTick);
        if (value == null) value = ParCoolConfig.Client.Integers.WallRunContinuableTick.DefaultValue;
        return Math.min(value, info.getServerLimitation().get(ParCoolConfig.Server.Integers.MaxWallRunContinuableTick));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (coolTime > 0) coolTime--;
    }

    /**
     * 跑墙期间的位移控制。
     *
     * <p>速度上限会按玩家的「移动速度属性倍率」放大
     * （{@code attr.getValue() / attr.getBaseValue()}）——
     * 所以喝了加速药水 / 用了速度附魔时跑墙也更快 ✓</p>
     *
     * <p>纵向速度按「光滑度 − 0.1」比例衰减，且<b>剩余时间越少衰减越强</b>
     * （乘了 {@code doingTick / maxRunningTick}）—— 即「跑到后期会往下掉」✓</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Vec3d wallDirection = WorldUtil.getRunnableWall(player, player.width * 0.65f);
        if (wallDirection == null) return;
        if (runningWallDirection == null) return;
        if (runningDirection == null) return;
        Vec3d lookVec = VectorUtil.fromYawDegree(player.renderYawOffset);
        double differenceAngle = Math.asin(
                new Vec3d(
                        lookVec.x * runningDirection.x + lookVec.z * runningDirection.z, 0,
                        -lookVec.x * runningDirection.z + lookVec.z * runningDirection.x
                ).normalize().z
        );
        bodyYaw = (float) VectorUtil.toYawDegree(lookVec.rotateYaw((float) (differenceAngle / 10)));
        Vec3d movement = new Vec3d(player.motionX, player.motionY, player.motionZ);
        BlockPos leanedBlock = new BlockPos(
                player.posX + runningWallDirection.x,
                player.getEntityBoundingBox().minY + player.height * 0.5,
                player.posZ + runningWallDirection.z
        );
        if (!player.world.isBlockLoaded(leanedBlock)) return;
        IBlockState state = player.world.getBlockState(leanedBlock);
        float slipperiness = state.getBlock().getSlipperiness(state, player.world, leanedBlock, player);
        if (slipperiness <= 0.8) {
            double speedScale = 0.2;
            IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            if (attr != null) {
                speedScale *= attr.getAttributeValue() / attr.getBaseValue();
            }
            player.motionX = runningDirection.x * speedScale;
            player.motionY = movement.y * (slipperiness - 0.1)
                    * ((double) getDoingTick()) / getMaxRunningTick(parkourability.getActionInfo());
            player.motionZ = runningDirection.z * speedScale;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        Vec3d wallDirection = WorldUtil.getRunnableWall(player, player.width * 0.65f);
        if (wallDirection == null) return false;
        Vec3d wallVec = wallDirection.normalize();
        Vec3d lookDirection = VectorUtil.fromYawDegree(player.renderYawOffset);
        lookDirection = new Vec3d(lookDirection.x, 0, lookDirection.z).normalize();
        // 把「墙向 / 视线向」当复数算夹角差 —— z 分量决定墙在左还是右
        Vec3d dividedVec =
                new Vec3d(
                        wallVec.x * lookDirection.x + wallVec.z * lookDirection.z, 0,
                        -wallVec.x * lookDirection.z + wallVec.z * lookDirection.x
                ).normalize();
        // 必须「大致侧对着墙」才算跑墙（|z| < 0.9 表示朝向太正或太背 → 不触发）
        if (Math.abs(dividedVec.z) < 0.9) {
            return false;
        }
        BufferUtil.wrap(startInfo).putBoolean(dividedVec.z > 0 /* true = 墙在右侧 */);
        Vec3d runDirection = wallVec.rotateYaw((float) (Math.PI / 2));
        if (runDirection.dotProduct(lookDirection) < 0) {
            runDirection = runDirection.scale(-1);
        }
        startInfo.putDouble(wallDirection.x)
                .putDouble(wallDirection.z)
                .putDouble(runDirection.x)
                .putDouble(runDirection.z);

        return (!parkourability.get(WallJump.class).justJumped()
                && (
                (ParCoolConfig.Client.getHWallRunControl() == ControlType.PressKey
                        && KeyBindings.getKeyHorizontalWallRun().isKeyDown())
                        || ParCoolConfig.Client.getHWallRunControl() == ControlType.Auto
        )
                && !parkourability.get(Crawl.class).isDoing()
                && !parkourability.get(Dodge.class).isDoing()
                && !parkourability.get(Vault.class).isDoing()
                && !parkourability.get(ClingToCliff.class).isDoing()
                && !ParCoolPlatform.isInWaterOrBubble(player)
                && Math.abs(player.motionY) < 0.5
                && coolTime == 0
                && !player.onGround
                && parkourability.getAdditionalProperties().getNotLandingTick() > 5
                && (
                parkourability.get(FastRun.class).canActWithRunning(player)
                        || parkourability.get(FastRun.class)
                        .getNotDashTick(parkourability.getAdditionalProperties()) < 10
        )
                && !stamina.isExhausted()
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Vec3d wallDirection = WorldUtil.getRunnableWall(player, player.width * 0.65f);
        if (wallDirection == null) return false;
        if (!(player instanceof EntityPlayerSP)) return false;
        EntityPlayerSP localPlayer = (EntityPlayerSP) player;
        if (localPlayer.movementInput == null) return false;

        // ⚠️ 1.12.2 没有 Vector2f（也没有 input.getMoveVector()）——
        //    直接用 moveStrafe / moveForward 两个 float 构造等价的输入向量
        Vec3d actualInputVector
                = new Vec3d(
                localPlayer.movementInput.moveStrafe, 0, localPlayer.movementInput.moveForward)
                .normalize()
                .rotateYaw((float) -Math.toRadians(player.rotationYaw));
        // 输入几乎正对墙面法向（= 想贴进墙里）→ 结束跑墙
        if (wallDirection.normalize().dotProduct(actualInputVector) < -0.86) {
            return false;
        }
        return (getDoingTick() < getMaxRunningTick(parkourability.getActionInfo())
                && !stamina.isExhausted()
                && !parkourability.get(WallJump.class).justJumped()
                && !parkourability.get(Crawl.class).isDoing()
                && !parkourability.get(Dodge.class).isDoing()
                && !parkourability.get(Vault.class).isDoing()
                && ((ParCoolConfig.Client.getHWallRunControl() == ControlType.PressKey
                && KeyBindings.getKeyHorizontalWallRun().isKeyDown())
                || ParCoolConfig.Client.getHWallRunControl() == ControlType.Auto)
                && !player.onGround
        );
    }

    /** 结束后 10 tick 冷却 —— 防止贴墙反复起停抖动 ✓ */
    @Override
    public void onStop(EntityPlayer player) {
        coolTime = 10;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        wallIsRightward = BufferUtil.getBoolean(startData);
        runningWallDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        runningDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.HORIZONTAL_WALL_RUN.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new HorizontalWallRunAnimator(wallIsRightward));
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        wallIsRightward = BufferUtil.getBoolean(startData);
        runningWallDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        runningDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        Animation animation = Animation.get(player);
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.HORIZONTAL_WALL_RUN.get(), 1f, 1f);
        }
        if (animation != null) {
            animation.setAnimator(new HorizontalWallRunAnimator(wallIsRightward));
        }
    }

    /**
     * 每帧校正朝向：身体朝向用算出来的 {@code bodyYaw}；
     * 视线与奔跑方向夹角超过 45° 时，把身体转向「离奔跑方向 45°」以内 ✓
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        if (isDoing()) {
            if (runningDirection == null) return;
            Vec3d lookVec = VectorUtil.fromYawDegree(player.rotationYawHead);
            double differenceAngle = Math.asin(
                    new Vec3d(
                            lookVec.x * runningDirection.x + lookVec.z * runningDirection.z, 0,
                            -lookVec.x * runningDirection.z + lookVec.z * runningDirection.x
                    ).normalize().z
            );
            if (Math.abs(differenceAngle) > Math.PI / 4) {
                player.rotationYaw = (float) VectorUtil.toYawDegree(
                        runningDirection.rotateYaw((float) (-Math.signum(differenceAngle) * Math.PI / 4))
                );
            }
            player.prevRenderYawOffset = player.renderYawOffset = bodyYaw;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        spawnRunningParticle(player);
    }

    @Override
    public void saveSynchronizedState(ByteBuffer buffer) {
        buffer.putFloat(bodyYaw);
    }

    @Override
    public void restoreSynchronizedState(ByteBuffer buffer) {
        bodyYaw = buffer.getFloat();
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }

    /** 沿「奔跑方向 − 墙向」撒粒子 —— 也就是朝<b>前进且脱离墙面</b>的方向飞 ✓ */
    @SideOnly(Side.CLIENT)
    public void spawnRunningParticle(EntityPlayer player) {
        if (!ParCoolConfig.Client.Booleans.EnableActionParticles.get()) return;
        if (runningDirection == null || runningWallDirection == null) return;
        World level = player.world;
        Vec3d pos = player.getPositionVector();
        BlockPos leanedBlock = new BlockPos(
                pos.add(runningWallDirection.x, player.height * 0.25, runningWallDirection.z)
        );
        if (!level.isBlockLoaded(leanedBlock)) return;
        float width = player.width;
        IBlockState blockstate = level.getBlockState(leanedBlock);

        Vec3d wallDirection = runningWallDirection.normalize();
        Vec3d orthogonalToWallVec = wallDirection.rotateYaw((float) (Math.PI / 2));
        Vec3d particleBaseDirection = runningDirection.subtract(wallDirection);
        if (blockstate.getRenderType() != EnumBlockRenderType.INVISIBLE) {
            Vec3d particlePos = new Vec3d(
                    pos.x + (wallDirection.x * 0.4 + orthogonalToWallVec.x * (player.getRNG().nextDouble() - 0.5D)) * width,
                    pos.y + 0.1D + 0.3 * player.getRNG().nextDouble(),
                    pos.z + (wallDirection.z * 0.4 + orthogonalToWallVec.z * (player.getRNG().nextDouble() - 0.5D)) * width
            );
            Vec3d particleSpeed = particleBaseDirection
                    .rotateYaw((float) (Math.PI * 0.2 * (player.getRNG().nextDouble() - 0.5)))
                    .scale(3 + 6 * player.getRNG().nextDouble())
                    .add(0, 1.5, 0);
            level.spawnParticle(
                    EnumParticleTypes.BLOCK_CRACK,
                    particlePos.x, particlePos.y, particlePos.z,
                    particleSpeed.x, particleSpeed.y, particleSpeed.z,
                    Block.getStateId(blockstate)
            );
        }
    }
}
