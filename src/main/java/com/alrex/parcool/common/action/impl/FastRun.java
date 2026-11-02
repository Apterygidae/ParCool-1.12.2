package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.impl.FastRunningAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.AdditionalProperties;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ActionInfo;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * FastRun —— 疾跑（1.12.2 版，**第三个真实现的动作**）。
 *
 * <p>按住疾跑键后通过 {@code MOVEMENT_SPEED} 属性修饰符额外加速，并配一个身体前倾的动画。</p>
 *
 * <h3>1.12.2 的 API 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code Attributes.MOVEMENT_SPEED}</td>
 *       <td>{@link SharedMonsterAttributes#MOVEMENT_SPEED}</td></tr>
 *   <tr><td>{@code ModifiableAttributeInstance}</td><td>{@link IAttributeInstance}</td></tr>
 *   <tr><td>{@code player.getAttribute(...)}</td><td>{@code player.getEntityAttribute(...)}</td></tr>
 *   <tr><td>{@code attr.addTransientModifier(m)}</td><td>{@code attr.applyModifier(m)}</td></tr>
 *   <tr><td>{@code AttributeModifier.Operation.ADDITION}</td>
 *       <td><b>{@code 0}</b>（1.12.2 第 4 参是 int，不是枚举）</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code player.getVehicle()}</td><td>{@code player.getRidingEntity()}</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td><td>{@link ParCoolPlatform#isFallFlying}</td></tr>
 *   <tr><td>{@code player.isVisuallyCrawling()}</td>
 *       <td>{@link ParCoolPlatform#isVisuallySwimming}（Aqua 的客户端判定）</td></tr>
 *   <tr><td>{@code player.isSwimming()}</td><td>{@link ParCoolPlatform#isSwimming}</td></tr>
 *   <tr><td>{@code player.isShiftKeyDown()}</td><td>{@code player.isSneaking()}</td></tr>
 *   <tr><td>{@code KeyBinding.isDown()}</td><td>{@code KeyBinding.isKeyDown()}</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td><td>{@link ParCool#PROXY}{@code .isLocalPlayer(player)}</td></tr>
 *   <tr><td>{@code ParCoolConfig.Client.FastRunControl.get()}</td>
 *       <td>{@code ParCoolConfig.Client.getFastRunControl()}（字符串存 + 解析）</td></tr>
 * </table>
 *
 * <p><b>{@code canStart} 直接复用 {@code canContinue}</b>（上游如此）——
 * 「能继续跑就意味着能开始跑」。</p>
 *
 * <p>它引用的 {@code Roll}/{@code Tap}/{@code ClingToCliff}/{@code HangDown}/{@code RideZipline}/
 * {@code HideInBlock} 目前还是骨架（{@code isDoing()} 恒 false）—— 那些 {@code !xxx.isDoing()}
 * 判断会恒为真，**不影响本动作工作**，只是暂时少了「这些动作进行中就别疾跑」的约束。</p>
 */
public class FastRun extends Action {

    public enum ControlType {
        PressKey, Toggle, Auto
    }

    private static final String FAST_RUNNING_MODIFIER_NAME = "parcool.modifier.fast_run";
    private static final UUID FAST_RUNNING_MODIFIER_UUID = UUID.randomUUID();
    private double speedModifier = 0;
    private boolean toggleStatus = false;
    private int lastDashTick = 0;

    public double getSpeedModifier(ActionInfo info) {
        return Math.min(
                info.getClientSetting().get(ParCoolConfig.Client.Doubles.FastRunSpeedModifier),
                info.getServerLimitation().get(ParCoolConfig.Server.Doubles.MaxFastRunSpeedModifier)
        );
    }

    /**
     * 服务端侧：加/刷新速度修饰符。
     *
     * <p>每 tick 先移除旧的再按需添加 —— 避免叠加，也保证 {@code speedModifier} 变化时即时生效。</p>
     *
     * <p>注意 {@code speedModifier / 100d}：上游把它当作「百分比」，而修饰符的第 3 参是增幅比例
     * （{@code 0.02} = +2%）—— 所以默认配置值 2 实际是 +2% 移速。</p>
     */
    @Override
    public void onServerTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (attr == null) return;
        AttributeModifier old = attr.getModifier(FAST_RUNNING_MODIFIER_UUID);
        if (old != null) attr.removeModifier(old);
        if (isDoing()) {
            player.setSprinting(true);
            attr.applyModifier(new AttributeModifier(
                    FAST_RUNNING_MODIFIER_UUID,
                    FAST_RUNNING_MODIFIER_NAME,
                    speedModifier / 100d,
                    0   // 0 = ADDITION（1.12.2 用 int，不是 AttributeModifier.Operation 枚举）
            ));
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (ParCool.PROXY.isLocalPlayer(player)) {
            if (ParCoolConfig.Client.getFastRunControl() == ControlType.Toggle
                    && parkourability.getAdditionalProperties().getSprintingTick() > 3
            ) {
                if (KeyRecorder.keyFastRunning.isPressed()) {
                    toggleStatus = !toggleStatus;
                }
            } else {
                toggleStatus = false;
            }
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        return canContinue(player, parkourability, stamina);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return (!stamina.isExhausted()
                && !ParCoolPlatform.isInWaterOrBubble(player)
                && player.getRidingEntity() == null
                && !ParCoolPlatform.isFallFlying(player)
                && player.isSprinting()
                && !ParCoolPlatform.isVisuallySwimming(player)
                && !ParCoolPlatform.isSwimming(player)
                && !player.isSneaking()
                && !parkourability.get(Crawl.class).isDoing()
                && !parkourability.get(ClingToCliff.class).isDoing()
                && !parkourability.get(HangDown.class).isDoing()
                && !parkourability.get(RideZipline.class).isDoing()
                && !parkourability.get(HideInBlock.class).isDoing()
                && ((ParCoolConfig.Client.getFastRunControl() == ControlType.PressKey
                        && KeyBindings.getKeyFastRunning().isKeyDown())
                || (ParCoolConfig.Client.getFastRunControl() == ControlType.Toggle && toggleStatus)
                || ParCoolConfig.Client.getFastRunControl() == ControlType.Auto)
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Animation animation = Animation.get(player);
        if (animation != null && !animation.hasAnimator()) {
            animation.setAnimator(new FastRunningAnimator());
        }
    }

    @Override
    public void onStartInServer(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        speedModifier = getSpeedModifier(parkourability.getActionInfo());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onStopInLocalClient(EntityPlayer player) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        lastDashTick = getDashTick(parkourability.getAdditionalProperties());
    }

    /**
     * 其它动作问「现在算不算在疾跑」时的统一入口。
     *
     * <p>开了 {@code SubstituteSprintForFastRun} 时，「原版疾跑」也算疾跑 ——
     * 这样不需要按额外键也能触发依赖 FastRun 的动作。</p>
     */
    @SideOnly(Side.CLIENT)
    public boolean canActWithRunning(EntityPlayer player) {
        return ParCoolConfig.Client.Booleans.SubstituteSprintForFastRun.get() ? player.isSprinting() : this.isDoing();
    }

    /** 开着 substitute 时返回原版疾跑的持续 tick 数，否则返回本动作的。 */
    @SideOnly(Side.CLIENT)
    public int getDashTick(AdditionalProperties properties) {
        return ParCoolConfig.Client.Booleans.SubstituteSprintForFastRun.get()
                ? properties.getSprintingTick()
                : this.getDoingTick();
    }

    @SideOnly(Side.CLIENT)
    public int getNotDashTick(AdditionalProperties properties) {
        return ParCoolConfig.Client.Booleans.SubstituteSprintForFastRun.get()
                ? properties.getNotSprintingTick()
                : this.getNotDoingTick();
    }

    public int getLastDashTick() {
        return lastDashTick;
    }
}
