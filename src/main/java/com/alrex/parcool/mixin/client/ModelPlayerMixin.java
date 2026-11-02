package com.alrex.parcool.mixin.client;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.capability.Animation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ParCool 动画的主钩子（1.12.2 版）—— 对应上游 {@code mixin/client/PlayerModelMixin}。
 *
 * <h3>★ 为什么打在 {@code ModelPlayer} 而不是 {@code ModelBiped}</h3>
 * <p>Aqua Acrobatics 有 {@code @Mixin(ModelBiped.class)}（读它的 {@code bipedXxx} 字段做泳姿）。
 * 本工程注入<b>子类</b> {@code ModelPlayer}，而 1.12.2 的
 * {@code ModelPlayer#setRotationAngles} 是<b>自己声明</b>并调用 {@code super} 的
 * （{@code ModelPlayer.java:127/:129}，已核实）—— 所以：</p>
 * <ul>
 *   <li>两个 mixin <b>不在同一个方法里</b>，天然分层隔离</li>
 *   <li>执行顺序天然正确：Aqua 在 {@code super} 里先改，ParCool 在子类里后改（ParCool 是后手）</li>
 * </ul>
 *
 * <h3>1.12.2 与 1.16.5 的签名差异</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerModel#setupAnim(T, float ×5)}</td>
 *       <td>{@code ModelPlayer#setRotationAngles(float ×6, Entity)} —— <b>多一个末尾的 {@code Entity}</b></td></tr>
 *   <tr><td>{@code @Shadow private boolean slim}</td>
 *       <td>{@code @Shadow private boolean smallArms}（1.12.2 的字段名）</td></tr>
 * </table>
 *
 * <p>上游还 {@code @Shadow} 了 {@code jacket}/{@code rightPants}/{@code rightSleeve}/{@code leftPants}/
 * {@code leftSleeve}/{@code ear} 六个字段，但它们的可见代码里<b>并未使用</b>（应该是历史遗留）。
 * 1.12.2 版本不影子这些字段 —— 少一处与 MC 内部字段名绑定的地方。</p>
 *
 * <p><b>只由 {@code mixins.parcool.json} 的 {@code client} 数组加载</b>，
 * 所以专用服务端不会尝试应用它（否则会碰到被 {@code @SideOnly(CLIENT)} 剥掉的客户端类）。</p>
 */
@Mixin(ModelPlayer.class)
public abstract class ModelPlayerMixin extends ModelBiped {

    @Shadow
    @Final
    private boolean smallArms;

    @Unique
    private PlayerModelTransformer parCool$transformer = null;

    /** Mixin 需要一个与目标类匹配的构造器（{@code ModelPlayer} 调的是 {@code super(modelSize, 0, 64, 64)}）。 */
    public ModelPlayerMixin(float modelSize, boolean smallArmsIn) {
        super(modelSize, 0.0F, 64, 64);
    }

    @Inject(method = "setRotationAngles", at = @At("HEAD"), cancellable = true, require = 0)
    protected void parCool$onSetRotationAnglesHead(
            float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            float scaleFactor, Entity entityIn, CallbackInfo info
    ) {
        if (!(entityIn instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entityIn;
        ModelPlayer model = (ModelPlayer) (Object) this;

        parCool$transformer = new PlayerModelTransformer(
                player,
                model,
                smallArms,
                ageInTicks,
                limbSwing,
                limbSwingAmount,
                netHeadYaw,
                headPitch
        );
        // 每帧从「无动画」的初始姿态开始，避免上一帧的改动累积
        parCool$transformer.reset();

        // ── ★ 1.12.2 第一人称：跳过 ParCool 的动画 ────────────────────────────
        // 1.12.2 的第一人称**不渲染身体模型** —— `setRotationAngles` 在这里只被
        // `RenderPlayer#renderRightArm / renderLeftArm` 调用，而那两处只渲染 `bipedRightArm`
        // / `bipedLeftArm`。此时套上 ParCool 的动画 = 把「手」当整个身体来摆
        // → 手模型会被转到镜头前（实测：FastRun 时第一人称的手发生偏移/放大）。
        //
        // 上游用 `ParCoolConfig.Client.Booleans.EnableFPVAnimation` 控制这件事，但它默认是
        // **true**；而 1.16 的第一人称同样只画手臂 —— 所以在 1.12.2 这个开关唯一的实际效果
        // 就是「让手乱动」。这里直接跳过，`reset()` 仍然保留以便把模型复位。
        //
        // 注意判据是「本地玩家 + thirdPersonView == 0」：非本地玩家永远是第三人称视角下的
        // 别人，`thirdPersonView` 不影响他们，所以不受影响 ✓
        if (parCool$isFirstPersonArmRender(player)) {
            parCool$transformer = null;
            return;
        }
        // ─────────────────────────────────────────────────────────────────────

        Animation animation = Animation.get(player);
        if (animation == null) return;

        boolean shouldCancel = animation.animatePre(player, parCool$transformer);
        parCool$transformer.copyFromBodyToWear();
        if (shouldCancel) {
            // 动画器要求「取消 vanilla 动画，我全权控制」
            parCool$transformer = null;
            info.cancel();
        }
    }

    /**
     * 当前这次 {@code setRotationAngles} 是不是「第一人称手臂渲染」。
     *
     * <p>见 HEAD 注入点里的注释 —— 1.12.2 第一人称只画手臂，所以这种情况必须跳过动画。</p>
     */
    @Unique
    private static boolean parCool$isFirstPersonArmRender(EntityPlayer player) {
        try {
            return ParCool.PROXY.isLocalPlayer(player)
                    && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // ── 已回退的尝试（2026-09-30，记录下来避免重走）────────────────────────
    // 曾在这里加过「爬行时把 Aqua 的 swimAnimation 压成 0」来压制它的泳姿四肢动画：
    //     if (parkourability.get(Crawl.class).isDoing()) {
    //         ((IModelBipedSwimming) model).setSwimAnimation(0.0F);
    //     }
    // 机制上说得通（时序：setLivingAnimations 与 Aqua 改四肢都在本注入点之后），
    // 但**实际效果不佳，已按用户要求回退**。
    // 目前的状态：Aqua 的泳姿动画与 ParCool 的 CrawlAnimator 仍会叠加，
    // 待找到更合适的做法（可能需要从「不让 Aqua 认为玩家在游泳」这个方向入手，
    // 但那与 Crawl 依赖 pose=SWIMMING 的机制相冲突，需要重新设计）。
    // ─────────────────────────────────────────────────────────────────────

    @Inject(method = "setRotationAngles", at = @At("TAIL"), require = 0)
    protected void parCool$onSetRotationAnglesTail(
            float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
            float scaleFactor, Entity entityIn, CallbackInfo info
    ) {
        if (!(entityIn instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entityIn;

        Animation animation = Animation.get(player);
        if (animation == null) {
            parCool$transformer = null;
            return;
        }

        if (parCool$transformer != null) {
            animation.animatePost(player, parCool$transformer);
            parCool$transformer.copyFromBodyToWear();
            parCool$transformer = null;
        }
    }
}
