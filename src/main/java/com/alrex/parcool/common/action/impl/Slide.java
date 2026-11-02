package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.CrawlAnimator;
import com.alrex.parcool.client.animation.impl.SlidingAnimator;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Slide —— 滑铲（1.12.2 版，**第十九个真实现的动作**）。
 *
 * <p>疾跑中<b>爬行状态下再按一次爬行键</b> → 沿视线方向侧身滑出去 ✓
 * 它<b>依附于爬行</b>：{@code canStart} 与 {@code canContinue} 都要求
 * {@code Crawl.isDoing()}，滑完还会切回 {@link CrawlAnimator} 让爬行姿态接上 ✓</p>
 *
 * <h3>与其它动作的关系</h3>
 * <ul>
 *   <li>{@code Roll}/{@code Tap} 进行中 → 不许滑铲（那几个也是「落地瞬间」的动作）✓</li>
 *   <li>{@code FastRun} 的「持续疾跑 tick > 5」才算数 —— 即<b>必须先跑起来</b> ✓</li>
 *   <li>滑行中通过 {@code BehaviorEnforcer} 屏蔽跳跃 ✓</li>
 * </ul>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getLookAngle().multiply(1,0,1).normalize()}</td>
 *       <td>{@code new Vec3d(look.x, 0, look.z).normalize()}</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code player.getAttribute(Attributes.MOVEMENT_SPEED)}</td>
 *       <td>{@code player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED)}</td></tr>
 *   <tr><td>{@code ModifiableAttributeInstance}</td><td>{@link IAttributeInstance}</td></tr>
 *   <tr><td>{@code attr.getValue()}</td><td>{@code attr.getAttributeValue()}</td></tr>
 *   <tr><td>{@code setDeltaMovement(vec.add(0, y, 0))}</td>
 *       <td>{@code Vec3d.add(x,y,z)} 在 1.12.2 <b>存在</b>，可照搬 ✓（结果赋给 motionX/Y/Z）</td></tr>
 *   <tr><td>{@code player.blockPosition()}</td>
 *       <td>{@code new BlockPos(player)}（1.12.2 的 {@code BlockPos} 有「从实体取整」的构造器）</td></tr>
 *   <tr><td>{@code player.getBbWidth()}</td><td>{@code player.width}</td></tr>
 *   <tr><td>{@code player.position()}</td><td>{@code player.getPositionVector()}</td></tr>
 *   <tr><td>{@code player.getRandom()}</td><td>{@code player.getRNG()}</td></tr>
 *   <tr><td>{@code state.getRenderShape() != BlockRenderType.INVISIBLE}</td>
 *       <td>{@code state.getRenderType() != EnumBlockRenderType.INVISIBLE}</td></tr>
 *   <tr><td>{@code vec.reverse()}</td><td>{@code vec.scale(-1)}</td></tr>
 *   <tr><td>{@code level.addParticle(BlockParticleData, ...)}</td>
 *       <td>{@code level.spawnParticle(BLOCK_CRACK, ..., Block.getStateId(state))}</td></tr>
 * </table>
 */
public class Slide extends Action {

    private static final BehaviorEnforcer.ID ID_JUMP_CANCEL = BehaviorEnforcer.newID();

