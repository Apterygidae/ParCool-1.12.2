package com.alrex.parcool.common.handlers;

import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.server.limitation.Limitations;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 玩家登出时卸载并落盘该玩家的限制（上游 {@code LoginLogoutHandler}）。
 *
 * <p>上游的 {@code onLogin} 是一个空的注释块 —— 登录侧的逻辑实际在
 * {@code PlayerJoinHandler → Limitations.update} 里，所以这里只实现登出。</p>
 *
 * <p>⚠️ 1.12.2 的 {@link PlayerEvent.PlayerLoggedOutEvent} <b>只在服务端触发</b>
 * （客户端连不上时的本地清理不发这个事件），所以这里的 {@code instanceof} 判断
 * 其实恒为 true —— 保留它只是为了与上游同构，别当成"必须的判断"。</p>
 */
public class LoginLogoutHandler {

    private LoginLogoutHandler() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EntityPlayer player = event.player;
        if (Parkourability.get(player) == null) return;
        if (player instanceof EntityPlayerMP) {
            Limitations.unload(player.getUniqueID());
        }
    }
}
