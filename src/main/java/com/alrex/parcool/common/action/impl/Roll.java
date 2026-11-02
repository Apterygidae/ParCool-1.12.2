package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.impl.RollAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.VectorUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * Roll —— 翻滚（1.12.2 版，**第八个真实现的动作**）。
 *
 * <p>落地瞬间朝移动方向翻滚出去（前/后/左/右），9 tick 内把水平速度交给翻滚方向。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code ClientPlayerEntity}</td><td>{@link EntityPlayerSP}</td></tr>
 *   <tr><td>{@code ClientPlayerEntity}</td><td><b>注意 {@code canStart} 里的强转目标也换成了
 *       {@link EntityPlayerSP}</b>（1.12.2 的客户端玩家类）</td></tr>
 *   <tr><td><b>{@code clientPlayer.input.leftImpulse}</b></td>
 *       <td><b>{@code movementInput.moveStrafe}</b>（1.12.2 的字段名）</td></tr>
 *   <tr><td>{@code clientPlayer.input.forwardImpulse}</td>
 *       <td>{@code movementInput.moveForward}</td></tr>
 *   <tr><td>{@code player.yBodyRot}</td><td>{@code player.renderYawOffset}</td></tr>
 *   <tr><td>{@code player.isVisuallyCrawling() / isVisuallySwimming()}</td>
 *       <td>{@link ParCoolPlatform#isVisuallySwimming}（两个在 1.12.2 是同一件事）</td></tr>
 *   <tr><td>{@code player.getBbWidth()}</td><td>{@code player.width}</td></tr>
 *   <tr><td>{@code vec.reverse()}</td><td>{@code vec.scale(-1)}</td></tr>
 *   <tr><td><b>{@code vec.yRot(弧度)}</b></td><td><b>{@code vec.rotateYaw(弧度)}</b>（公式一致，已核实）</td></tr>
 *   <tr><td>{@code setDeltaMovement(x, 0, z)}</td><td>{@code motionX/motionY/motionZ} 赋值</td></tr>
 *   <tr><td>{@code KeyBinding.isDown()}</td><td>{@code KeyBinding.isKeyDown()}</td></tr>
 * </table>
 *
 * <h3>触发方式</h3>
 * <ul>
 *   <li><b>生存</b>：由 {@code BreakfallReady.startBreakfall(...)} 触发（落地缓冲准备动作）</li>
 *   <li><b>创造</b>：开了 {@code enable_roll_creative} 后，落地瞬间按「缓冲键 + 前进」也能触发
 *       （本类 {@code onClientTick} 里的那段）</li>
 * </ul>
 *
 * <p><b>⚠️ 目前的接线状态</b>：{@code BreakfallReady} 与 {@code StartBreakfallMessage} 尚未移植
 * → 生存模式下还触发不到本动作；创造模式那条路是通的 ✓</p>
 */
public class Roll extends Action {

    private static final BehaviorEnforcer.ID ID_JUMP_CANCEL = BehaviorEnforcer.newID();

    public enum Direction {Front, Back, Left, Right}

    private int creativeCoolTime = 0;
    private boolean startRequired = false;

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (ParCool.PROXY.isLocalPlayer(player)) {
            if (KeyBindings.getKeyBreakfall().isKeyDown()
                    && KeyBindings.isKeyForwardDown()
                    && !parkourability.get(Dodge.class).isDoing()
                    && !parkourability.get(Crawl.class).isDoing()
                    && !ParCoolPlatform.isVisuallySwimming(player)
                    && ParCoolConfig.Client.Booleans.EnableRollWhenCreative.get()
                    && player.isCreative()
                    && parkourability.getAdditionalProperties().getLandingTick() <= 1
                    && player.onGround
                    && !isDoing()
                    && creativeCoolTime == 0
            ) {
                startRequired = true;
                creativeCoolTime = 20;
            }
            if (creativeCoolTime > 0) creativeCoolTime--;
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnStart;
    }

    /**
     * 从移动输入的左右/前后分量决定翻滚方向。
     *
     * <p>注意符号：1.12.2 的 {@code moveStrafe} 是「左为正」（与 1.16 的 {@code leftImpulse} 一致），
     * 所以 {@code < -0.5} 是「向右滚」✓ 与上游保持同一套映射。</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        if (!(player instanceof EntityPlayerSP)) return false;
        EntityPlayerSP clientPlayer = (EntityPlayerSP) player;
        Direction rollDirection = Direction.Front;
        if (clientPlayer.movementInput.moveStrafe < -0.5) {
            rollDirection = Direction.Right;
        } else if (clientPlayer.movementInput.moveStrafe > 0.5) {
            rollDirection = Direction.Left;
        } else if (clientPlayer.movementInput.moveForward < -0.5) {
            rollDirection = Direction.Back;
        }
        startInfo.putInt(rollDirection.ordinal());

        return startRequired;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return getDoingTick() < getRollMaxTick();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        startRequired = false;
        Direction direction = Direction.values()[startData.getInt()];
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new RollAnimator(direction));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startData) {
        startRequired = false;
        Direction direction = Direction.values()[startData.getInt()];
        double modifier = Math.sqrt(player.width);
        Vec3d vec = VectorUtil.fromYawDegree(player.renderYawOffset).scale(modifier);
        switch (direction) {
            case Back:
                vec = vec.scale(-1);
                break;
            case Right:
                vec = vec.rotateYaw((float) (-Math.PI / 2));
                break;
            case Left:
                vec = vec.rotateYaw((float) (Math.PI / 2));
                break;
            default:
                break;
        }
        player.motionX = vec.x;
        player.motionY = 0;
        player.motionZ = vec.z;
        Animation animation = Animation.get(player);
        if (animation != null) animation.setAnimator(new RollAnimator(direction));
        parkourability.getBehaviorEnforcer().addMarkerCancellingJump(ID_JUMP_CANCEL, this::isDoing);
    }

    /** 由 {@code BreakfallReady} 调用。 */
    public void startRoll(EntityPlayer player) {
        startRequired = true;
    }

    public int getRollMaxTick() {
        return 9;
    }
}
