package com.alrex.parcool.client.animation;

import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.impl.ClingToCliff;
import com.alrex.parcool.common.action.impl.Dive;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.alrex.parcool.utilities.EasingFunctions;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 「**被动自定义动画**」—— 没有动作动画器时的兜底动画 ✓
 *
 * <h3>★ 它是什么</h3>
 * <p>ParCool 大多数动画由<b>动作</b>驱动（动作启动 → {@code setAnimator} ✗）。
 * 但有一类姿态<b>不属于任何动作</b> ✗ —— 只要满足条件就自动出现 ✓：
 * <b>从高空自由坠落时的张臂姿势</b> ✓
 * （实测：用户在上游看到「从高空坠落自动触发」的姿态，正是这里产生的 ✓）</p>
 *
 * <h3>★ 触发条件（照搬上游）</h3>
 * <ul>
 *   <li>{@code fallingAnimationTick >= 14} —— 连续满足 14 tick 才起手（避免刚离地就摆姿势 ✗）✓</li>
 *   <li>这 14 tick 的成立条件：<b>不在落地 + {@code fallDistance > 1}
 *       + 不是创造飞行 + 不在鞘翅滑翔 + 没在攀墙</b> ✓</li>
 *   <li>受配置 {@code enable_falling_animation} 控制（默认开 ✓）</li>
 *   <li><b>{@code Dive} 动作进行中时不叠加</b> ✓（俯冲有自己的动画 ✗）</li>
 * </ul>
 *
 * <h3>★ 姿态本身</h3>
 * <p>双臂向两侧抬 80°、双腿各外撇 10°，配合动态摆动 —— 就是「张臂下坠」✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code player.abilities.flying}</td><td>{@code player.capabilities.isFlying}</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td>
 *       <td>{@link ParCoolPlatform#isFallFlying}（内含 Aqua 的姿态判断 ✓）</td></tr>
 * </table>
 *
 * <p><b>⚠️ 上游还有「创造飞行姿态」那半部分（{@code animateCreativeFlying} 等）
 * —— 那些方法在上游<b>没有任何调用点</b>（{@code animate()} 不调它们 ✗），属死代码 ✗
 * → 本移植<b>刻意省略</b>，并在此标注 ✓ 若将来上游启用，再补 ✓</b></p>
 */
@SideOnly(Side.CLIENT)
public class PassiveCustomAnimation {

    /** 要连续满足多少 tick 才起手 ✓ */
    private static final int FALLING_START_LINE = 14;

    private int fallingAnimationTick = 0;

    /** 每 tick 更新状态（由 {@code Animation#tick} 调用 ✓）。 */
    public void tick(EntityPlayer player, Parkourability parkourability) {
        if (!player.onGround
                && player.fallDistance > 1
                && !player.capabilities.isFlying
                && !ParCoolPlatform.isFallFlying(player)
                && !parkourability.get(ClingToCliff.class).isDoing()
        ) {
            fallingAnimationTick++;
        } else {
            fallingAnimationTick = 0;
        }
    }

    /**
     * 施加姿态（由 {@code Animation#animatePost} 在「没有动作动画器」时调用 ✓）。
     *
     * <p>注意它用的是 {@code addRotate*} —— <b>叠加</b>在原版姿态之上 ✓，
     * 这样手臂/腿仍是「在默认姿态基础上再抬」✓</p>
     */
    public void animate(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        if (fallingAnimationTick >= FALLING_START_LINE
                && ParCoolConfig.Client.Booleans.EnableFallingAnimation.get()
                && !parkourability.get(Dive.class).isDoing()
        ) {
            animateFalling(transformer);
        }
    }

    /** 空实现 —— 上游如此（该被动动画不做整体旋转 ✓），照搬以保持调用结构一致 ✓ */
    public void rotate(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
    }

    private void animateFalling(PlayerModelTransformer transformer) {
        // 起手后 14 tick 内从 0 平滑过渡到满幅度 ✓
        float phase = (fallingAnimationTick + transformer.getPartialTick() - FALLING_START_LINE) / 14f;
        float factor = phase > 1 ? 1 : EasingFunctions.SinInOutBySquare(phase);
        transformer
                .addRotateRightArm(0, 0, (float) Math.toRadians(80 * factor))
                .addRotateLeftArm(0, 0, (float) Math.toRadians(-80 * factor))
                .addRotateRightLeg(0, 0, (float) Math.toRadians(10 * factor))
                .addRotateLeftLeg(0, 0, (float) Math.toRadians(-10 * factor))
                .makeArmsMoveDynamically(factor)
                .makeLegsShakingDynamically(factor)
                .end();
    }
}
