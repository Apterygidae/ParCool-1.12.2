package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.RenderBehaviorEnforcer;
import com.alrex.parcool.client.animation.impl.HideInBlockAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.BufferUtil;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * HideInBlock —— 藏进方块（1.12.2 版，**第二十五个真实现的动作**）。
 *
 * <p>对着草垛 / 树叶按藏身键 → <b>整个人钻进去</b>，外面看不见你（名字也不显示）✓
 * 从俯冲状态落到方块上还能直接滑进去 ✓</p>
 *
 * <h3>★★ 本动作踩了 4 个「1.12.2 完全没有」的概念 —— 这是它被推迟到最后的原因</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td><b>{@code Pose} / {@code getPose() / setPose()}</b></td>
 *       <td><b>没有姿态系统</b> ✗ → 上游用它的两处都改成等价判断：<br>
 *           {@code getPose() == CROUCHING} → {@code player.isSneaking()} ✓<br>
 *           {@code setPose(STANDING)} → <b>直接去掉</b> ——
 *           本动作本来就挂了「屏蔽潜行」的标记，效果一致 ✓</td></tr>
 *   <tr><td><b>{@code PointOfView.THIRD_PERSON_BACK}</b></td>
 *       <td><b>没有这个枚举</b> ✗ → 见 {@link RenderBehaviorEnforcer}：
 *           用 {@code int thirdPersonView} + 每 tick 写回字段 ✓（还省掉一个 mixin）</td></tr>
 *   <tr><td><b>{@code mc.hitResult} / {@code BlockRayTraceResult}</b></td>
 *       <td>→ {@code Minecraft.getMinecraft().objectMouseOver} +
 *           {@code result.typeOfHit == RayTraceResult.Type.BLOCK} ✓</td></tr>
 *   <tr><td><b>{@code mc.particleEngine.destroy(pos, state)}</b></td>
 *       <td>→ {@code Minecraft.getMinecraft().effectRenderer.addBlockDestroyEffects(pos, state)} ✓</td></tr>
 * </table>
 *
 * <h3>其它替换</h3>
 * <table border="1">
 *   <tr><td>{@code player.noPhysics}</td><td>{@code player.noClip}</td></tr>
 *   <tr><td>{@code player.isPassenger()}</td><td>{@code player.isRiding()}</td></tr>
 *   <tr><td>{@code player.isVisuallySwimming()}</td><td>{@link ParCoolPlatform#isVisuallySwimming}</td></tr>
 *   <tr><td>{@code player.blockPosition()}</td><td>{@code new BlockPos(player)}</td></tr>
 *   <tr><td>{@code player.getBbHeight()}</td><td>{@code player.height}</td></tr>
 *   <tr><td>{@code player.position()}</td><td>{@code player.getPositionVector()}</td></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code player.getLookVec()}</td></tr>
 *   <tr><td>{@code player.setDeltaMovement(Vector3d.ZERO)}</td><td>{@code motionX/Y/Z = 0}</td></tr>
 *   <tr><td>{@code player.setPos(x,y,z)}</td><td>{@code player.setPosition(x,y,z)}</td></tr>
 *   <tr><td>{@code world.isLoaded(pos)}</td><td>{@code world.isBlockLoaded(pos)}</td></tr>
 * </table>
 *
 * <h3>★ 状态同步的要点</h3>
 * <p>{@code keyPressed} 通过 {@code saveSynchronizedState} 同步给服务端 ——
 * 因为 {@code isStandbyInAir()}（「从俯冲接进来」的判定）在<b>服务端也要用</b> ✓
 * 而那个判定依赖「藏身键是否按着」✗（客户端输入）→ 必须同步 ✓</p>
 */
public class HideInBlock extends Action {

    private static final BehaviorEnforcer.ID ID_SHOW_NAME = BehaviorEnforcer.newID();
    private static final BehaviorEnforcer.ID ID_SNEAK = BehaviorEnforcer.newID();

