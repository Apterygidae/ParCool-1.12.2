package com.alrex.parcool.common.handlers;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.server.limitation.Limitations;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 玩家进服时的初始化同步（服务端侧）。
 *
 * <h3>⚠️ 时机是这个类存在的唯一理由</h3>
 * <p>发包**绝不能早于连接进入 PLAY 状态**，否则 vanilla 的 {@code NettyPacketEncoder}
 * 会抛 {@code Can't serialize unregistered packet}
 * （该错误是「发送方按 方向+当前连接状态 精确类名查表失败」，与接收方无关 —— 详见 P2 文档）。</p>
 *
 * <p>{@link PlayerEvent.PlayerLoggedInEvent} 的时机是安全的：它由
 * {@code PlayerList.initializeConnectionToPlayer} 在玩家实体加入世界之后触发，
 * 那一刻连接已经是 PLAY。**不要**改用 {@code ServerConnectionFromClientEvent} 之类的早期钩子。</p>
 *
 * <h3>本类只做服务端</h3>
 * <p>客户端侧的上报（{@code SyncClientInformationMessage}）放在
 * {@code ClientInGameCheck} 里 —— 原因见 {@code EventBusForgeRegistry} 的类注释：
 * 公共类里不出现客户端专属引用。</p>
 *
 * <h3>P6 已接线：不再有 TODO</h3>
 * <p>原本这里是 {@code ServerLimitation.fromLocalServerConfig(player)} 的占位实现
 * （只读服务端配置，缺「管理员按玩家施加的限制」那一层）。现在改走
 * {@link Limitations#update(EntityPlayerMP)}，与上游
 * {@code LoginLogoutHandler.onLogin → Limitations.update} 的职责合并 ——
 * 上游那个 {@code onLogin} 其实是空的注释块，真正做事的就是这里这条链路。</p>
 */
public class PlayerJoinHandler {

    private PlayerJoinHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;   // 只处理服务端侧
        EntityPlayerMP player = (EntityPlayerMP) event.player;

        if (Parkourability.get(player) == null) {
            ParCool.LOGGER.warn("[ParCool] Parkourability capability missing on join for {}", player.getName());
            return;
        }

        // ① 重算该玩家最终生效的限制（全局限制 + 该玩家名下的各条限制）
        // ② 把限制（+ 体力权威值）一起下发；客户端收到后 limitationIsNotSynced()
        //    才会变 false，动作也才可能被允许执行
        Limitations.update(player);
    }
}
