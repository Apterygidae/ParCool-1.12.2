package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.client.animation.impl.WallSlideAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.damage.DamageSources;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
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
 * WallSlide —— 滑墙（1.12.2 版，**第十三个真实现的动作**）。
 *
 * <p>贴着墙下落时按住滑墙键 → 顺着墙面下滑，速度被墙的<b>光滑度</b>影响
 * （滑面下得更快），并会<b>持续擦伤掉血</b>（下滑速度越快扣得越狠）。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getDeltaMovement().y()}</td><td>{@code player.motionY}</td></tr>
 *   <tr><td>{@code getDeltaMovement()}</td><td>{@code new Vec3d(motionX, motionY, motionZ)}</td></tr>
 *   <tr><td>{@code setDeltaMovement(vec)}</td><td>{@code motionX/Y/Z} 赋值</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code player.abilities.flying}</td><td>{@code player.capabilities.isFlying}</td></tr>
 *   <tr><td>{@code player.getBbHeight() / getBbWidth()}</td><td>{@code player.height / player.width}</td></tr>
 *   <tr><td>{@code player.getBoundingBox().minY}</td><td>{@code player.getEntityBoundingBox().minY}</td></tr>
 *   <tr><td>{@code player.getX() / getZ()}</td><td>{@code player.posX / player.posZ}</td></tr>
 *   <tr><td>{@code player.level}</td><td>{@code player.world}</td></tr>
 *   <tr><td>{@code level.isLoaded(pos)}</td><td>{@code level.isBlockLoaded(pos)}</td></tr>
 *   <tr><td><b>{@code state.getSlipperiness(level, pos, player)}</b></td>
 *       <td><b>{@code block.getSlipperiness(state, level, pos, player)}</b>（1.12.2 挂在 Block 上，四参）</td></tr>
 *   <tr><td><b>{@code player.invulnerableTime}</b></td>
 *       <td><b>{@code player.hurtResistantTime}</b>（1.12.2 的字段名）</td></tr>
 *   <tr><td>{@code player.hurt(src, dmg)}</td><td>{@code player.attackEntityFrom(src, dmg)}</td></tr>
 *   <tr><td>{@code player.getRandom()}</td><td>{@code player.getRNG()}</td></tr>
 *   <tr><td>{@code player.position()}</td><td>{@code player.getPositionVector()}</td></tr>
 *   <tr><td><b>{@code level.addParticle(BlockParticleData, ...)}</b></td>
 *       <td>{@code level.spawnParticle(}{@link EnumParticleTypes}{@code .BLOCK_CRACK, x,y,z, vx,vy,vz, }
 *           {@code Block.getStateId(state))}</td></tr>
 *   <tr><td>{@code state.getRenderShape() != BlockRenderType.INVISIBLE}</td>
 *       <td>{@code state.getRenderType() != EnumBlockRenderType.INVISIBLE}</td></tr>
 *   <tr><td>{@code vec.yRot(弧度)} / {@code vec.reverse()}</td>
 *       <td>{@code vec.rotateYaw(弧度)} / {@code vec.scale(-1)}</td></tr>
 *   <tr><td>{@code vec.add(x, y, z)}</td><td>构造新 {@code Vec3d}（1.12.2 没有这个重载）</td></tr>
 * </table>
 *
 * <p><b>关于擦伤</b>：这是本动作的核心机制，不是「伤害处理」的一部分 ——
 * 所以随本动作一起移植 ✓ 用的是 {@link DamageSources#WALL_SLIDE}，
 * 并且<b>临时把 {@code hurtResistantTime} 清零</b>来绕过原版的受伤无敌帧
 * （否则每秒最多掉一次血）✓ 这是上游的写法，照搬。</p>
 */
public class WallSlide extends Action {

    @Nullable
    private Vec3d leanedWallDirection = null;
    private byte particleSpawnCoolTime = 0;
    private double startYSpeed = 0;
    private int damageCount = 0, takenDamageCount = 0;
    private byte damageCoolTime = 0;

    @Nullable
    public Vec3d getLeanedWallDirection() {
        return leanedWallDirection;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        // 把「开始下滑时的纵向速度」传给服务端 —— 它用来算总擦伤次数
        startInfo.putDouble(Math.abs(player.motionY));
        return canContinue(player, parkourability, stamina);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Vec3d wall = WorldUtil.getWall(player);
        return (wall != null
                && !player.onGround
                && !parkourability.get(FastRun.class).isDoing()
                && !parkourability.get(Dodge.class).isDoing()
                && !player.capabilities.isFlying
                && player.motionY <= 0
                && KeyBindings.getKeyWallSlide().isKeyDown()
                && !stamina.isExhausted()
                && !parkourability.get(Dive.class).isDoing()
                && !parkourability.get(ClingToCliff.class).isDoing()
                // 抓墙刚结束的 12 tick 内不许滑墙（避免两个动作互相打架）
                && parkourability.get(ClingToCliff.class).getNotDoingTick() > 12
        );
    }

    @Override
    public void onStart(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        particleSpawnCoolTime = 0;
    }

    /** 只服务端跑：把「这次下滑总共该擦伤几次」定下来（客户端传上来的初速决定）。 */
    @Override
    public void onStartInServer(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        startYSpeed = startData.getDouble();
        damageCount = (int) (5.5 * (startYSpeed - 1.) / player.height);
        takenDamageCount = 0;
        damageCoolTime = 0;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Animation animation = Animation.get(player);
        if (animation != null && !animation.hasAnimator()) {
            animation.setAnimator(new WallSlideAnimator());
        }
        particleSpawnCoolTime--;
        if (particleSpawnCoolTime <= 0) {
            particleSpawnCoolTime = 2;
            spawnSlideParticle(player);
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }

    /**
     * 双端都跑：按墙面光滑度修正下滑速度。
     *
     * <p>{@code slipperiness} 取平方根 —— 上游的意图是「让差异别太极端」。
     * 注意 {@code motionY} 直接乘上去：滑面（冰，0.98）几乎不减速，
     * 糙面（0.6）会明显刹住 ✓ 同时 {@code fallDistance} 也按同系数衰减，
     * 这样贴着墙滑下去不会吃全额摔伤 ✓</p>
     */
    @Override
    public void onWorkingTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        leanedWallDirection = WorldUtil.getWall(player);
        if (leanedWallDirection != null) {
            BlockPos leanedBlock = new BlockPos(
                    player.posX + leanedWallDirection.x,
                    player.getEntityBoundingBox().minY + player.height * 0.75,
                    player.posZ + leanedWallDirection.z
            );
            if (!player.world.isBlockLoaded(leanedBlock)) return;
            IBlockState state = player.world.getBlockState(leanedBlock);
            float slipperiness = state.getBlock().getSlipperiness(state, player.world, leanedBlock, player);
            slipperiness = (float) Math.sqrt(slipperiness);
            player.fallDistance *= slipperiness;
            player.motionX *= 0.8;
            player.motionY *= slipperiness;
            player.motionZ *= 0.8;
        }
    }

    /**
     * 只服务端跑：分次擦伤。
     *
     * <p>{@code damageCoolTime} 让两次扣血之间至少隔 1 tick；
     * 而把 {@code hurtResistantTime} 临时清零是为了<b>绕过原版的受伤无敌帧</b> ——
     * 否则每 10 tick 才能扣一次，总伤害会少得多 ✓</p>
     */
    @Override
    public void onWorkingTickInServer(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (damageCoolTime <= 0 && damageCount > takenDamageCount++) {
            int invulnerableTime = player.hurtResistantTime; // 临时绕过无敌帧
            damageCoolTime = 1;
            player.hurtResistantTime = 0;
            player.attackEntityFrom(DamageSources.WALL_SLIDE, 0.3f);
            player.hurtResistantTime = invulnerableTime;
        } else {
            damageCoolTime--;
        }
    }

    /** 每 2 tick 撒一次墙皮碎屑（沿墙面法向 + 切向随机偏移）。 */
    @SideOnly(Side.CLIENT)
    private void spawnSlideParticle(EntityPlayer player) {
        if (!ParCoolConfig.Client.Booleans.EnableActionParticles.get()) return;
        if (leanedWallDirection == null) return;
        if (player.getRNG().nextBoolean()) return;   // 一半概率跳过 —— 让粒子稀一点
        World level = player.world;
        Vec3d pos = player.getPositionVector();
        BlockPos leanedBlock = new BlockPos(
                pos.add(leanedWallDirection.x, player.height * 0.25, leanedWallDirection.z)
        );
        if (!level.isBlockLoaded(leanedBlock)) return;
        float width = player.width;
        IBlockState blockstate = level.getBlockState(leanedBlock);

        Vec3d normalizedWallVec = leanedWallDirection.normalize();
        Vec3d orthogonalToWallVec = normalizedWallVec.rotateYaw((float) (Math.PI / 2));
        if (blockstate.getRenderType() != EnumBlockRenderType.INVISIBLE) {
            Vec3d particlePos = new Vec3d(
                    pos.x + (normalizedWallVec.x * 0.4 + orthogonalToWallVec.x * (player.getRNG().nextDouble() - 0.5D)) * width,
                    pos.y + player.height - 0.2D + 0.3 * player.getRNG().nextDouble(),
                    pos.z + (normalizedWallVec.z * 0.4 + orthogonalToWallVec.z * (player.getRNG().nextDouble() - 0.5D)) * width
            );
            // ⚠️ 1.12.2 的 Vec3d 没有 add(x,y,z) 三参重载 —— 拆成两步
            Vec3d baseSpeed = normalizedWallVec
                    .scale(-1)
                    .rotateYaw((float) (Math.PI * 0.1 * (player.getRNG().nextDouble() - 0.5)))
                    .scale(0.05);
            Vec3d particleSpeed = new Vec3d(
                    baseSpeed.x,
                    baseSpeed.y - 0.5 - player.getRNG().nextDouble(),
                    baseSpeed.z
            );
            level.spawnParticle(
                    EnumParticleTypes.BLOCK_CRACK,
                    particlePos.x, particlePos.y, particlePos.z,
                    particleSpeed.x, particleSpeed.y, particleSpeed.z,
                    Block.getStateId(blockstate)
            );
        }
    }
}
