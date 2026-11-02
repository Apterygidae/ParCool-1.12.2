package com.alrex.parcool.mixin.client;

import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.platform.ParCoolPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * 整个玩家模型朝向的钩子（1.12.2 版）—— 对应上游 {@code mixin/client/PlayerRendererMixin}。
 *
 * <h3>★ 与 Aqua 在同一个注入点 —— 所以这里必须让位</h3>
 * <p>Aqua 的 {@code RenderPlayerMixin} <b>也</b>注入 {@code RenderPlayer#applyRotations} 的 {@code TAIL}
 * （它在那里做泳姿的 {@code GlStateManager} 旋转与下移）。两边都改会把模型转两次。
 * 因此本 mixin 的 HEAD/TAIL 都<b>先判 {@code isActuallySwimming} 并让位</b>：</p>
 *
 * <pre>
 * if (ParCoolPlatform.isActuallySwimming(player)) return;   // 交给 Aqua
 * </pre>
 *
 * <p>这条是移植方案里已定的协调策略（见主文档的 Aqua 冲突矩阵：{@code RenderPlayer#applyRotations}
 * 属「同点共存」——Aqua 追加 GlStateManager 旋转，ParCool 加让位判断）。</p>
 *
 * <h3>1.12.2 与 1.16.5 的签名差异</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code setupRotations(T, MatrixStack, float, float, float)}</td>
 *       <td>{@code applyRotations(T, float ageInTicks, float rotationYaw, float partialTicks)}
 *           —— <b>没有 MatrixStack</b>（1.12.2 是立即模式，见 {@code PlayerModelRotator} 的类注释）</td></tr>
 *   <tr><td>{@code PlayerRenderer}</td><td>{@code RenderPlayer}</td></tr>
 *   <tr><td>{@code AbstractClientPlayerEntity}</td><td>{@code AbstractClientPlayer}</td></tr>
 * </table>
 *
 * <p>上游把 {@code (ageInTicks, rotationYaw, partialTicks)} 依次传成 {@code (xRot, yRot, zRot)}
 * （它自己注释也写了「arg names may be incorrect」）—— 这里保持同样的传法，不擅自"修正"。</p>
 *
 * <h3>没有注入 {@code doRender}</h3>
 * <p>{@code Animation.onRenderTick} 由 {@code ActionProcessor} 的事件钩子驱动，不需要改渲染循环。
 * 顺带一提 {@code RenderPlayer#doRender} 是 Aqua <b>没有</b>占用的点，将来若需要可以安全注入。</p>
 */
@Mixin(RenderPlayer.class)
public abstract class RenderPlayerMixin extends RenderLivingBase<AbstractClientPlayer> {

    /** Mixin 需要与目标类匹配的构造器（{@code RenderPlayer} 调的是 {@code super(rm, model, 0.5F)}）。 */
    public RenderPlayerMixin(RenderManager renderManagerIn, ModelBase modelBaseIn, float shadowSizeIn) {
        super(renderManagerIn, modelBaseIn, shadowSizeIn);
    }

    @Unique
    private PlayerModelRotator parCool$rotator = null;

    /**
     * 让位判断：这个玩家的模型朝向是否该交给 Aqua 处理。
     *
     * <h3>⚠️ 为什么<b>不能</b>用 {@code isActuallySwimming()}</h3>
     * <p>最初这里写的是 {@code ParCoolPlatform.isActuallySwimming(player)}，本意是
     * 「Aqua 在游泳时会接管模型，我们不插手」。但 Aqua 的实现是：</p>
     * <pre>
     * // aquaacrobatics EntityPlayerMixin.java:480
     * public boolean isActuallySwimming() {
     *     return this.getPose() == Pose.SWIMMING || ...;      // ← 只看姿态
     * }
     * </pre>
     * <p><b>而 ParCool 的 {@code Crawl} 正是靠把姿态设成 {@code SWIMMING} 来实现爬行的</b>
     * （见 {@code CrawlAnimator} / {@code ParCoolPlatform#setForcingCrawling}）。
     * 于是爬行时本判断误触发 → 我们在 {@code applyRotations} 让位 → Aqua 做它的
     * <b>整模型游泳旋转</b>，而 ParCool 的 {@code CrawlAnimator} 同时在改四肢
     * → <b>两套动画叠在一起</b>（实测：用户看到「ParCool 爬行 + Aqua 游泳」的混合姿态）。</p>
     *
     * <p>改为看「<b>玩家是否真的在水里</b>」—— 那才是 Aqua 的游泳旋转要处理的场景。
     * 爬行时的姿态虽然是 SWIMMING，但人不在水里，不该让位。</p>
     */
    @Unique
    private static boolean parCool$shouldYieldToAqua(AbstractClientPlayer player) {
        try {
            return ParCoolPlatform.isReady(player) && ParCoolPlatform.isInWaterOrBubble(player);
        } catch (Throwable t) {
            // 让位判断本身不该把渲染搞崩 —— 出问题就当作「不让位」
            return false;
        }
    }

    @Inject(method = "applyRotations", at = @At("HEAD"), cancellable = true, require = 0)
    protected void parCool$onApplyRotationsHead(
            AbstractClientPlayer entityLiving, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci
    ) {
        // ── ★ 补偿 1.12.2 原版对「潜行玩家」的 -0.125 位移 ────────────────────
        // 见 RenderPlayer.java:66-71：
        //     double d0 = y;
        //     if (entity.isSneaking()) { d0 = y - 0.125D; }   // 整个模型下移 0.125
        // 那是 **1.12.2 原版行为**（因为 1.12.2 的潜行只是模型前倾 + 模型空间里下移腿/头，
        // 见 ModelBiped.java:239-249，需要在 world 空间再补一点），
        // **与 Aqua、ParCool 都无关**。1.16/1.20 没有这个 hack（姿态系统接管了）——
        // 所以上游的动画看起来不会偏低，而我们的会。
        //
        // 时序上这里能补：RenderLivingBase:130-132 先 renderLivingAt（施加那个 -0.125）、
        // **再** applyRotations，所以在本注入点 +0.125 就能抵消 ✓
        //
        // ⚠️ 只在「ParCool 的动画正在接管」时补偿 —— 否则会改变原版所有潜行玩家的外观。
        //    代价：动画结束的瞬间模型会回落 0.125（很小，且那是原版的正常外观）。
        Animation sneakingAnimation = parCool$getActiveAnimation(entityLiving);
        if (sneakingAnimation != null) {
            GlStateManager.translate(0, 0.125f, 0);
        }
        // ─────────────────────────────────────────────────────────────────────

        if (parCool$shouldYieldToAqua(entityLiving)) return;

        Animation animation = Animation.get(entityLiving);
        if (animation == null) return;

        parCool$rotator = new PlayerModelRotator(
                entityLiving,
                Minecraft.getMinecraft().getRenderPartialTicks(),
                ageInTicks,
                rotationYaw,
                partialTicks
        );
        // ★★ 修复「周围生物光照变怪」：**只复位法线，绝不动矩阵** ✗→✓
        //
        // 🚨 实测 bug（2026-10-04）：「闪避和快跑时，周围生物的光照变得奇怪」✗
        //
        // 根因（1.12.2 立即模式）：
        //   `GlStateManager.rotate` → `glRotatef` → **同时旋转「当前法线向量」** ✗
        //   而光照（lightmap）正是按法线算的 ✗
        //   且 **`glPopMatrix` 只恢复矩阵、不恢复法线** ✗
        //   → 动画器旋转过的法线**泄漏到之后渲染的每一个生物** ✗
        //   （1.16 用 `MatrixStack` 无此问题 ✓）
        //
        // 🚨🚨 但第一版修法是错的（已回退 ✗）：
        //   我用 `pushMatrix`/`popMatrix` 包住了整段 ——
        //   **动画器在 `rotatePre` 里施加的位移/旋转必须【一直保留到模型渲染】✗**
        //   （`applyRotations` 在 `RenderLivingBase:130` 调用，模型在之后才渲染 ✓）
        //   → pop 在渲染前就把变换撤掉了 ✗
        //   → 实测症状：「玩家身体只会朝向一个位置，其他动作动画也出问题」✗
        //
        // ✅ 正确做法：**矩阵保持不动（变换要留给模型）✓，只把法线复位** ✓
        if (animation.rotatePre(entityLiving, parCool$rotator)) {
            parCool$rotator = null;
            parCool$restoreNormal();
            ci.cancel();
            return;
        }
    }

    /**
     * 复位法线向量 ✓（见 HEAD 注入点的长注释）。
     *
     * <p>{@code (0, 1, 0)} 是「竖直向上」—— 实体渲染所期望的基准法线 ✓</p>
     * <p><b>⚠️ 只复位法线，不要动矩阵</b> ✗ —— 矩阵里的变换是动画要留给模型用的 ✓</p>
     */
    @Unique
    private static void parCool$restoreNormal() {
        try {
            GlStateManager.glNormal3f(0f, 1f, 0f);
        } catch (Throwable ignored) {
            // 复位失败不该把渲染搞崩
        }
    }

    /**
     * 若该玩家「正在潜行」且「ParCool 有动画器在跑」，返回那个 capability；否则 null。
     *
     * <p>见 HEAD 注入点里的注释 —— 用于决定要不要补偿原版的潜行位移。</p>
     */
    @Unique
    @Nullable
    private static Animation parCool$getActiveAnimation(AbstractClientPlayer player) {
        try {
            if (!player.isSneaking()) return null;
            Animation animation = Animation.get(player);
            return (animation != null && animation.hasAnimator()) ? animation : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Inject(method = "applyRotations", at = @At("TAIL"), require = 0)
    protected void parCool$onApplyRotationsTail(
            AbstractClientPlayer entityLiving, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci
    ) {
        if (parCool$shouldYieldToAqua(entityLiving)) return;

        Animation animation = Animation.get(entityLiving);
        if (animation == null) return;

        if (parCool$rotator != null) {
            animation.rotatePost(entityLiving, parCool$rotator);
            parCool$rotator = null;
        }
        // ★ 只复位法线 —— **不要碰矩阵** ✗
        //   （模型在 applyRotations 之后才渲染 ✓，动画的变换必须留着 ✓）
        parCool$restoreNormal();
    }
}
