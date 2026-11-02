package com.alrex.parcool.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * P2-A 通道自检 · 响应（服务端 → 客户端）。
 *
 * <p>与 {@link NetworkCheckRequest} 成对，用来验证 S→C 方向。
 * 两者是<b>两个独立的类</b>而不是同一个类的两个 discriminator ——
 * {@code SimpleNetworkWrapper} 内部按 {@code Map<Class, discriminator>} 分配编号，
 * 同一个类注册两次会互相覆盖，导致其中一个方向静默失效。</p>
 *
 * <p>签名注意：{@code IMessage} 的两个方法参数都是 {@link ByteBuf}（不是 {@code PacketBuffer}），
 * 详见 {@link NetworkCheckRequest} 的类注释。</p>
 */
public class NetworkCheckResponse implements IMessage {

    private static final Logger LOGGER = LogManager.getLogger("ParCool");

    private long clientTimestamp;
    private long serverTimestamp;

    /** 反序列化用 */
    public NetworkCheckResponse() {
    }

    public NetworkCheckResponse(long clientTimestamp, long serverTimestamp) {
        this.clientTimestamp = clientTimestamp;
        this.serverTimestamp = serverTimestamp;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packet = new PacketBuffer(buf);
        this.clientTimestamp = packet.readLong();
        this.serverTimestamp = packet.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packet = new PacketBuffer(buf);
        packet.writeLong(this.clientTimestamp);
        packet.writeLong(this.serverTimestamp);
    }

    /** 注册为 {@code Side.CLIENT} —— 即只在客户端接收。 */
    public static class Handler implements IMessageHandler<NetworkCheckResponse, IMessage> {

        @Override
        public IMessage onMessage(NetworkCheckResponse message, MessageContext ctx) {
            LOGGER.info("[ParCool/P2] >> S->C OK: received check response (client ts = {}, server ts = {}, RTT-ish = {} ms)",
                    message.clientTimestamp, message.serverTimestamp,
                    message.serverTimestamp - message.clientTimestamp);
            LOGGER.info("[ParCool/P2] >>> BIDIRECTIONAL CHANNEL VERIFIED <<<");
            return null;
        }
    }
}