    @Nullable
    Vec3d hidingPoint = null;
    @Nullable
    Tuple<BlockPos, BlockPos> hidingArea = null;
    @Nullable
    Vec3d enterPoint = null;
    @Nullable
    Vec3d lookDirection = null;
    boolean hidingBlockChanged = false;
    boolean keyPressed;
    boolean startedFromDiving;

    @Nullable
    public Vec3d getLookDirection() {
        return lookDirection;
    }

    @Nullable
    public Tuple<BlockPos, BlockPos> getHidingArea() {
        return hidingArea;
    }

    /** 是否处于「从俯冲接进来」的待命状态（服务端也要判 → 靠同步来的 keyPressed ✓）。 */
    public boolean isStandbyInAir(Parkourability parkourability) {
        if (!keyPressed) return false;
        Dive dive = parkourability.get(Dive.class);
        return (dive.isDoing() || dive.getNotDoingTick() < 2)
                && (parkourability.getAdditionalProperties().getLandingTick() <= 1);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        if (player.isSprinting()
                || player.noClip
                || !player.onGround
                || player.isInWater()
                || player.isRiding()
                || ParCoolPlatform.isVisuallySwimming(player)
                || getNotDoingTick() < 6
                || player.hurtTime > 0
                || parkourability.get(Crawl.class).isDoing()
        ) {
            return false;
        }

        BlockPos hideBaseBlockPos = null;
        boolean startFromDiving = false;
        if (isStandbyInAir(parkourability)) {
            hideBaseBlockPos = new BlockPos(player).down();
            startFromDiving = true;
        } else if (KeyBindings.getKeyHideInBlock().isKeyDown()
                && (!ParCoolConfig.Client.Booleans.HideInBlockSneakNeeded.get() || player.isSneaking())) {
            // 1.12.2: 用 objectMouseOver + typeOfHit 判断，而不是 instanceof BlockRayTraceResult ✓
            RayTraceResult result = Minecraft.getMinecraft().objectMouseOver;
            if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK && parkourability.isDoingNothing()) {
                hideBaseBlockPos = result.getBlockPos();
            }
        }

