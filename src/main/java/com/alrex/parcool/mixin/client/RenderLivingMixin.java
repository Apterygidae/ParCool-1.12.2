package com.alrex.parcool.mixin.client;

import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「**隐藏玩家名牌**」—— 供 {@code HideInBlock} 藏进方块时用 ✓
 *
 * <p>（文件名里的 {@code RenderLiving} 是历史遗留 ✗ —— 实际目标是 {@code RenderLivingBase} ✓）</p>
 *
 * <h3>★ 为什么需要它</h3>
 * <p>上游 {@code LivingRendererMixin} 注入 {@code shouldShowName(LivingEntity)}，
 * 靠 {@code BehaviorEnforcer.cancelShowingName()} 关掉名牌 ✓
 * <b>这是 {@code BehaviorEnforcer} 那套「标记」的<b>应用层</b>之一</b> ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code LivingRenderer#shouldShowName(LivingEntity)}</td>
 *       <td>{@code RenderLivingBase#canRenderName(EntityLivingBase)}（名字不同，职责一样 ✓）</td></tr>
 *   <tr><td>{@code LivingEntity}</td><td>{@code EntityLivingBase}</td></tr>
 * </table>
 *
 * <h3>🚨🚨🚨 这里踩过一个大坑，务必读完（2026-10-03 ~ 10-04）</h3>
 * <p><b>症状</b>：藏进方块后，其他玩家依然看得见名字 ✗</p>
 * <p><b>当时的误判</b>：以为「注入点选错了层」✗ ——
 * 因为 1.12.2 里确实有<b>两个</b> {@code canRenderName}
 * （{@code RenderLivingBase:506} 与覆写它的 {@code RenderLiving:24}），
 * 于是把目标从 {@code RenderLivingBase} 改成了 {@code RenderLiving} ✗</p>
 * <p><b>真实原因</b>：<b>改完源码后 jar 从来没有重新构建过</b> ✗ ——
 * 用户测的始终是旧 jar ✓（见 2026-10-03 记忆里的「stale jar」教训 ✓）</p>
 *
 * <h3>★★ 为什么 {@code RenderLivingBase} 才是对的（已从反编译源码核实）</h3>
 * <pre>
 * RenderPlayer   extends RenderLivingBase&lt;AbstractClientPlayer&gt;    ← 玩家走【RenderLivingBase】✓
 * RenderLiving&lt;T extends EntityLiving&gt; extends RenderLivingBase&lt;T&gt;  ← 只给 mob ✗
 * EntityPlayer   extends EntityLivingBase                           ← 不是 EntityLiving ✗
 * </pre>
 * <p><b>⭐ 判据不是「谁覆写了它」，而是「目标实体的渲染器继承自谁」</b> ✓
 * 改到 {@code RenderLiving} 上不仅无效，还会因为 {@code EntityLiving} 与 {@code EntityPlayer}
 * 是<b>兄弟类型</b>而<b>直接编译不过</b> ✗
 * （编译器报「{@code EntityLiving} 无法转换为 {@code EntityPlayer}」✗ —— 这条报错反过来印证了继承链 ✓）</p>
 *
 * <p><b>⚠️ 本 mixin 只对玩家生效</b> —— 非玩家实体直接放行 ✓
 * （{@code BehaviorEnforcer} 挂在玩家能力上 ✓ 别的实体没有 ✗）</p>
 */
@Mixin(RenderLivingBase.class)
public abstract class RenderLivingMixin {

    @Inject(method = "canRenderName", at = @At("HEAD"), cancellable = true, require = 0)
    protected void parCool$onCanRenderName(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof EntityPlayer)) return;
        Parkourability parkourability = Parkourability.get((EntityPlayer) entity);
        if (parkourability == null) return;
        if (parkourability.getBehaviorEnforcer().cancelShowingName()) {
            cir.setReturnValue(false);
        }
    }
}
