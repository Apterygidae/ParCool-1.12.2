package com.alrex.parcool.common.network;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ServerLimitation;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import java.nio.ByteBuffer;

/**
 * 服务端限制下发（服务端 → 单个玩家）。
 *
 * <p>对应上游 {@code SyncServerInfoMessage}。内容是「服务端限制快照」+ 可选的体力权威值。
 * 客户端收到后写进 {@code Parkourability.getActionInfo().setServerLimitation(...)}，
 * 从此 {@code limitationIsNotSynced()} 变 false，动作才会被允许执行。</p>
 *
 * <h3>1.12.2 的写法差异</h3>
 * <ul>
 *   <li>{@code PacketDistributor.PLAYER.with(() -> player)} → {@code NetworkHandler.INSTANCE.sendTo(msg, player)}</li>
 *   <li>{@code packet.writeBytes(data)} → {@code writeByteArray(byte[])}（见 {@code SyncClientInformationMessage} 的说明）</li>
 *   <li>{@code Minecraft.getInstance().player} → {@code Minecraft.getMinecraft().player}</li>
 * </ul>
 */
public class SyncServerInfoMessage implements IMessage {

    private final ByteBuffer limitationData = ByteBuffer.allocate(512);
    private int staminaValue;
    private boolean staminaExhausted;
    private boolean staminaNeedSync;

    public SyncServerInfoMessage() {
    }

    public void writeTo(PacketBuffer packet) {
        packet.writeBoolean(staminaNeedSync);
        packet.writeBoolean(staminaExhausted);
        packet.writeInt(staminaValue);

        byte[] payload = new byte[limitationData.remaining()];
        limitationData.get(payload);
        packet.writeByteArray(payload);
        limitationData.rewind();
    }

    public void readFrom(PacketBuffer packet) {
        this.staminaNeedSync = packet.readBoolean();
        this.staminaExhausted = packet.readBoolean();
        this.staminaValue = packet.readInt();
        byte[] payload = packet.readByteArray();
        this.limitationData.clear();
        if (payload != null) {
            this.limitationData.put(payload);
        }
        this.limitationData.flip();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeTo(new PacketBuffer(buf));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        readFrom(new PacketBuffer(buf));
    }

    // ── 客户端接收 ──────────────────────────────────────────────────────────

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<SyncServerInfoMessage, IMessage> {

        @Override
        public IMessage onMessage(SyncServerInfoMessage message, MessageContext ctx) {
            // ★ 本方法运行在 Netty 线程 —— 排到客户端主线程（见 NetworkHandler 的注释）
            NetworkHandler.scheduleClient(() -> {
                EntityPlayerSP player = Minecraft.getMinecraft().player;
                if (player == null) return;
                Parkourability parkourability = Parkourability.get(player);
                if (parkourability == null) return;

                logReceived(player);
                parkourability.getActionInfo().setServerLimitation(ServerLimitation.readFrom(message.limitationData));

                if (message.staminaNeedSync) {
                    IStamina stamina = IStamina.get(player);
                    if (stamina == null) return;
                    stamina.set(message.staminaValue);
                    stamina.setExhaustion(message.staminaExhausted);
                }
            });
            return null;
        }
    }

    // ── 发送入口（服务端） ──────────────────────────────────────────────────

    /** 只发限制，不动体力。 */
    public static void sync(EntityPlayerMP player) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        SyncServerInfoMessage msg = new SyncServerInfoMessage();
        parkourability.getActionInfo().getServerLimitation().writeTo(msg.limitationData);
        msg.limitationData.flip();
        msg.staminaNeedSync = false;
        NetworkHandler.INSTANCE.sendTo(msg, player);
    }

    /** 连体力权威值一起发（玩家刚进服 / 重生时用）。 */
    public static void syncWithStamina(EntityPlayerMP player, IStamina stamina) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        SyncServerInfoMessage msg = new SyncServerInfoMessage();
        parkourability.getActionInfo().getServerLimitation().writeTo(msg.limitationData);
        msg.limitationData.flip();
        msg.staminaNeedSync = true;
        msg.staminaExhausted = stamina.isExhausted();
        msg.staminaValue = stamina.get();
        NetworkHandler.INSTANCE.sendTo(msg, player);
        logSent(player);
    }

    // ── 日志 ────────────────────────────────────────────────────────────────

    private static void logReceived(EntityPlayer player) {
        ParCool.LOGGER.log(Level.INFO, "Received Server Limitation of [" + player.getName() + "]");
    }

    private static void logSent(EntityPlayer player) {
        ParCool.LOGGER.log(Level.INFO, "Sent Server Limitation of [" + player.getName() + "]");
    }
}