        if (hideBaseBlockPos != null) {
            Tuple<BlockPos, BlockPos> hideArea = WorldUtil.getHideAbleSpace(player, hideBaseBlockPos);
            if (hideArea == null) return false;
            Vec3d hidePoint = new Vec3d(
                    0.5 + (hideArea.getFirst().getX() + hideArea.getSecond().getX()) / 2.,
                    Math.min(hideArea.getFirst().getY(), hideArea.getSecond().getY()),
                    0.5 + (hideArea.getFirst().getZ() + hideArea.getSecond().getZ()) / 2.
            );
            if (player.getPositionVector().distanceTo(hidePoint) >= 1.8) return false;
            {
                int minX = Math.min(hideArea.getFirst().getX(), hideArea.getSecond().getX());
                int maxX = Math.max(hideArea.getFirst().getX(), hideArea.getSecond().getX());
                int minY = Math.min(hideArea.getFirst().getY(), hideArea.getSecond().getY());
                int maxY = Math.max(hideArea.getFirst().getY(), hideArea.getSecond().getY());
                int minZ = Math.min(hideArea.getFirst().getZ(), hideArea.getSecond().getZ());
                int maxZ = Math.max(hideArea.getFirst().getZ(), hideArea.getSecond().getZ());
                hideArea = new Tuple<>(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
            }
            Vec3d direction;
            // 「站姿」= 空间高度够站（方块只有一格高时才算得进）
            boolean stand = player.height < (hideArea.getSecond().getY() - hideArea.getFirst().getY() + 1);
            if (stand && startFromDiving) return false;
            if (stand) {
                Vec3d lookAngle = player.getLookVec();
                direction = Math.abs(lookAngle.x) > Math.abs(lookAngle.z)
                        ? new Vec3d(lookAngle.x > 0 ? 1 : -1, 0, 0)
                        : new Vec3d(0, 0, lookAngle.z > 0 ? 1 : -1);
            } else {
                boolean zLonger = Math.abs(hideArea.getFirst().getZ() - hideArea.getSecond().getZ())
                        > Math.abs(hideArea.getFirst().getX() - hideArea.getSecond().getX());
                direction = zLonger
                        ? new Vec3d(0, 0, player.getLookVec().z > 0 ? 1 : -1)
                        : new Vec3d(player.getLookVec().x > 0 ? 1 : -1, 0, 0);
            }
            BufferUtil.wrap(startInfo)
                    .putBoolean(stand)
                    .putBoolean(startFromDiving)
                    .putBlockPos(hideArea.getFirst())
                    .putBlockPos(hideArea.getSecond())
                    .putVector3d(hidePoint)
                    .putVector3d(player.getPositionVector())
                    .putVector3d(direction);
            return true;
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (hidingBlockChanged) {
            // 藏身处方块被破坏 → 立刻结束（并把标记清掉）✓
            return hidingBlockChanged = false;
        }
        // 1.12.2: 「站姿」用 !isSneaking 代替 getPose() == STANDING ✓
        //
        // ⚠️⚠️⚠️ 但这里**必须去掉**那个判定 —— 实测（2026-10-02）：
        //   1.16 的 onStart 会 `setPose(Pose.STANDING)` 强制站姿 → 所以 `getPose()==STANDING` 恒成立 ✓
        //   1.12.2 没有姿态系统，而玩家此刻正**蹲着**（潜行就是本动作的触发方式 ✗）
        //   → 保留判定会让 canContinue 在**第一 tick 就失败** ✗
        //   → 实测症状：「模型抽搐一下后归位」（动作刚起就结束）✗
        return (player.hurtTime <= 0 || (startedFromDiving && getDoingTick() < 10))
                && (getDoingTick() < 6
                || KeyBindings.getKeyHideInBlock().isKeyDown()
                || KeyBindings.getKeySneak().isKeyDown());
    }

    @Override
    public void onStart(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        boolean _stand = BufferUtil.getBoolean(startData);
        startedFromDiving = BufferUtil.getBoolean(startData);
        hidingArea = new Tuple<>(BufferUtil.getBlockPos(startData), BufferUtil.getBlockPos(startData));
        hidingPoint = BufferUtil.getVector3d(startData);
        enterPoint = BufferUtil.getVector3d(startData);
        lookDirection = BufferUtil.getVector3d(startData);
        if (startedFromDiving) {
            parkourability.getBehaviorEnforcer().setMarkerEnforcePosition(this::isDoing, () -> hidingPoint);
        } else {
            parkourability.getBehaviorEnforcer().setMarkerEnforcePosition(this::isDoing, () -> {
                // 第 0 tick 只走 3/4 路程 —— 让「钻进去」有个过程 ✓
                if (getDoingTick() == 0) {
                    return hidingPoint.subtract(enterPoint).scale(0.75).add(enterPoint);
                }
                return hidingPoint;
            });
        }
        parkourability.getBehaviorEnforcer().addMarkerCancellingSneak(ID_SNEAK, this::isDoing);
        // 1.12.2: 没有 Pose —— setPose(STANDING) 的意图是「别蹲着」，已由上面的 sneak 标记覆盖 ✓
        player.noClip = true;
        playBreakSound(player);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        boolean stand = BufferUtil.getBoolean(startData);
        // 藏进去时强制第三人称（第一人称会看到方块内部）✓
        RenderBehaviorEnforcer.setMarkerEnforceCameraType(
                this::isDoing, () -> RenderBehaviorEnforcer.THIRD_PERSON_BACK);
        parkourability.getBehaviorEnforcer().addMarkerCancellingShowName(ID_SHOW_NAME, this::isDoing);
        spawnOnHideParticles(player);
        Animation animation = Animation.get(player);
        if (animation == null) return;
        animation.setAnimator(new HideInBlockAnimator(stand, startedFromDiving));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        boolean stand = BufferUtil.getBoolean(startData);
        Animation animation = Animation.get(player);
        parkourability.getBehaviorEnforcer().addMarkerCancellingShowName(ID_SHOW_NAME, this::isDoing);
        spawnOnHideParticles(player);
        if (animation == null) return;
        animation.setAnimator(new HideInBlockAnimator(stand, startedFromDiving));
    }

    /**
     * 服务端侧：每 tick 把玩家位置钉死在隐藏点 ✓
     *
     * <p>这是「藏进去」真正生效的地方 —— 客户端那边还可能被别的力推动，
     * 这里强制拉回来 ✓</p>
     */
    @Override
    public void onWorkingTickInServer(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (hidingPoint == null) return;
        player.setPosition(hidingPoint.x, hidingPoint.y, hidingPoint.z);
    }

    /**
     * 藏身期间：钉住速度/朝向、关掉碰撞，并**强制解除潜行** ✓
     *
     * <p><b>★ 这一行就是上游 {@code addMarkerCancellingSneak} 的等价物</b> ✓
     * 上游靠 {@code mixin/client/ClientPlayerEntityMixin} 在 {@code isSneaking()} 被问时返回 false；
     * 本工程没有那个 mixin，改为**每 tick 直接把潜行状态设回 false** ✓
     * 位置在 vanilla 处理完输入之后（{@code PlayerTickEvent} 的 END 阶段）→ 不会被覆盖 ✓</p>
     */
    @Override
    public void onWorkingTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
        player.noClip = true;
        player.setSprinting(false);
        // 上游靠 addMarkerCancellingSneak 实现，见方法注释 ✓
        player.setSneaking(false);
        // 1.12.2: 没有 Pose ✓
    }

