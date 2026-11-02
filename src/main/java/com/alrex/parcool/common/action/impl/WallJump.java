package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.BackwardWallJumpAnimator;
import com.alrex.parcool.client.animation.impl.WallJumpAnimator;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
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
 * WallJump —— 蹬墙跳（1.12.2 版，**第十五个真实现的动作**）。
 *
 * <p>在空中贴着墙时按蹬墙键 → 朝<b>背离墙的方向</b>弹出去，
 * 并根据视线与墙的相对角度播放三种动画之一（背翻 / 挥右臂 / 挥左臂）✓</p>
 *
 * <h3>触发条件（上游设计，照搬）</h3>
 * <ul>
 *   <li>不在空中 / 水里 / 鞘翅 / 创造飞行 → 不触发</li>
 *   <li>不在爬行、不在垂直跑墙、不在滑索上</li>
 *   <li>{@code getNotLandingTick() > 4}（落地后要缓一下）</li>
 *   <li>没在冷却中（见 {@link #isInCooldown}）</li>
 *   <li><b>关键</b>：视线与墙面法向的点积 <b>小于</b>
 *       {@code -cos(可接受角度)} —— 也就是「必须大致面朝墙」
 *       （默认 110° → 约 70° 以内）✗</li>
 * </ul>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getBbWidth()}</td><td>{@code player.width}</td></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code player.getLookVec()}</td></tr>
 *   <tr><td>{@code wall.length()}</td><td>{@code wall.lengthVector()}</td></tr>
 *   <tr><td>{@code wall.dot(vec)}</td><td>{@code wall.dotProduct(vec)}</td></tr>
 *   <tr><td>{@code vec.reverse()}</td><td>{@code vec.scale(-1)}</td></tr>
 *   <tr><td>{@code vec.add(otherVec)}</td><td>同名 ✓（1.12.2 有这个重载）</td></tr>
 *   <tr><td><b>{@code vec.add(x, y, z)}</b></td><td><b>不存在</b> → 构造新 {@code Vec3d}（**第 3 次遇到**）</td></tr>
 *   <tr><td>{@code player.isOnGround() / isInWaterOrBubble() / isFallFlying()}</td>
 *       <td>{@code player.onGround} / {@link ParCoolPlatform} 的两个包装</td></tr>
 *   <tr><td>{@code player.abilities.flying}</td><td>{@code player.capabilities.isFlying}</td></tr>
 *   <tr><td>{@code player.getX() / getZ()}</td><td>{@code player.posX / player.posZ}</td></tr>
 *   <tr><td>{@code player.getBoundingBox().minY}</td><td>{@code player.getEntityBoundingBox().minY}</td></tr>
 *   <tr><td>{@code player.level} / {@code isLoaded}</td>
 *       <td>{@code player.world} / {@code isBlockLoaded}</td></tr>
 *   <tr><td>{@code state.getSlipperiness(level,pos,player)}</td>
 *       <td>{@code block.getSlipperiness(state,level,pos,player)}</td></tr>
 *   <tr><td>{@code setDeltaMovement(x,y,z)}</td><td>{@code motionX/Y/Z} 赋值</td></tr>
 *   <tr><td>{@code getDeltaMovement().y()}</td><td>{@code player.motionY}</td></tr>
 *   <tr><td>{@code player.position()}</td><td>{@code player.getPositionVector()}</td></tr>
 *   <tr><td>{@code player.getRandom()}</td><td>{@code player.getRNG()}</td></tr>
 *   <tr><td>{@code state.getRenderShape() != BlockRenderType.INVISIBLE}</td>
 *       <td>{@code state.getRenderType() != EnumBlockRenderType.INVISIBLE}</td></tr>
 *   <tr><td>{@code level.addParticle(BlockParticleData, ...)}</td>
 *       <td>{@code level.spawnParticle(BLOCK_CRACK, ..., Block.getStateId(state))}</td></tr>
 *   <tr><td>{@code ParCoolConfig.Client.WallJumpControl.get()}</td>
 *       <td>{@code getWallJumpControl()}（字符串存 + 解析）</td></tr>
 * </table>
 *
 * <h3>★ 一个不太直白的机制：{@code startInfoTempBuffer}</h3>
 * <p>{@code canStart} 被调用时<b>不能直接算</b>（因为要等按键时机），
 * 所以上游把「预计算好的起跳信息」存在一个实例字段里：
 * {@code onClientTick} 每 tick 调用 {@link #checkCanStart} 把结果写进
 * {@code startInfoTempBuffer}，然后 {@code canStart} 只负责「按键到了就把缓存的
 * info 拷过去」✓ 这样「判定」与「触发」解耦，按键时机才准 ✓ 照搬。</p>
 */
public class WallJump extends Action {

    public enum ControlType {
        PressKey, ReleaseKey
    }

    private static final BehaviorEnforcer.ID ID_FALL_FLY_CANCEL = BehaviorEnforcer.newID();
    private static final float MAX_COOL_DOWN_TICK = 8;

    private boolean jump = false;
    private boolean inPossibleState = false;
    private final ByteBuffer startInfoTempBuffer = ByteBuffer.allocate(64);
    private final BehaviorEnforcer.Marker persistentFallFlyCanceler = () -> this.inPossibleState;

    public boolean justJumped() {
        return jump;
    }

    private boolean isInCooldown(Parkourability parkourability) {
        return (parkourability.getClientInfo().get(ParCoolConfig.Client.Booleans.EnableWallJumpCooldown)
                || !parkourability.getServerLimitation().get(ParCoolConfig.Server.Booleans.AllowDisableWallJumpCooldown))
                && getNotDoingTick() <= MAX_COOL_DOWN_TICK;
    }

    @Override
    public void onTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        jump = false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        startInfoTempBuffer.clear();
        inPossibleState = checkCanStart(player, parkourability, stamina, startInfoTempBuffer);
        startInfoTempBuffer.flip();
        parkourability.getBehaviorEnforcer().addMarkerCancellingFallFlying(ID_FALL_FLY_CANCEL, persistentFallFlyCanceler);
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }

    /**
     * 算「该往哪弹」。
     *
     * <p>先剔除「视线完全没对着墙」的情况（点积 > -cos(可接受角度) → 返回 null），
     * 然后分两种：</p>
     * <ul>
     *   <li><b>面朝墙</b>（点积 > 0）→ 做一次<b>镜面反射</b>：
     *       {@code vec.add(wall.scale(2*dot/|wall|))} 就是把视线沿墙面法向翻过去 ✓</li>
     *   <li><b>背朝墙</b>（点积 ≤ 0）→ 直接用视线方向</li>
     * </ul>
     * <p>最后再往「离墙」方向偏 0.7 并归一化，避免贴着墙起跳 ✓</p>
     */
    @SideOnly(Side.CLIENT)
    @Nullable
    private Vec3d getJumpDirection(EntityPlayer player, Vec3d wall) {
        if (wall == null) return null;
        wall = wall.normalize();
        Vec3d lookVec = player.getLookVec();
        Vec3d vec = new Vec3d(lookVec.x, 0, lookVec.z).normalize();
        Vec3d value;
        double dotProduct = wall.dotProduct(vec);

        if (dotProduct > -Math.cos(Math.toRadians(ParCoolConfig.Client.Integers.AcceptableAngleOfWallJump.get()))) {
            return null;
        }
        if (dotProduct > 0) { // 面朝墙 → 镜面反射
            double dot = vec.scale(-1).dotProduct(wall);
            Vec3d scaled = wall.scale(2 * dot / wall.length());
            value = vec.add(scaled);
        } else { // 背朝墙
            value = vec;
        }

        Vec3d awayFromWall = value.normalize().add(wall.scale(-0.7));
        return awayFromWall.normalize();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        if (inPossibleState && isInputDone() && startInfoTempBuffer.hasRemaining()) {
            startInfo.put(startInfoTempBuffer);
            startInfoTempBuffer.rewind();
            return true;
        }
        return false;
    }

    /** 按键时机：{@code PressKey} 模式要「刚按下」，{@code ReleaseKey} 模式要「刚松开」✓ */
    @SideOnly(Side.CLIENT)
    public boolean isInputDone() {
        ControlType control = ParCoolConfig.Client.getWallJumpControl();
        return (control == ControlType.PressKey && KeyRecorder.keyWallJump.isPressed())
                || (control == ControlType.ReleaseKey && KeyRecorder.keyWallJump.isReleased());
    }

    @SideOnly(Side.CLIENT)
    public boolean checkCanStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        Vec3d wallDirection = WorldUtil.getWall(player, player.width * 0.65);
        Vec3d jumpDirection = getJumpDirection(player, wallDirection);
        if (jumpDirection == null) return false;
        ClingToCliff cling = parkourability.get(ClingToCliff.class);

        boolean value = (!stamina.isExhausted()
                && !player.onGround
                && !ParCoolPlatform.isInWaterOrBubble(player)
                && !ParCoolPlatform.isFallFlying(player)
                && !player.capabilities.isFlying
                && parkourability.getAdditionalProperties().getNotCreativeFlyingTick() > 10
                && ((!cling.isDoing() && cling.getNotDoingTick() > 3)
                || (cling.isDoing() && cling.getFacingDirection() != ClingToCliff.FacingDirection.ToWall))
                && !parkourability.get(Crawl.class).isDoing()
                && !parkourability.get(VerticalWallRun.class).isDoing()
                && !parkourability.get(RideZipline.class).isDoing()
                && parkourability.getAdditionalProperties().getNotLandingTick() > 4
                && !isInCooldown(parkourability)
        );
        if (!value) return false;

        // 把「墙向 / 起跳向」当复数 (x + z i) 算夹角差，决定播哪个动画
        Vec3d dividedVec =
                new Vec3d(
                        wallDirection.x * jumpDirection.x + wallDirection.z * jumpDirection.z, 0,
                        -wallDirection.x * jumpDirection.z + wallDirection.z * jumpDirection.x
                ).normalize();
        Vec3d lookVec = player.getLookVec();
        Vec3d lookFlat = new Vec3d(lookVec.x, 0, lookVec.z).normalize();
        Vec3d lookDividedVec =
                new Vec3d(
                        lookFlat.x * wallDirection.x + lookFlat.z * wallDirection.z, 0,
                        -lookFlat.x * wallDirection.z + lookFlat.z * wallDirection.x
                ).normalize();

        WallJumpAnimationType type;
        if (lookDividedVec.x > 0.707) {
            type = WallJumpAnimationType.Back;
        } else if (dividedVec.z > 0) {
            type = WallJumpAnimationType.SwingRightArm;
        } else {
            type = WallJumpAnimationType.SwingLeftArm;
        }

        double lookAngleY = player.getLookVec().normalize().y;
        if (lookAngleY > 0.5) { // 抬头看 → 把起跳方向也抬起来（飞得更高）
            jumpDirection = jumpDirection.add(0, lookAngleY * 2, 0).normalize();
        } else {
            jumpDirection = jumpDirection.add(0, 1, 0).normalize();
        }
        startInfo
                .putDouble(jumpDirection.x)
                .putDouble(jumpDirection.y)
                .putDouble(jumpDirection.z)
                .putDouble(wallDirection.x)
                .putDouble(wallDirection.z)
                .put(type.getCode());
        return true;
    }

    /** 瞬间动作 —— 一 tick 就结束 ✓ */
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return false;
    }

    @Override
    public void onStart(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        jump = true;
        player.fallDistance = 0;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.WALL_JUMP.get(), 1f, 1f);
        }
        Vec3d jumpDirection = new Vec3d(startData.getDouble(), startData.getDouble(), startData.getDouble());
        Vec3d jumpMotion = jumpDirection.scale(0.59);
        Vec3d wallDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        Vec3d motion = new Vec3d(player.motionX, player.motionY, player.motionZ);

        BlockPos leanedBlock = new BlockPos(
                player.posX + wallDirection.x,
                player.getEntityBoundingBox().minY + player.height * 0.25,
                player.posZ + wallDirection.z
        );
        IBlockState leanedState = player.world.getBlockState(leanedBlock);
        float slipperiness = player.world.isBlockLoaded(leanedBlock)
                ? leanedState.getBlock().getSlipperiness(leanedState, player.world, leanedBlock, player)
                : 0.6f;

        double ySpeed;
        if (slipperiness > 0.9) { // 冰面：不给上升补偿（否则会滑得飞起）
            ySpeed = motion.y;
        } else {
            // 只取「当前下落速度」与「起跳上升分量」里更有利的那个
            ySpeed = motion.y > jumpMotion.y ? motion.y + jumpMotion.y : jumpMotion.y;
            spawnJumpParticles(player, wallDirection, jumpDirection);
        }
        player.motionX = motion.x + jumpMotion.x;
        player.motionY = ySpeed;
        player.motionZ = motion.z + jumpMotion.z;

        WallJumpAnimationType type = WallJumpAnimationType.fromCode(startData.get());
        Animation animation = Animation.get(player);
        if (animation != null) {
            switch (type) {
                case Back:
                    animation.setAnimator(new BackwardWallJumpAnimator());
                    break;
                case SwingLeftArm:
                    animation.setAnimator(new WallJumpAnimator(false));
                    break;
                case SwingRightArm:
                    animation.setAnimator(new WallJumpAnimator(true));
                    break;
                default:
                    break;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.WALL_JUMP.get(), 1f, 1f);
        }
        Vec3d jumpDirection = new Vec3d(startData.getDouble(), startData.getDouble(), startData.getDouble());
        Vec3d wallDirection = new Vec3d(startData.getDouble(), 0, startData.getDouble());
        BlockPos leanedBlock = new BlockPos(
                player.posX + wallDirection.x,
                player.getEntityBoundingBox().minY + player.height * 0.25,
                player.posZ + wallDirection.z
        );
        IBlockState leanedState = player.world.getBlockState(leanedBlock);
        float slipperiness = player.world.isBlockLoaded(leanedBlock)
                ? leanedState.getBlock().getSlipperiness(leanedState, player.world, leanedBlock, player)
                : 1f;
        // 注意这里与 LocalClient 相反：只在「非冰面」撒粒子（上游如此）
        if (slipperiness <= 0.9) {
            spawnJumpParticles(player, wallDirection, jumpDirection);
        }

        WallJumpAnimationType type = WallJumpAnimationType.fromCode(startData.get());
        Animation animation = Animation.get(player);
        if (animation != null) {
            switch (type) {
                case Back:
                    animation.setAnimator(new BackwardWallJumpAnimator());
                    break;
                case SwingLeftArm:
                    animation.setAnimator(new WallJumpAnimator(false));
                    break;
                case SwingRightArm:
                    animation.setAnimator(new WallJumpAnimator(true));
                    break;
                default:
                    break;
            }
        }
    }

    /** 沿「背离墙面、偏向起跳方向」的锥形撒 10 颗墙皮碎屑 ✓ */
    @SideOnly(Side.CLIENT)
    private void spawnJumpParticles(EntityPlayer player, Vec3d wallDirection, Vec3d jumpDirection) {
        if (!ParCoolConfig.Client.Booleans.EnableActionParticles.get()) return;
        World level = player.world;
        Vec3d pos = player.getPositionVector();
        BlockPos leanedBlock = new BlockPos(
                pos.add(wallDirection.x, player.height * 0.25, wallDirection.z)
        );
        if (!level.isBlockLoaded(leanedBlock)) return;
        float width = player.width;
        IBlockState blockstate = level.getBlockState(leanedBlock);

        Vec3d horizontalJumpDirection = new Vec3d(jumpDirection.x, 0, jumpDirection.z).normalize();

        wallDirection = wallDirection.normalize();
        Vec3d orthogonalToWallVec = wallDirection.rotateYaw((float) (Math.PI / 2)).normalize();

        // 把「起跳方向相对墙的共轭」当复数算 —— 得到粒子锥的朝向
        Vec3d differenceVec =
                new Vec3d(
                        -wallDirection.x * horizontalJumpDirection.x - wallDirection.z * horizontalJumpDirection.z, 0,
                        wallDirection.z * horizontalJumpDirection.x - wallDirection.x * horizontalJumpDirection.z
                );
        differenceVec = new Vec3d(differenceVec.x, 0, -differenceVec.z).normalize();
        Vec3d particleBaseDirection =
                new Vec3d(
                        -wallDirection.x * differenceVec.x + wallDirection.z * differenceVec.z, 0,
                        -wallDirection.x * differenceVec.z - wallDirection.z * differenceVec.x
                );
        if (blockstate.getRenderType() != EnumBlockRenderType.INVISIBLE) {
            for (int i = 0; i < 10; i++) {
                Vec3d particlePos = new Vec3d(
                        pos.x + (wallDirection.x * 0.4 + orthogonalToWallVec.x * (player.getRNG().nextDouble() - 0.5D)) * width,
                        pos.y + 0.1D + 0.3 * player.getRNG().nextDouble(),
                        pos.z + (wallDirection.z * 0.4 + orthogonalToWallVec.z * (player.getRNG().nextDouble() - 0.5D)) * width
                );
                Vec3d baseSpeed = particleBaseDirection
                        .rotateYaw((float) (Math.PI * 0.2 * (player.getRNG().nextDouble() - 0.5)))
                        .scale(3 + 9 * player.getRNG().nextDouble());
                Vec3d particleSpeed = new Vec3d(
                        baseSpeed.x,
                        baseSpeed.y - jumpDirection.y * 3 * player.getRNG().nextDouble(),
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

    private enum WallJumpAnimationType {
        Back, SwingRightArm, SwingLeftArm;

        public byte getCode() {
            return (byte) this.ordinal();
        }

        public static WallJumpAnimationType fromCode(byte code) {
            return values()[code];
        }
    }
}
