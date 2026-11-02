package com.alrex.parcool.client;

import com.alrex.parcool.common.network.NetworkCheckRequest;
import com.alrex.parcool.common.network.NetworkHandler;
import com.alrex.parcool.common.network.SyncClientInformationMessage;
import com.alrex.parcool.core.P0Verifier;
import com.alrex.parcool.core.StartupChecks;
import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * P0 验证用：进入世界后做一次终检。
 *
 * <p>为什么必须等到「进入世界」：{@code EntityPlayerMixin} 注入的是
 * {@code EntityPlayer#onLivingUpdate}，只有真正有玩家实例在跑 tick 时才会执行。
 * 因此「Mixin 是否生效」这一项在 postInit 阶段只能是 PENDING。</p>
 *
 * <p>这一段同时充当 Aqua API 的运行期冒烟测试：它依次调用
 * {@code getPose / getSize / getWidth / getHeight / canForceCrawling}，
 * 只要全部成功，就证明「发行版 obf jar 的 API 签名与 dev 环境 MCP 名一致」
 * 这个前提在实际运行中是成立的。</p>
 *
 * <p><b>P0 结束后可以删除本类</b>（连同 {@code ClientProxy} 里的注册语句）。</p>
 */
@SideOnly(Side.CLIENT)
public final class ClientInGameCheck {

    private static final Logger LOGGER = LogManager.getLogger("ParCool");
    private static boolean done = false;

    private ClientInGameCheck() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;

        done = true;
        LOGGER.info("===== [ParCool/P0] in-game verification =====");
        LOGGER.info(" 1. ParCool mixin injected  : {}",
                P0Verifier.hasTouched() ? "OK" : "FAILED  <<< 检查 refmap / Mixin 引导 / 注入点方法名");

        boolean aquaOk = player instanceof IPlayerResizeable;
        LOGGER.info(" 2. Aqua mixin usable       : {}",
                aquaOk ? "OK (player instanceof IPlayerResizeable)" : "FAILED  <<< Aqua 的 mixin 未生效");
        LOGGER.info(" 3. Aqua mixin (class-level): {}",
                StartupChecks.isAquaMixinApplied() ? "OK" : "FAILED");

        if (aquaOk) {
            try {
                IPlayerResizeable resizeable = (IPlayerResizeable) player;
                Pose pose = resizeable.getPose();
                EntitySize size = resizeable.getSize(pose);
                LOGGER.info(" 4. Aqua API smoke test     : OK");
                LOGGER.info("      getPose()            = {}", pose);
                LOGGER.info("      getSize(pose)        = {} (w={}, h={})", size, size.width, size.height);
                LOGGER.info("      getWidth()/getHeight() = {} / {}", resizeable.getWidth(), resizeable.getHeight());
                LOGGER.info("      canForceCrawling()   = {}", resizeable.canForceCrawling());
                LOGGER.info("      isSwimming()         = {}", resizeable.isSwimming());
            } catch (Throwable t) {
                LOGGER.error(" 4. Aqua API smoke test     : FAILED  <<< 签名不匹配，需改用 publishToMavenLocal 方案", t);
            }
        } else {
            LOGGER.error(" 4. Aqua API smoke test     : SKIPPED (接口未挂载)");
        }

        // ── P2-A：网络通道自检（P2-B 接通真实消息后删除） ──────────────────
        // 时机说明：能走到这里说明 mc.player 已存在，连接必然已是 PLAY 状态 ——
        // 这一点很重要，PLAY 之前发包会让 vanilla 的 NettyPacketEncoder 抛
        // "Can't serialize unregistered packet"。
        try {
            NetworkHandler.INSTANCE.sendToServer(new NetworkCheckRequest(System.currentTimeMillis()));
            LOGGER.info(" 5. Network check          : request sent (awaiting C->S / S->C round trip)");
        } catch (Throwable t) {
            LOGGER.error(" 5. Network check          : FAILED to send  <<< 检查 NetworkHandler.registerMessages() 是否在 init 调用", t);
        }

        // ── P2-B：上报客户端设置（顺带请求服务端下发限制） ─────────────────
        // 同样是「必须晚于 PLAY」的包。服务端收到后会回一份 ServerLimitation，
        // 收到之后 Parkourability.limitationIsNotSynced() 才会变 false。
        try {
            SyncClientInformationMessage.sync(player, true);
            LOGGER.info(" 6. Client info sync       : sent (requesting server limitation)");
        } catch (Throwable t) {
            LOGGER.error(" 6. Client info sync       : FAILED", t);
        }

        LOGGER.info("=============================================");
    }
}
