package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.VerticalWallRunAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.VectorUtil;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * VerticalWallRun —— 垂直跑墙（1.12.2 版，**第十四个真实现的动作**）。
 *
 * <p>疾跑跳起后<b>贴着墙、抬头看墙</b>按跳跃键 → 沿墙面向上跑一段，
 * 上冲速度取决于<b>墙的高度</b>与<b>墙的光滑度</b>（糙墙冲得更高）✓</p>
 *
 * <h3>触发条件（上游设计，照搬）</h3>
 * <ul>
 *   <li>起跳后 4~13 tick 之间（{@code tickAfterLastJump}）—— 即「跳跃后的上升段」</li>
 *   <li>纵向速度接近 0（{@code |vy| <= height/5}）—— 即「到顶了」</li>
 *   <li>最近疾跑过（{@code getNotDashTick() < 8} 且 {@code getLastSprintingTick() > 12}）</li>
 *   <li>视线朝上（{@code lookVec.y > 0}）+ 视线与墙面法向点积 > 0.93（约 21° 以内）</li>
 *   <li>墙要够高（{@code getWallHeight(...) > height * 1.3}）</li>
 * </ul>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getDeltaMovement().y()}</td><td>{@code player.motionY}</td></tr>
 *   <tr><td>{@code setDeltaMovement(getDeltaMovement().multiply(1,0,1).add(0,v,0))}</td>
 *       <td>{@code motionY += v;}（XZ 不变，等价）</td></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code player.getLookVec()}</td></tr>
 *   <tr><td>{@code player.getBbHeight()}</td><td>{@code player.height}</td></tr>
 *   <tr><td>{@code player.getYHeadRot()} / {@code setYHeadRot(x)}</td>
 *       <td>{@code player.rotationYawHead}（字段读写）</td></tr>
 *   <tr><td>{@code player.yBodyRotO = player.yBodyRot = x}</td>
 *       <td>{@code player.prevRenderYawOffset = player.renderYawOffset = x}</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td><td>{@link ParCoolPlatform#isFallFlying}</td></tr>
 *   <tr><td>{@code player.level} / {@code isLoaded}</td>
 *       <td>{@code player.world} / {@code isBlockLoaded}</td></tr>
 *   <tr><td>{@code state.getSlipperiness(level, pos, player)}</td>
 *       <td>{@code block.getSlipperiness(state, level, pos, player)}</td></tr>
 *   <tr><td>{@code level.addParticle(BlockParticleData, ...)}</td>
 *       <td>{@code level.spawnParticle(BLOCK_CRACK, ..., Block.getStateId(state))}</td></tr>
 *   <tr><td>{@code vec.reverse()} / {@code vec.yRot(r)}</td>
 *       <td>{@code vec.scale(-1)} / {@code vec.rotateYaw(r)}</td></tr>
 *   <tr><td>{@code vec.add(0, y, 0)}</td><td>构造新 {@code Vec3d}</td></tr>
 * </table>
 *
 * <p><b>⚠️ {@code canStart} 上游没标 {@code @OnlyIn(CLIENT)}，但它读了 {@code KeyBindings}</b>
 * → 1.12.2 必须补 {@code @SideOnly(Side.CLIENT)} ✓</p>
 *
 * <p><b>⚠️ {@code startData.position(8 + 4)}</b> —— 上游靠「跳过已读的 double + float」
 * 来对齐到 wall 坐标。1.12.2 的 {@code ByteBuffer.position(int)} 语义相同，
 * 可以照搬 ✓</p>
 */
public class VerticalWallRun extends Action {

    private double playerYSpeed = 0;

    @Nullable
    private Vec3d wallDirection = null;

    @Override
    public void onTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        playerYSpeed = player.motionY;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        int tickAfterJump = parkourability.getAdditionalProperties().getTickAfterLastJump();
        Vec3d lookVec = player.getLookVec();
        boolean able = !stamina.isExhausted()
                && (Math.abs(player.motionY) <= player.height / 5)
                && (4 < tickAfterJump && tickAfterJump < 13)
                && getNotDoingTick() > 15
                && !ParCoolPlatform.isFallFlying(player)
                && KeyBindings.isKeyJumpDown()
                && !parkourability.get(ClingToCliff.class).isDoing()
                && !parkourability.get(Crawl.class).isDoing()
                && !parkourability.get(CatLeap.class).isDoing()
                && !parkourability.get(WallSlide.class).isDoing()
                && !parkourability.get(HorizontalWallRun.class).isDoing()
                && !parkourability.get(Vault.class).isDoing()
                && !parkourability.get(Flipping.class).isDoing()
                && parkourability.get(FastRun.class).getNotDashTick(parkourability.getAdditionalProperties()) < 8
                && parkourability.getAdditionalProperties().getLastSprintingTick() > 12
                && lookVec.y > 0;
        if (able) {
            Vec3d wall = WorldUtil.getWall(player);
            if (wall == null) return false;
            wall = wall.normalize();
            if (wall.dotProduct(VectorUtil.fromYawDegree(player.rotationYawHead)) > 0.93) {
                double height = WorldUtil.getWallHeight(player, wall, player.height * 2.2, 0.2);
                if (height > player.height * 1.3) {
                    BlockPos targetBlock = new BlockPos(
                            player.posX + wall.x,
                            player.getEntityBoundingBox().minY + player.height * 0.5,
                            player.posZ + wall.z
                    );
                    if (!player.world.isBlockLoaded(targetBlock)) return false;
                    IBlockState state = player.world.getBlockState(targetBlock);
                    float slipperiness = state.getBlock().getSlipperiness(state, player.world, targetBlock, player);
                    startInfo.putDouble(height);
                    startInfo.putFloat(slipperiness);
                    startInfo.putDouble(wall.x);
                    startInfo.putDouble(wall.y);
                    startInfo.putDouble(wall.z);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Vec3d wall = WorldUtil.getWall(player);
        if (wall == null) return false;
        wall = wall.normalize();
        return (wall.dotProduct(VectorUtil.fromYawDegree(player.rotationYawHead)) > 0.93
                && playerYSpeed > 0)
                || getDoingTick() > 30;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        double height = startData.getDouble();
        float slipperiness = startData.getFloat();
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.VERTICAL_WALL_RUN.get(), 1f, 1f);
        }
        // 上冲速度 = 系数(糙墙 0.32 / 滑墙 0.16) × √墙高 —— 墙越高冲得越远，但收益递减
        player.motionY += (slipperiness <= 0.8f ? 0.32 : 0.16) * Math.sqrt(height);
        onStartInOtherClient(player, parkourability, startData);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        startData.position(8 + 4); // 跳过已读的 height(double) 与 slipperiness(float)，对齐到 wall 坐标
        wallDirection = new Vec3d(startData.getDouble(), startData.getDouble(), startData.getDouble());
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.VERTICAL_WALL_RUN.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new VerticalWallRunAnimator());
        }
    }

    /** 跑墙时把身体朝向对齐到墙面法向。 */
    @SideOnly(Side.CLIENT)
    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        if (wallDirection != null && isDoing()) {
            player.rotationYawHead = (float) VectorUtil.toYawDegree(wallDirection);
            player.prevRenderYawOffset = player.renderYawOffset = player.rotationYawHead;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        spawnRunningParticle(player);
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }

    /** 每 tick 在脚下撒一把墙皮碎屑（向外 + 向上，比滑墙的更有冲劲）。 */
    @SideOnly(Side.CLIENT)
    public void spawnRunningParticle(EntityPlayer player) {
        if (!ParCoolConfig.Client.Booleans.EnableActionParticles.get()) return;
        if (wallDirection == null) return;
        World level = player.world;
        Vec3d pos = player.getPositionVector();
        BlockPos leanedBlock = new BlockPos(
                pos.add(wallDirection.x, player.height * 0.25, wallDirection.z)
        );
        if (!level.isBlockLoaded(leanedBlock)) return;
        float width = player.width;
        IBlockState blockstate = level.getBlockState(leanedBlock);

        Vec3d normalizedWallVec = wallDirection.normalize();
        Vec3d orthogonalToWallVec = normalizedWallVec.rotateYaw((float) (Math.PI / 2));
        if (blockstate.getRenderType() != EnumBlockRenderType.INVISIBLE) {
            Vec3d particlePos = new Vec3d(
                    pos.x + (normalizedWallVec.x * 0.4 + orthogonalToWallVec.x * (player.getRNG().nextDouble() - 0.5D)) * width,
                    pos.y + 0.1D + 0.3 * player.getRNG().nextDouble(),
                    pos.z + (normalizedWallVec.z * 0.4 + orthogonalToWallVec.z * (player.getRNG().nextDouble() - 0.5D)) * width
            );
            Vec3d baseSpeed = normalizedWallVec
                    .scale(-1)
                    .rotateYaw((float) (Math.PI * 0.2 * (player.getRNG().nextDouble() - 0.5)))
                    .scale(2 + 4 * player.getRNG().nextDouble());
            Vec3d particleSpeed = new Vec3d(baseSpeed.x, baseSpeed.y + 0.5, baseSpeed.z);
            level.spawnParticle(
                    EnumParticleTypes.BLOCK_CRACK,
                    particlePos.x, particlePos.y, particlePos.z,
                    particleSpeed.x, particleSpeed.y, particleSpeed.z,
                    Block.getStateId(blockstate)
            );
        }
    }
}
