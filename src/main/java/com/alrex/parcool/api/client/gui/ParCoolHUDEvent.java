package com.alrex.parcool.api.client.gui;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * ParCool HUD 的**对外事件** ✓ —— 让其它 mod / 整合包能拦截体力 HUD 的绘制 ✓。
 *
 * <p>1:1 移植自上游（1.16.5）。<b>唯一的差异：1.12.2 没有 {@code MatrixStack}</b> ✗ ——
 * 上游的 {@code getMatrixStack()} 已删除 ✓（1.12.2 是立即模式，无矩阵对象可传 ✓）。</p>
 *
 * <p>⚠️ 本类只被客户端代码引用 ✓（{@code StaminaHUDController} 在 {@code @SideOnly(CLIENT)} 下 ✓），
 * 因此**不加** {@code @SideOnly} ✗ —— 与上游一致 ✓。</p>
 *
 * <h3>用法（其它 mod）</h3>
 * <pre>
 * &#64;SubscribeEvent
 * public void onParCoolHUD(ParCoolHUDEvent.RenderEvent e) {
 *     if (我的条件) e.setCanceled(true);   // 取消 → ParCool 不画体力条
 * }
 * </pre>
 */
public class ParCoolHUDEvent extends Event {

    /** 体力 HUD **即将绘制前**触发 ✓；`setCanceled(true)` 可阻止 ParCool 自己绘制 ✓。 */
    @Cancelable
    public static class RenderEvent extends ParCoolHUDEvent {

        private final RenderGameOverlayEvent internalEvent;

        public RenderEvent(RenderGameOverlayEvent e) {
            this.internalEvent = e;
        }

        /** 底层的 Forge 事件 ✓（可取 {@code getResolution()} / {@code getPartialTicks()} ✓）。 */
        public RenderGameOverlayEvent getInternalInformation() {
            return internalEvent;
        }
    }
}
