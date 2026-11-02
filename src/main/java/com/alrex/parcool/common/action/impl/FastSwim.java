package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.impl.FastSwimAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
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
 * FastSwim —— 快速游泳（1.12.2 版，**第十七个真实现的动作**）。
 *
 * <p>在水里疾跑时按住疾跑键 → 游得更快 ✓ 与 {@link FastRun} 共用同一个按键与「按住/切换/自动」
 * 三种控制模式（{@code fast-run_control}）—— 这是上游的设计：<b>陆上一套、水下一套</b> ✓</p>
 *
 * <h3>⚠️ 一处刻意的降级：`ForgeMod.SWIM_SPEED` → `MOVEMENT_SPEED`</h3>
 * <p>上游用 {@code ForgeMod.SWIM_SPEED.get()} 这个<b>专用游泳速度属性</b> ——
 * 但<b>那是 1.16 才加的，1.12.2 根本没有</b> ✗</p>
 * <p>本工程改用 {@link SharedMonsterAttributes#MOVEMENT_SPEED}（1.12.2 的水中移动本来就吃它）：</p>
 * <ul>
 *   <li>修改量除以 8（与上游一致，因为游泳的速度基数小）✓</li>
 *   <li>修饰符<b>只在 {@code isDoing()} 期间存在</b>，而 {@code canContinue} 要求
 *       {@code isInWaterOrBubble} → <b>不会影响陆地移动</b> ✓</li>
 * </ul>
 * <p><b>结论：效果等价，但作用渠道不同</b> —— 若将来装了什么"水中移动速度"相关的 mod，
 * 那个 mod 看的是原版属性，不一定感知到这个修饰符 ✓ 属可接受的降级。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td><b>{@code ForgeMod.SWIM_SPEED.get()}</b></td>
 *       <td><b>{@code SharedMonsterAttributes.MOVEMENT_SPEED}</b>（见上）</td></tr>
 *   <tr><td>{@code player.getAttribute(...)}</td><td>{@code player.getEntityAttribute(...)}</td></tr>
 *   <tr><td>{@code ModifiableAttributeInstance}</td><td>{@link IAttributeInstance}</td></tr>
 *   <tr><td>{@code attr.addTransientModifier(m)}</td><td>{@code attr.applyModifier(m)}</td></tr>
 *   <tr><td>{@code AttributeModifier.Operation.ADDITION}</td><td>{@code 0}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code player.isSwimming()}</td><td>{@link ParCoolPlatform#isSwimming}</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td><td>{@link ParCoolPlatform#isFallFlying}</td></tr>
 *   <tr><td>{@code player.getVehicle()}</td><td>{@code player.getRidingEntity()}</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td><td>{@link ParCool#PROXY}{@code .isLocalPlayer(...)}</td></tr>
 *   <tr><td>{@code KeyBinding.isDown()}</td><td>{@code isKeyDown()}</td></tr>
 *   <tr><td>{@code ParCoolConfig.Client.FastRunControl.get()}</td>
 *       <td>{@code getFastRunControl()}（与 FastRun 共用）</td></tr>
 * </table>
 */
public class FastSwim extends Action {

    private static final String FAST_SWIM_MODIFIER_NAME = "parcool.modifier.fastswimming";
    private static final UUID FAST_SWIM_MODIFIER_UUID = UUID.randomUUID();

    private double speedModifier = 0;
    private boolean toggleStatus;

    public double getSpeedModifier(ActionInfo info) {
        return Math.min(
                info.getClientSetting().get(ParCoolConfig.Client.Doubles.FastSwimSpeedModifier),
                info.getServerLimitation().get(ParCoolConfig.Server.Doubles.MaxFastSwimSpeedModifier)
        );
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
                && ParCoolPlatform.isInWaterOrBubble(player)
                && player.getRidingEntity() == null
                && !ParCoolPlatform.isFallFlying(player)
                && player.isSprinting()
                && ParCoolPlatform.isSwimming(player)
                // 与 FastRun 互斥：陆上那套在水里不该生效，反之亦然
                && !parkourability.get(FastRun.class).isDoing()
                && ((ParCoolConfig.Client.getFastRunControl() == FastRun.ControlType.PressKey
                        && KeyBindings.getKeyFastRunning().isKeyDown())
                || (ParCoolConfig.Client.getFastRunControl() == FastRun.ControlType.Toggle && toggleStatus)
                || ParCoolConfig.Client.getFastRunControl() == FastRun.ControlType.Auto)
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (ParCool.PROXY.isLocalPlayer(player)) {
            if (ParCoolConfig.Client.getFastRunControl() == FastRun.ControlType.Toggle
                    && parkourability.getAdditionalProperties().getSprintingTick() > 3
                    && ParCoolPlatform.isInWaterOrBubble(player)
                    && ParCoolPlatform.isSwimming(player)
            ) {
                if (KeyRecorder.keyFastRunning.isPressed()) {
                    toggleStatus = !toggleStatus;
                }
            } else {
                toggleStatus = false;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Animation animation = Animation.get(player);
        if (animation != null && !animation.hasAnimator()) {
            animation.setAnimator(new FastSwimAnimator());
        }
    }

    @Override
    public void onStartInServer(EntityPlayer player, Parkourability parkourability, ByteBuffer startData) {
        speedModifier = parkourability.get(FastSwim.class).getSpeedModifier(parkourability.getActionInfo());
    }

    /**
     * 服务端侧：加/刷新游泳加速修饰符。
     *
     * <p>每 tick 先移除旧的再按需添加 —— 与 {@code FastRun} 同样的写法 ✓</p>
     */
    @Override
    public void onServerTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        // ⚠️ 1.12.2 没有 ForgeMod.SWIM_SPEED → 改用 MOVEMENT_SPEED（见类注释的降级说明）
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (attr == null) return;
        AttributeModifier old = attr.getModifier(FAST_SWIM_MODIFIER_UUID);
        if (old != null) attr.removeModifier(old);
        if (isDoing()) {
            player.setSprinting(true);
            attr.applyModifier(new AttributeModifier(
                    FAST_SWIM_MODIFIER_UUID,
                    FAST_SWIM_MODIFIER_NAME,
                    speedModifier / 8d,
                    0   // 0 = ADDITION
            ));
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }
}
