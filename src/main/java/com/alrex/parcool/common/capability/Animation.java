package com.alrex.parcool.common.capability;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.unstable.animation.AnimationOption;
import com.alrex.parcool.api.unstable.animation.AnimationPart;
import com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent;
import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PassiveCustomAnimation;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.capability.capabilities.ClientCapabilities;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * 动画 Capability（1.12.2 版）—— 挂在玩家身上、持有当前 {@link Animator} 的那一层。
 *
 * <p>它对外只暴露四组转发：{@code rotatePre/Post}（整个模型朝向）、
 * {@code animatePre/Post}（各部位角度）、{@code cameraSetup}（相机）、
 * {@code tick/onRenderTick}（驱动）。真正的注入点见 {@code ModelPlayerMixin} / {@code RenderPlayerMixin}。</p>
 *
 * <h3>1.12.2 差异</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code LazyOptional.isPresent()/orElseThrow()}</td>
 *       <td>{@code getCapability(...)} 直接返回或 {@code null}</td></tr>
 *   <tr><td>{@code Minecraft.getInstance().options.getCameraType().isFirstPerson()}</td>
 *       <td>{@code Minecraft.getMinecraft().gameSettings.thirdPersonView == 0}</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td>
 *       <td>{@link ParCool#PROXY}{@code .isLocalPlayer(player)}</td></tr>
 *   <tr><td>{@code AbstractClientPlayerEntity} / {@code ClientPlayerEntity}</td>
 *       <td>{@code AbstractClientPlayer} / {@code EntityPlayerSP}</td></tr>
 * </table>
 *
 * <p><b>TODO（两处，都不会产生可见差异）：</b></p>
 * <ul>
 *   <li><b>passiveAnimation</b>：上游在「没有动作时」也会跑 {@code PassiveCustomAnimation}
 *       （下落/创造飞行的被动姿态）。该类的 {@code ClingToCliff} / {@code Dive} 依赖尚未移植的动作类，
 *       等 P4-B 落地后补。</li>
 *   <li><b>canAnimate(Class&lt;? extends Animator&gt;)</b>：上游按动画器逐个开关。
 *       现在还没有任何具体 Animator，该开关无从观测，等 P5-B 补齐动画器时一并接入。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class Animation {

    @Nullable
    public static Animation get(EntityPlayer player) {
        return player.getCapability(ClientCapabilities.ANIMATION_CAPABILITY, null);
    }

    @Nullable
    private Animator animator = null;
    /** 被动动画（自由坠落姿态等）—— 没有动作动画器时生效 ✓ */
    private final PassiveCustomAnimation passiveAnimation = new PassiveCustomAnimation();
    private AnimationOption option = new AnimationOption();

    public void setAnimator(Animator animator) {
        if (!ParCoolConfig.Client.Booleans.EnableAnimation.get()) return;
        // ★ 逐动画器开关 ✓（上游同此）—— 玩家可以单独关掉不喜欢的动画 ✓
        //   注意是【直接返回】而不是清空已有的 animator ✓（上游行为 ✓）
        if (!ParCoolConfig.canAnimate(animator.getClass())) return;
        this.animator = animator;
    }

    public boolean animatePre(EntityPlayer player, PlayerModelTransformer modelTransformer) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return false;
        if (animator != null && animator.shouldRemoved(player, parkourability)) animator = null;
        if (animator == null) return false;
        modelTransformer.setOption(option);
        if (shouldCancelAnimation(player)) return false;
        return animator.animatePre(player, parkourability, modelTransformer);
    }

    public void animatePost(EntityPlayer player, PlayerModelTransformer modelTransformer) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        if (shouldCancelAnimation(player)) return;
        if (animator == null) {
            // 没有动作动画器时，跑「被动动画」（自由坠落姿态 ✓）—— 上游同此 ✓
            passiveAnimation.animate(player, parkourability, modelTransformer);
            return;
        }
        animator.animatePost(player, parkourability, modelTransformer);
    }

    public boolean rotatePre(AbstractClientPlayer player, PlayerModelRotator rotator) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return false;
        if (animator != null && animator.shouldRemoved(player, parkourability)) animator = null;
        if (animator == null) return false;
        if (shouldCancelAnimation(player) || option.isCanceled(AnimationPart.ROTATION)) return false;
        return animator.rotatePre(player, parkourability, rotator);
    }

    public void rotatePost(AbstractClientPlayer player, PlayerModelRotator rotator) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        if (shouldCancelAnimation(player) || option.isCanceled(AnimationPart.ROTATION)) return;
        if (animator == null) {
            // 上游此处调 passiveAnimation.rotate —— 它的实现是空的 ✓ 照搬保持结构一致 ✓
            passiveAnimation.rotate(player, parkourability, rotator);
            return;
        }
        animator.rotatePost(player, parkourability, rotator);
    }

    public void cameraSetup(EntityViewRenderEvent.CameraSetup event, EntityPlayerSP player, Parkourability parkourability) {
        if (animator == null) return;
        if (option.isCanceled(AnimationPart.CAMERA)) return;
        if (animator.shouldRemoved(player, parkourability)) {
            animator = null;
            return;
        }
        animator.onCameraSetUp(event, player, parkourability);
    }

    public void tick(AbstractClientPlayer player, Parkourability parkourability) {
        // ★ 被动动画的状态先更新（自由坠落的计时 ✓）—— 上游同此 ✓
        passiveAnimation.tick(player, parkourability);
        if (animator != null) {
            animator.tick(player);
        }
    }

    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        if (animator != null) {
            animator.onRenderTick(event, player, parkourability);
        }
        if (event.phase == TickEvent.Phase.START && player instanceof AbstractClientPlayer) {
            updateAnimationInfo((AbstractClientPlayer) player);
        }
    }

    /** 抛出 {@link ParCoolAnimationInfoEvent}，让第三方 mod 有机会取消某些部位的动画。 */
    public void updateAnimationInfo(AbstractClientPlayer player) {
        ParCoolAnimationInfoEvent animationEvent = new ParCoolAnimationInfoEvent(player, animator);
        MinecraftForge.EVENT_BUS.post(animationEvent);
        option = animationEvent.getOption();
    }

    public boolean shouldCancelAnimation(EntityPlayer player) {
        // 第一人称且未开启 FPV 动画时不做动画
        if (ParCool.PROXY.isLocalPlayer(player)
                && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0
                && !ParCoolConfig.Client.Booleans.EnableFPVAnimation.get()
        ) {
            return true;
        }
        return this.option.isAnimationCanceled();
    }

    public boolean hasAnimator() {
        return animator != null;
    }

    public void removeAnimator() {
        animator = null;
    }
}
