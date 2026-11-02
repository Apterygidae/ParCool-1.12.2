package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.client.animation.impl.RideZiplineAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity;
import com.alrex.parcool.common.zipline.Zipline;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.BufferUtil;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * 「**滑滑索**」动作 ✓ —— 1.12.2 版（整类重写 ✗）。
 *
 * <p>玩家在空中按 <b>右键</b>抓住附近的滑索 ✓，沿索滑行 ✓，
 * 蓄积的重力势能从加速度体现出来 ✓，松开或撞到东西就脱手 ✓。</p>
 *
 * <h3>★ 状态机骨架</h3>
 * <ol>
 *   <li>{@link #canStart} —— 按着键 + 在空中 + 附近**够得着**一条索 ✓</li>
 *   <li>{@link #onStart} —— 记下索的两端偏移 ✓、禁掉鞘翅 ✓</li>
 *   <li>{@link #onWorkingTickInLocalClient} —— **每 tick 算速度与参数 t** ✓（只在本地客户端 ✓）</li>
 *   <li>{@link #onWorkingTick} —— 速度清零 ✓（位置由 BehaviorEnforcer 强制 ✓）</li>
 *   <li>{@link #onStopInLocalClient} —— 松手时把速度**还给玩家** ✓（冲量 ✓）</li>
 * </ol>
 *
 * <h3>🚨 1.12.2 的整片 API 都不同 ✗</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.level}</td><td>{@code player.world} ✗</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround} ✗（字段 ✓）</td></tr>
 *   <tr><td>{@code player.isCrouching()}</td><td>{@code isSneaking()} ✗</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td><td>{@link ParCoolPlatform#isFallFlying} ✗</td></tr>
 *   <tr><td>{@code player.isSwimming()}</td><td>{@link ParCoolPlatform#isSwimming} ✗</td></tr>
 *   <tr><td>{@code player.isInWall()}</td>
 *       <td><b>没有</b> ✗ → {@code isCollidedHorizontally && isCollidedVertically} ✓（近似 ✓）</td></tr>
 *   <tr><td>{@code KeyBindings...isDown()}</td><td>{@code isKeyDown()} ✗</td></tr>
 *   <tr><td>{@code ClientPlayerEntity}</td><td>{@code EntityPlayerSP} ✗（且在 client 包 ✗）</td></tr>
 *   <tr><td>{@code Attributes.MOVEMENT_SPEED}</td><td>{@code SharedMonsterAttributes.MOVEMENT_SPEED} ✗</td></tr>
 *   <tr><td>{@code speedAttr.getValue()}</td><td>{@code getAttributeValue()} ✗</td></tr>
 *   <tr><td>{@code ForgeMod.ENTITY_GRAVITY}</td>
 *       <td><b>没有这个属性</b> ✗ → 用硬编码 {@code 0.08} ✓</td></tr>
 *   <tr><td>{@code input.leftImpulse / forwardImpulse}</td>
 *       <td>{@code movementInput.moveStrafe / moveForward} ✗</td></tr>
 *   <tr><td>{@code player.getLookAngle()}</td><td>{@code getLookVec()} ✗</td></tr>
 *   <tr><td>{@code player.position()}</td><td>{@code getPositionVector()} ✗</td></tr>
 *   <tr><td>{@code Vector3d.ZERO}</td><td>{@code Vec3d.ZERO} ✗</td></tr>
 *   <tr><td>{@code player.setDeltaMovement(v)}</td>
 *       <td><b>没有 setter</b> ✗ → 直接写 {@code motionX/Y/Z} ✓</td></tr>
 * </table>
 *
 * <h3>★ 位置是怎么被"强制"的</h3>
 * <p>本动作**不直接改坐标** ✓，而是通过 {@link BehaviorEnforcer} 挂两个标记：</p>
 * <ul>
 *   <li>{@code setMarkerEnforceMovePoint} ✓ —— 每 tick 提供一个**目标点** ✓，
 *       由 mixin 在 {@code move()} 里替换掉玩家的位移 ✓</li>
 *   <li>{@code addMarkerCancellingSprint} ✓ —— 滑索期间**禁止疾跑** ✓</li>
 * </ul>
 * <p><b>⭐ 所以本动作要真正生效，还需要那个消费标记的 mixin</b> ✗
 * （见 {@code mixin/client/EntityPlayerSPMixin} ✓）——
 * 上游对应 {@code ClientPlayerEntityMixin} ✓。</p>
 */
public class RideZipline extends Action {

    /** 1.12.2 没有「实体重力属性」✗ → 用原版硬编码值 ✓ */
    private static final double GRAVITY = 0.08;

    private static final BehaviorEnforcer.ID ID_FALL_FLY_CANCEL = BehaviorEnforcer.newID();
    private static final BehaviorEnforcer.ID ID_SPRINT_CANCEL = BehaviorEnforcer.newID();

    @Nullable
    private ZiplineRopeEntity ridingZipline;
    @Nullable
    private Vec3d endOffsetFromStart;
    private double speed;
    private double acceleration;
    private double slope;
    private float currentT;
    @Nullable
    private Vec3d currentPos;
    private boolean previouslyStopByCollision = false;

    // ── 供 RideZiplineAnimator 读取 ✓ ───────────────────────────────────

    public double getAcceleration() {
        return acceleration;
    }

    public double getSlope() {
        return slope;
    }

    @Nullable
    public Vec3d getEndOffsetFromStart() {
        return endOffsetFromStart;
    }

    // ── 启动条件 ─────────────────────────────────────────────────────────

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        if (KeyBindings.getKeyRideZipline().isKeyDown()
                && !player.onGround
                && !player.isInWater()
                && !ParCoolPlatform.isFallFlying(player)
                && !player.isSneaking()
                && !ParCoolPlatform.isSwimming(player)
                && !stamina.isExhausted()
                // 刚跳过、或刚因碰撞脱手时的冷却 ✓（否则会立刻又抓住 ✓）
                && (!KeyBindings.isKeyJumpDown() || getNotDoingTick() > 5)
                // ⚠️ 这里保持与上游一致的 **5 tick** ✓
                //   （我曾一度改成 20 ✗ 想压住「反复抓住/脱手」的振荡 ✓，
                //    但后来查明**振荡的真因不是冷却** ✗ ——
                //    而是「启动前没有预检目标位置」✗ ，见本方法下方的 landingBox 检测 ✓。
                //    真因修掉后，冷却就没必要偏离上游了 ✓）
                && (!previouslyStopByCollision || getNotDoingTick() > 5)
                // 其它占用手脚的动作进行中时不能上索 ✓
                && !parkourability.get(Vault.class).isDoing()
                && !parkourability.get(HangDown.class).isDoing()
                && !parkourability.get(Flipping.class).isDoing()
                && !parkourability.get(HorizontalWallRun.class).isDoing()
                && !parkourability.get(VerticalWallRun.class).isDoing()
        ) {
            ZiplineRopeEntity ropeEntity = Zipline.getHangableZipline(player.world, player);
            if (ropeEntity == null) return false;
            Zipline zipline = ropeEntity.getZipline();
            double t = zipline.getParameter(player.getPositionVector());
            // 抓取点必须落在索的有效参数区间内 ✓
            if (t < 0 || 1 < t) return false;

            // ── ★ 启动【前】的预检：目标位置会不会撞到方块 ✗★ ────────────
            //   ⚠️ 这一段是为了修「在原地来回抽插」的振荡 bug ✗（2026-10-11 ✓）：
            //   玩家在索**下方**按住右键跳跃时 ✓，抓取点判定能过 ✓，
            //   但启动后的**目标点（索上 ✗）正好落进方块里** ✗
            //   → 下一 tick 判碰撞脱手 ✓ → 冲量又把他推回索下 ✓ → 冷却过后再抓 ✓ → 死循环 ✓
            //
            //   ✅ 所以启动前先算一次「挂点对应的玩家身体盒」✓，
            //      如果那里有方块 → **根本不该启动** ✓（和"启动后立刻脱手"等价 ✓，
            //      但不会产生那一帧的强制位移和冲量 ✓）
            float clampedT = (float) MathHelper.clamp(t, 0, 1);
            Vec3d hangPos = zipline.getMidPoint(clampedT)
                    .subtract(0, player.height * 1.11, 0);
            AxisAlignedBB landingBox = player.getEntityBoundingBox().offset(
                    hangPos.x - player.posX, hangPos.y - player.posY, hangPos.z - player.posZ);
            if (!player.world.getCollisionBoxes(player, landingBox).isEmpty()) return false;

            ridingZipline = ropeEntity;
            // 把「索的两端偏移」随启动包传给其他端 ✓
            BufferUtil.wrap(startInfo).putVector3d(zipline.getOffsetToEndFromStart());
            return true;
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        // 撞到任何东西就脱手 ✓，并标记「因碰撞而停」✓（下次抓取要冷却 ✓）
        if (player.collidedHorizontally || player.collidedVertically) {
            previouslyStopByCollision = true;
            return false;
        }
        return KeyBindings.getKeyRideZipline().isKeyDown()
                && !KeyRecorder.keyJumpState.isPressed()
                && !stamina.isExhausted()
                && ridingZipline != null
                && ridingZipline.isEntityAlive()
                && 0 <= currentT && currentT <= 1;
    }

    // ── 启动钩子 ─────────────────────────────────────────────────────────

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        if (ridingZipline == null) return;
        rideNewZipline(ridingZipline, player.getPositionVector(), getMotion(player));

        player.setSprinting(false);

        // ★ 每 tick 告诉 mixin「玩家应该在哪」✓（真正的位移替换在 mixin 里 ✓）
        parkourability.getBehaviorEnforcer().setMarkerEnforceMovePoint(
                this::isDoing,
                () -> {
                    if (currentPos == null) return null;
                    // 挂点在**索上** ✓，而玩家身体要**吊在索下面** ✗ → 往下挪一点 ✓
                    return currentPos.subtract(0, player.height * 1.11, 0);
                }
        );
        parkourability.getBehaviorEnforcer().addMarkerCancellingSprint(ID_SPRINT_CANCEL, this::isDoing);

        Animation animation = Animation.get(player);
        if (animation == null) return;
        animation.setAnimator(new RideZiplineAnimator());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        // 旁观者只播动画 ✓（位置由服务端同步 ✓）
        Animation animation = Animation.get(player);
        if (animation == null) return;
        animation.setAnimator(new RideZiplineAnimator());
    }

    @Override
    public void onStart(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        previouslyStopByCollision = false;
        endOffsetFromStart = BufferUtil.getVector3d(startData);
        player.setSprinting(false);
        parkourability.getBehaviorEnforcer().addMarkerCancellingFallFlying(ID_FALL_FLY_CANCEL, this::isDoing);
    }

    // ── 每 tick 的运动学（只在本地客户端算 ✓，结果随同步包发给其他端 ✓）────

    @Override
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (ridingZipline == null) return;
        if (!(player instanceof EntityPlayerSP)) return;
        IAttributeInstance speedAttr = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        EntityPlayerSP clientPlayer = (EntityPlayerSP) player;
        double oldSpeed = speed;
        Zipline zipline = ridingZipline.getZipline();

        slope = zipline.getSlope(currentT);
        // 空气阻力 ✓
        speed *= 0.98;
        // 水里更黏 ✓
        if (player.isInWater()) speed *= 0.8;
        // ★ 重力沿索方向的分量 ✗ —— 「滑下坡会加速」的来源 ✓
        //   slope 是 dy/d(水平距离) ✓，除以 sqrt(slope²+1) 得到沿索的分量 ✓
        speed -= GRAVITY * slope * MathHelper.fastInvSqrt(slope * slope + 1);

        // ★ 用前进键"蹬"索可加一点速度 ✓ —— 只有朝索的方向蹬才有效 ✓
        Vec3d input = new Vec3d(-clientPlayer.movementInput.moveStrafe, 0, clientPlayer.movementInput.moveForward);
        Vec3d offset = zipline.getOffsetToEndFromStart();
        if (input.lengthSquared() > 0.01) {
            // ⚠️ 1.12.2 的 Vec3d 没有 multiply(1,0,1) ✗ → 手动把 y 清零 ✓
            Vec3d look = player.getLookVec()
                    .rotateYaw((float) Math.toRadians(VectorUtil.toYawDegree(input)));
            double dot = new Vec3d(look.x, 0, look.z).normalize()
                    .dotProduct(new Vec3d(offset.x, 0, offset.z).normalize());
            // 上限 0.08 ✓，随移动速度属性缩放 ✓
            speed += Math.min(dot * 0.01 * (speedAttr.getAttributeValue() / speedAttr.getBaseValue()), 0.08);
        }

        currentT = (float) zipline.getMovedPositionByParameterApproximately(currentT, (float) speed);
        acceleration = speed - oldSpeed;
        currentPos = zipline.getMidPoint(currentT);
    }

    /** 开始滑一条新索 ✓ —— 初速度 = **当前动量在索方向上的投影** ✓。 */
    private void rideNewZipline(ZiplineRopeEntity ropeEntity, Vec3d position, Vec3d motion) {
        ridingZipline = ropeEntity;
        Zipline zipline = ropeEntity.getZipline();
        acceleration = 0;
        currentT = MathHelper.clamp(zipline.getParameter(position), 0, 1);
        currentPos = zipline.getMidPoint(currentT);
        slope = zipline.getSlope(currentT);
        speed = motion.dotProduct(getSpeedScale(zipline, currentT));
    }

    /** 沿索的**单位方向向量** ✓（带竖直分量 ✓）。 */
    private static Vec3d getSpeedScale(Zipline zipline, float t) {
        double yScale = zipline.getSlope(t);
        Vec3d pointsOffset = zipline.getOffsetToEndFromStart();
        double xzInv = MathHelper.fastInvSqrt(pointsOffset.x * pointsOffset.x + pointsOffset.z * pointsOffset.z);
        return new Vec3d(pointsOffset.x * xzInv, yScale, pointsOffset.z * xzInv).normalize();
    }

    /** 按当前速度算方向速度向量 ✓（脱手时还给玩家 ✓）。 */
    private static Vec3d getDeltaMovement(Zipline zipline, double speed, float t) {
        return getSpeedScale(zipline, t).scale(speed);
    }

    // ── 状态同步（加速度与斜率要发给其他端播动画 ✓）────────────────────

    @Override
    public void saveSynchronizedState(ByteBuffer buffer) {
        buffer.putDouble(acceleration);
        buffer.putDouble(slope);
    }

    @Override
    public void restoreSynchronizedState(ByteBuffer buffer) {
        acceleration = buffer.getDouble();
        slope = buffer.getDouble();
    }

    // ── 每 tick（两端）────────────────────────────────────────────────────
    @Override
    public void onWorkingTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        // 位置由 BehaviorEnforcer 强制 ✓ → 这里把速度清零 ✓，
        // 免得玩家物理和强制位移打架 ✓（也顺带免摔伤 ✓）
        player.fallDistance = 0;
        setMotion(player, Vec3d.ZERO);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInLocalClient(EntityPlayer player) {
        if (ridingZipline != null) {
            // ★ 脱手时把速度**还回去** ✓ —— 顺着惯性飞出去 ✓（与上游完全一致 ✓）
            //   按着跳键时额外给一点向上 ✓
            //
            // ⚠️ 我一度在这里加了「因碰撞脱手就清零速度」✗ ——
            //   那是**没找到真因时**为了压住「原地来回抽插」的症状 ✓。
            //   后来查明**真因是「启动前没有预检目标位置」** ✗
            //   （见 canStart 里的 landingBox 检测 ✓），
            //   真因既然修掉，这条症状压制就**撤销** ✓
            //   —— 保留它会让碰撞脱手时玩家「突然静止」✓，与上游手感不符 ✓。
            Vec3d out = getDeltaMovement(ridingZipline.getZipline(), speed, currentT)
                    .add(0, KeyBindings.isKeyJumpDown() ? 0.25 : 0, 0);
            setMotion(player, out);
        }
        currentT = 0;
        currentPos = null;
        acceleration = 0;
        speed = 0;
        slope = 0;
    }

    @Override
    public void onStop(EntityPlayer player) {
        ridingZipline = null;
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }

    // ── 1.12.2 的「速度」是三个字段，没有 setter ✗ ──────────────────────

    private static Vec3d getMotion(EntityPlayer player) {
        return new Vec3d(player.motionX, player.motionY, player.motionZ);
    }

    private static void setMotion(EntityPlayer player, Vec3d v) {
        player.motionX = v.x;
        player.motionY = v.y;
        player.motionZ = v.z;
    }
}