    /**
     * 本地客户端：把玩家「钉」在隐藏点上 ✓
     *
     * <p><b>★ 这是上游 {@code getEnforcedPosition()} 那条 mixin 的等价物</b> ——
     * {@code onWorkingTickInServer} 只管服务端，而玩家的位置是<b>客户端权威</b>的 ✗
     * → 客户端不自己移过去的话，画面上的角色只会「原地抽搐」✗（实测症状）✓</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (hidingPoint == null) return;
        player.setPosition(hidingPoint.x, hidingPoint.y, hidingPoint.z);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        keyPressed = KeyBindings.getKeyHideInBlock().isKeyDown();
        // 把「强制第三人称」写回 gameSettings（1.12.2 的 thirdPersonView 是公开字段）✓
        RenderBehaviorEnforcer.apply();
    }

    /** 结束瞬间把玩家从隐藏点「弹」回入口点（分两小步，看起来是钻出来而不是瞬移）✓ */
    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInLocalClient(EntityPlayer player) {
        final Vec3d hidePos = hidingPoint;
        final Vec3d entPos = enterPoint;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        parkourability.getBehaviorEnforcer().setMarkerEnforcePosition(
                () -> this.getNotDoingTick() <= 1,
                () -> {
                    if (getNotDoingTick() == 0) {
                        return entPos.subtract(hidePos).scale(0.65).add(hidePos);
                    }
                    return entPos;
                });
        spawnOnHideParticles(player);
        playBreakSound(player);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInOtherClient(EntityPlayer player) {
        spawnOnHideParticles(player);
        playBreakSound(player);
    }

    @Override
    public void onStop(EntityPlayer player) {
        // ★ 上游这里用 setMarkerEnforcePosition 做「退出时推回入口点」，
        //   但那套 marker 在本工程没有消费方 ✗ → 直接一次性设回去 ✓
        //   （必须做：noClip 恢复后玩家会卡在实心方块里 ✗）
        if (player.noClip && enterPoint != null) {
            player.setPosition(enterPoint.x, enterPoint.y, enterPoint.z);
        }
        hidingPoint = null;
        enterPoint = null;
        hidingArea = null;
        lookDirection = null;
        player.noClip = false;
    }

    /** 钻进去 / 钻出来时播放「藏身处方块被破坏」的音效 ✓ */
    private void playBreakSound(EntityPlayer player) {
        // ⚠️ 位置必须用隐藏点，不能用玩家当前位置 ——
        //    onStart 那一刻玩家还没被移过去 ✗ 取到的是脚下的地面（石头/泥土）✗
        //    （实测症状：藏进草垛却播放石头被破坏的音效 ✗）
        if (hidingPoint == null) return;
        BlockPos pos = new BlockPos(hidingPoint.add(0, 0.2, 0));
        net.minecraft.block.state.IBlockState state = player.world.getBlockState(pos);
        player.playSound(state.getBlock().getSoundType(state, player.world, pos, player).getBreakSound(), 1, 1);
    }

    /** 把藏身处每一格方块都「打碎」一遍 —— 进/出时各一次，视觉上像钻进去/钻出来 ✓ */
    @SideOnly(Side.CLIENT)
    private void spawnOnHideParticles(EntityPlayer player) {
        if (hidingArea == null) return;
        World world = player.world;
        int minX = hidingArea.getFirst().getX();
        int minY = hidingArea.getFirst().getY();
        int minZ = hidingArea.getFirst().getZ();
        int maxX = hidingArea.getSecond().getX();
        int maxY = hidingArea.getSecond().getY();
        int maxZ = hidingArea.getSecond().getZ();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isBlockLoaded(pos)) break;
                    // 1.12.2: particleEngine.destroy() → effectRenderer.addBlockDestroyEffects() ✓
                    Minecraft.getMinecraft().effectRenderer.addBlockDestroyEffects(pos, world.getBlockState(pos));
                }
            }
        }
    }

    @Override
    public void saveSynchronizedState(ByteBuffer buffer) {
        BufferUtil.wrap(buffer).putBoolean(keyPressed);
    }

    @Override
    public void restoreSynchronizedState(ByteBuffer buffer) {
        keyPressed = BufferUtil.getBoolean(buffer);
    }

    private boolean isHidingBlock(BlockPos pos) {
        if (hidingArea == null) return false;
        BlockPos posA = hidingArea.getFirst(), posB = hidingArea.getSecond();
        return (posA.getX() <= pos.getX() && pos.getX() <= posB.getX()
                && posA.getY() <= pos.getY() && pos.getY() <= posB.getY()
                && posA.getZ() <= pos.getZ() && pos.getZ() <= posB.getZ()
        );
    }

    /**
     * 「这个位置是不是<b>本地玩家当前藏身处</b>的一部分」—— 供相机修正在渲染期查询 ✓
     *
     * <h3>★ 为什么相机需要问这个</h3>
     * <p>藏进方块后玩家眼睛在方块内部 → 第三人称相机那条遮挡射线<b>起点就在方块里</b> ✗
     * → 立刻命中 → 相机被压到脸前 ✗</p>
     * <p><b>⚠️ 但不能把这条射线整个无视掉</b> ✗ ——
     * 那样相机会一路穿进<b>周围的墙</b>，造成<b>透视</b> ✗（实测确认 ✓）</p>
     * <p><b>✅ 正确做法</b>：只无视「遮挡物是藏身方块自己」的那些命中 ✓
     * 其它方块照常阻挡 → 相机会停在墙上 → 既不卡脸前、也不透视 ✓</p>
     *
     * <p>这也正是上游 1.16 的表现（相机退到藏身方块外、紧贴周围墙壁 ✓）✓</p>
     */
    @SideOnly(Side.CLIENT)
    public static boolean isHidingBlockAt(BlockPos pos) {
        net.minecraft.entity.player.EntityPlayer local = Minecraft.getMinecraft().player;
        if (local == null) return false;
        Parkourability parkourability = Parkourability.get(local);
        if (parkourability == null) return false;
        HideInBlock hiding = parkourability.get(HideInBlock.class);
        return hiding.isDoing() && hiding.isHidingBlock(pos);
    }

    /** 藏身处方块被改变时由外部通知（上游由一个 mixin 在方块变化时调用）✓ */
    @SideOnly(Side.CLIENT)
    public void notifyBlockChanged(BlockPos pos) {
        if (isHidingBlock(pos)) {
            hidingBlockChanged = true;
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }
}
