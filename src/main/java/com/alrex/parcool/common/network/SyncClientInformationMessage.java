package com.alrex.parcool.common.network;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ClientSetting;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * 客户端设置上报（客户端 → 服务端，随后广播）。
 *
 * <p>对应上游 {@code SyncClientInformationMessage}。它把客户端的
 * 「动作开关 + 体力消耗 + 各项设置」打包发给服务端，服务端存进该玩家的
 * {@code Parkourability.getActionInfo().setClientSetting(...)}，然后广播给所有人
 * （这样其他玩家渲染你时也能用你的设置）。</p>
 *
 * <p>{@code requestLimitations} 标志用于「顺便让服务端回一份限制」——
 * 上游走 {@code Limitations.update(player)}；本工程该子系统属 P6，
 * 暂时改为直接用本机服务端配置构造一份 {@code ServerLimitation} 并回发。</p>
 *
 * <h3>1.12.2 的写法差异</h3>
 * <ul>
 *   <li>{@code packet.writeBytes(data)} / {@code readByte()} 循环 →
 *       <b>{@code PacketBuffer.writeByteArray(byte[])} / {@code readByteArray()}</b>
 *       （1.12.2 的 Forge 扩展，自带长度前缀，比「读到包尾」更稳）</li>
 *   <li>{@code player.getGameProfile().getName()} → {@code player.getName()}</li>
 *   <li>{@code PacketDistributor.SERVER.noArg()} → {@code NetworkHandler.INSTANCE.sendToServer(msg)}</li>
 *   <li>上游把「单机时自己也是服务端」的两种情况写在同一个方法里；1.12.2 拆成两个 Handler 类</li>
 * </ul>
 */
public class SyncClientInformationMessage implements IMessage {

    private final ByteBuffer data = ByteBuffer.allocate(512);
    @Nullable
    private UUID playerID = null;
    private boolean requestLimitations = false;

    public SyncClientInformationMessage() {
    }

    public void writeTo(PacketBuffer packet) {
        packet.writeLong(playerID.getMostSignificantBits());
        packet.writeLong(playerID.getLeastSignificantBits());
        packet.writeBoolean(requestLimitations);

        byte[] payload = new byte[data.remaining()];
        data.get(payload);
        packet.writeByteArray(payload);
        data.rewind();
    }

    public void readFrom(PacketBuffer packet) {
        this.playerID = new UUID(packet.readLong(), packet.readLong());
        this.requestLimitations = packet.readBoolean();
        byte[] payload = packet.readByteArray();
        this.data.clear();
        if (payload != null) {
            this.data.put(payload);
        }
        this.data.flip();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeTo(new PacketBuffer(buf));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        readFrom(new PacketBuffer(buf));
    }

    // ── 服务端接收 ──────────────────────────────────────────────────────────

    public static class HandlerServer implements IMessageHandler<SyncClientInformationMessage, IMessage> {

        @Override
        public IMessage onMessage(SyncClientInformationMessage message, MessageContext ctx) {
            // ★ 本方法运行在 Netty 线程 —— 必须把工作排到主线程
            //   （见 NetworkHandler.scheduleServer 的注释）
            //   这一处尤其关键：它写的是**服务端的** ClientSetting，
            //   而服务端的 ActionInfo.can() 会读它（如落地缓冲的判定）
            final SyncClientInformationMessage msg = message;
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;

            NetworkHandler.scheduleServer(() -> {
                NetworkHandler.INSTANCE.sendToAll(msg);

                Parkourability parkourability = Parkourability.get(player);
                if (parkourability == null) return;

                if (msg.requestLimitations) {
                    // P6：与上游一致 —— 走 Limitations.update（它会用
                    // 「全局限制 + 该玩家名下的限制」重算 ServerLimitation，并下发）
                    com.alrex.parcool.server.limitation.Limitations.update(player);
                }
                logReceived(player);
                parkourability.getActionInfo().setClientSetting(ClientSetting.readFrom(msg.data));
                msg.data.rewind();
            });
            return null;
        }
    }

    // ── 其他客户端接收（渲染别人的动作时要用别人的设置） ────────────────────

    @SideOnly(Side.CLIENT)
    public static class HandlerClient implements IMessageHandler<SyncClientInformationMessage, IMessage> {

        @Override
        public IMessage onMessage(SyncClientInformationMessage message, MessageContext ctx) {
            // ★ 同上：Netty 线程 → 排到客户端主线程
            final SyncClientInformationMessage msg = message;
            NetworkHandler.scheduleClient(() -> {
                World world = Minecraft.getMinecraft().world;
                if (world == null || msg.playerID == null) return;
                EntityPlayer player = world.getPlayerEntityByUUID(msg.playerID);
                if (player == null || ParCool.PROXY.isLocalPlayer(player)) return;

                Parkourability parkourability = Parkourability.get(player);
                if (parkourability == null) return;
                logReceived(player);
                parkourability.getActionInfo().setClientSetting(ClientSetting.readFrom(msg.data));
                msg.data.rewind();
            });
            return null;
        }
    }

    // ── 发送入口 ────────────────────────────────────────────────────────────

    /**
     * 上报自己的客户端设置。
     *
     * @param requestSendLimitation 是否顺便要服务端回一份限制（用于限制同步请求）
     */
    @SideOnly(Side.CLIENT)
    public static void sync(EntityPlayer player, boolean requestSendLimitation) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        // ★★ 这里必须用「从本地配置现读一份」，而不是玩家当前的 ClientSetting 实例。
        //
        // 原因：本方法在「进世界后的首个 tick」（ClientInGameCheck）就会被调用，
        // 而那时玩家的 ClientSetting 可能还是 `UNSYNCED_INSTANCE` ——
        // 它的 `getPossibilityOf(...)` **恒返回 false**（Default 实现，用于「尚未同步时一律禁止」）。
        // 于是 `writeTo` 会把「**所有动作都禁用**」发给服务端 ✗
        // → 服务端那份 ClientSetting 全为 false → 服务端侧 `ActionInfo.can()` 恒 false ✗
        //
        // 实测症状：落地缓冲在**服务端**判定 `can(Roll)/can(Tap)` 为 false
        // → 不发 `StartBreakfallMessage` → Roll/Tap 永远不触发。
        // （客户端侧没事，因为 ActionProcessor 里会自愈本地实例 → 所以其它动作正常 ✓）
        //
        // 由本地配置现读，既拿到权威内容，也**与实例初始化时机解耦** ✓
        ClientSetting authoritative = ClientSetting.readFromLocalConfig();
        parkourability.getActionInfo().setClientSetting(authoritative);   // 顺带把本端也自愈了

        SyncClientInformationMessage message = new SyncClientInformationMessage();
        authoritative.writeTo(message.data);
        message.data.flip();
        message.playerID = player.getUniqueID();
        message.requestLimitations = requestSendLimitation;

        NetworkHandler.INSTANCE.sendToServer(message);
        logSent(player);
    }

    // ── 日志 ────────────────────────────────────────────────────────────────

    private static void logReceived(EntityPlayer player) {
        ParCool.LOGGER.log(Level.INFO, "Received Client Information of [" + player.getName() + "]");
    }

    private static void logSent(EntityPlayer player) {
        ParCool.LOGGER.log(Level.INFO, "Sent Client Information of [" + player.getName() + "]");
    }
}
