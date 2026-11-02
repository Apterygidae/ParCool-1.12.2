package com.alrex.parcool.client.hud;

import com.alrex.parcool.client.hud.impl.StaminaHUDController;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 体力 HUD 的**事件入口** ✓ —— 由 {@code ClientProxy.preInit} 注册到 Forge 事件总线 ✓。
 *
 * <h3>⚠️ 1.12.2 的事件包路径（与 1.16 全不同 ✗）</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code net.minecraftforge.eventbus.api.SubscribeEvent}</td>
 *       <td><b>{@code net.minecraftforge.fml.common.eventhandler.SubscribeEvent}</b> ✗</td></tr>
 *   <tr><td>{@code net.minecraftforge.event.TickEvent}</td>
 *       <td><b>{@code net.minecraftforge.fml.common.gameevent.TickEvent}</b> ✗</td></tr>
 *   <tr><td>{@code RenderGameOverlayEvent}</td>
 *       <td>{@code net.minecraftforge.client.event.RenderGameOverlayEvent} ✓（同名同包 ✓）</td></tr>
 * </table>
 *
 * <p>⚠️ 本类只在客户端加载 ✓（由 {@code @SideOnly(CLIENT)} 的 {@code ClientProxy} 注册 ✓）。</p>
 */
@SideOnly(Side.CLIENT)
public class HUDRegistry {

    private static HUDRegistry instance = null;

    public static HUDRegistry getInstance() {
        if (instance == null) instance = new HUDRegistry();
        return instance;
    }

    private final StaminaHUDController staminaHUD = new StaminaHUDController();

    /**
     * ⚠️ 只监听 {@code Post} ✓ 且内部再判一次 {@code EXPERIENCE} ✗ ——
     * 那是「饥饿条之后」的时机 ✓，HUD 的垂直堆叠基准才有效 ✓。
     */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.EXPERIENCE) return;
        staminaHUD.render(event);
    }

    /** ⚠️ 只在 {@code END} 阶段跑 ✓（{@code START} 太早，玩家状态还没更新 ✓）。 */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;
        staminaHUD.onTick(event);
    }
}