    private Vec3d slidingVec = null;

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        Vec3d look = player.getLookVec();
        Vec3d lookingVec = new Vec3d(look.x, 0, look.z).normalize();
        startInfo.putDouble(lookingVec.x).putDouble(lookingVec.z);
        return (!stamina.isExhausted()
                && KeyRecorder.keyCrawlState.isPressed()
                && player.onGround
                && !parkourability.get(Roll.class).isDoing()
                && !parkourability.get(Tap.class).isDoing()
                // ★ 关键在于「已经在爬」—— 滑铲是爬行的延伸，不是独立起手
                && parkourability.get(Crawl.class).isDoing()
                && !ParCoolPlatform.isInWaterOrBubble(player)
                // ★ 必须先疾跑起来（DashTick > 5）
                && parkourability.get(FastRun.class).getDashTick(parkourability.getAdditionalProperties()) > 5
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        int maxSlidingTick = Math.min(
                parkourability.getActionInfo().getClientSetting()
                        .get(ParCoolConfig.Client.Integers.SlidingContinuableTick),
                parkourability.getActionInfo().getServerLimitation()
                        .get(ParCoolConfig.Server.Integers.MaxSlidingContinuableTick)
        );
        // 爬行一结束，滑铲也跟着结束 —— 保持「依附于爬行」的关系
        return getDoingTick() < maxSlidingTick
                && parkourability.get(Crawl.class).isDoing();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        slidingVec = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.SLIDE.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new SlidingAnimator());
        }
        parkourability.getBehaviorEnforcer().addMarkerCancellingJump(ID_JUMP_CANCEL, this::isDoing);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        slidingVec = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.SLIDE.get(), 1f, 1f);
        }
        Animation animation = Animation.get(player);
        if (animation != null) {
            animation.setAnimator(new SlidingAnimator());
        }
    }

    /**
     * 滑行位移：速度来自「移动速度属性 × 4.5」（所以加速药水/速度附魔也更快）✓
     *
     * <p>离地时只保留 60% —— 于是滑出方块边缘会自然减速 ✓
     * 纵向速度原样保留（不额外加浮力）✓</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (slidingVec != null) {
            IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            double speedScale = 0.45;
            if (attr != null) {
                speedScale = attr.getAttributeValue() * 4.5;
            }
            Vec3d vec = slidingVec.scale(speedScale);
            Vec3d result = (player.onGround ? vec : vec.scale(0.6))
                    .add(0, player.motionY, 0);
            player.motionX = result.x;
            player.motionY = result.y;
            player.motionZ = result.z;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        spawnSlidingParticle(player);
    }

    /**
     * 滑铲结束时若还没有动画器，就切回 {@link CrawlAnimator} ——
     * 这样「滑铲 → 爬行」的姿态是连续的，不会在结束那一帧弹回站姿 ✓
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInLocalClient(EntityPlayer player) {
        Animation animation = Animation.get(player);
        if (animation != null && !animation.hasAnimator()) {
            animation.setAnimator(new CrawlAnimator());
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInOtherClient(EntityPlayer player) {
        Animation animation = Animation.get(player);
        if (animation != null && !animation.hasAnimator()) {
            animation.setAnimator(new CrawlAnimator());
        }
    }

    @Nullable
    public Vec3d getSlidingVector() {
        return slidingVec;
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }

    /** 从脚下方块溅出碎屑，方向是「滑行方向的反向 + 向上」—— 像刹车扬起的尘 ✓ */
    @SideOnly(Side.CLIENT)
    private void spawnSlidingParticle(EntityPlayer player) {
        if (!ParCoolConfig.Client.Booleans.EnableActionParticles.get()) return;
        World level = player.world;
        Vec3d pos = player.getPositionVector();
        IBlockState feetBlock = player.world.getBlockState(new BlockPos(player).down());
        float width = player.width;
        Vec3d direction = getSlidingVector();
        if (direction == null) return;

        if (feetBlock.getRenderType() != EnumBlockRenderType.INVISIBLE) {
            Vec3d particlePos = new Vec3d(
                    pos.x + (player.getRNG().nextDouble() - 0.5D) * width,
                    pos.y + 0.01D + 0.2 * player.getRNG().nextDouble(),
                    pos.z + (player.getRNG().nextDouble() - 0.5D) * width
            );
            Vec3d baseSpeed = direction
                    .scale(-1)
                    .scale(2.5 + 5 * player.getRNG().nextDouble());
            Vec3d particleSpeed = baseSpeed.add(0, 1.5, 0);
            level.spawnParticle(
                    EnumParticleTypes.BLOCK_CRACK,
                    particlePos.x, particlePos.y, particlePos.z,
                    particleSpeed.x, particleSpeed.y, particleSpeed.z,
                    Block.getStateId(feetBlock)
            );
        }
    }
}
