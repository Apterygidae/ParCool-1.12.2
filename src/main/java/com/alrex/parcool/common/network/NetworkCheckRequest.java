package com.alrex.parcool.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * P2-A 通道自检 · 请求（客户端 → 服务端）。
 *
 * <p>用途：在没有真实业务消息之前，用一次最小往返证明
 * 「通道建立 + 双向序列化 + 双端 handler 分流」这条链路是通的。
 * P2-B 接通真实消息后<b>可以删除本类与 {@link NetworkCheckResponse}</b>
 * （连同 {@code NetworkHandler} 里的两条注册和 {@code ClientInGameCheck} 里的发送语句）。</p>
 *
 * <h3>⚠️ 1.12.2 的 IMessage 签名（踩过一次）</h3>
 * <p>{@code IMessage} 的两个方法<b>参数都是 {@link ByteBuf}</b>，不是 {@code PacketBuffer}：</p>
 * <pre>
 * void fromBytes(ByteBuf buf);
 * void toBytes(ByteBuf buf);
 * </pre>
 * <p>因为 {@code PacketBuffer} 在 1.12.2 是 {@code extends ByteBuf} 的，
 * 写 {@code fromBytes(PacketBuffer)} <b>不构成覆写</b>，会报
 * 「未覆盖抽象方法 toBytes(io.netty.buffer.ByteBuf)」。</p>
 *
 * <p><b>本工程约定</b>：在方法体内包一层 {@code new PacketBuffer(buf)} 再读写 ——
 * 这样才能用上游那套 {@code readInt/readBoolean/readBytes/writeString} API，
 * P2-B 移植 6 个真实消息时 encode/decode 体几乎可以照搬。</p>
 */
public class NetworkCheckRequest implements IMessage {

    private static final Logger LOGGER = LogManager.getLogger("ParCool");

    private long clientTimestamp;

    /** 反序列化用 */
    public NetworkCheckRequest() {
    }

    public NetworkCheckRequest(long clientTimestamp) {
        this.clientTimestamp = clientTimestamp;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packet = new PacketBuffer(buf);
        this.clientTimestamp = packet.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packet = new PacketBuffer(buf);
        packet.writeLong(this.clientTimestamp);
    }

    public long getClientTimestamp() {
        return clientTimestamp;
    }

    /** 注册为 {@code Side.SERVER} —— 即只在服务端接收。 */
    public static class Handler implements IMessageHandler<NetworkCheckRequest, IMessage> {

        @Override
        public IMessage onMessage(NetworkCheckRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            LOGGER.info("[ParCool/P2] >> C->S OK: received check request from {} (client ts = {})",
                    player == null ? "<null>" : player.getName(), message.getClientTimestamp());
            if (player == null) return null;
            // 立刻回一发，验证 S->C 方向
            return new NetworkCheckResponse(message.getClientTimestamp(), System.currentTimeMillis());
        }
    }
}
