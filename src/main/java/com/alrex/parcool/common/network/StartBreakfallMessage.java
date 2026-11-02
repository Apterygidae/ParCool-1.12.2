package com.alrex.parcool.common.network;

import com.alrex.parcool.common.action.impl.BreakfallReady;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 「开始落地缓冲」通知（**服务端 → 单个客户端**）。
 *
 * <p>这是 {@code ID_BREAKFALL}(3) 对应的包 —— 也是当初推迟的那一个 ✓</p>
 *
 * <h3>方向为什么是 S→C</h3>
 * <p>判定「这次落地该不该缓冲」需要看<b>摔落距离</b>，那是服务端的权威数据；
 * 判定完再由服务端通知该玩家的客户端去播放动作 ✓
 * 上游的 {@code handleServer} 是空的（{@code @OnlyIn(DEDICATED_SERVER)}），
 * 因为它从不被客户端发上来 ✓ <b>所以 1.12.2 侧只注册客户端方向的 handler</b> ✓</p>
 *
 * <h3>触发链（完整）</h3>
 * <pre>
 * 服务端 LivingFallEvent（PlayerJumpHandler.onFall）
 *   → 判定 BreakfallReady.isDoing() + 落差超过阈值
 *   → StartBreakfallMessage.send(player, justTimed)
 *   → 客户端 Handler 收到 → BreakfallReady.startBreakfall(...)
 *   → Roll.startRoll(...) 或 Tap.startTap(...)
 * </pre>
 *
 * <h3>线格式</h3>
 * <pre>
 * [16 字节] playerUUID（两个 long）
 * [1  字节] justTimed（是否落在恰好时机窗口内）
 * </pre>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code ServerPlayerEntity} / {@code PlayerEntity}</td>
 *       <td>{@link EntityPlayerMP} / {@link EntityPlayer}</td></tr>
 *   <tr><td>{@code player.getUUID()}</td><td>{@code player.getUniqueID()}</td></tr>
 *   <tr><td>{@code PacketDistributor.PLAYER.with(() -> player)}</td>
 *       <td>{@code NetworkHandler.INSTANCE.sendTo(msg, playerMP)}</td></tr>
 *   <tr><td>{@code Minecraft.getInstance().player}</td>
 *       <td>{@code Minecraft.getMinecraft().player}</td></tr>
 *   <tr><td>{@code PacketDirection.CLIENTBOUND} 检查</td>
 *       <td><b>可以省掉</b> —— 1.12.2 的客户端 handler 只会收到 S→C 的包</td></tr>
 * </table>
 *
 * <p>⚠️ 无参构造器必须是 {@code public}（1.12.2 的 FML 用 {@code newInstance()} 造实例）。</p>
 */
public class StartBreakfallMessage implements IMessage {

    @Nullable
    private UUID playerID = null;
    private boolean justTimed = false;

    /** ⚠️ 必须 public —— 见类注释。 */
    public StartBreakfallMessage() {
    }

    public void writeTo(PacketBuffer packet) {
        packet.writeLong(playerID.getMostSignificantBits());
        packet.writeLong(playerID.getLeastSignificantBits());
        packet.writeBoolean(justTimed);
    }

    public void readFrom(PacketBuffer packet) {
        this.playerID = new UUID(packet.readLong(), packet.readLong());
        this.justTimed = packet.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeTo(new PacketBuffer(buf));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        readFrom(new PacketBuffer(buf));
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<StartBreakfallMessage, IMessage> {

        @Override
        public IMessage onMessage(StartBreakfallMessage message, MessageContext ctx) {
            // ★ 本方法运行在 Netty 线程 —— 必须把工作排到主线程（见 NetworkHandler.scheduleClient 的注释）
            final StartBreakfallMessage msg = message;
            NetworkHandler.scheduleClient(() -> {
                EntityPlayer player = Minecraft.getMinecraft().player;
                if (player == null || msg.playerID == null) return;
                // 只认自己的那份（服务端是发给特定玩家的，这里再确认一次）
                if (!msg.playerID.equals(player.getUniqueID())) return;

                Parkourability parkourability = Parkourability.get(player);
                if (parkourability == null) return;
                IStamina stamina = IStamina.get(player);
                if (stamina == null) return;

                parkourability.get(BreakfallReady.class)
                        .startBreakfall(player, parkourability, stamina, msg.justTimed);
            });
            return null;
        }
    }

    /** 服务端调用：把「该缓冲了」这件事通知那个玩家的客户端。 */
    public static void send(EntityPlayerMP player, boolean justTimed) {
        StartBreakfallMessage message = new StartBreakfallMessage();
        message.playerID = player.getUniqueID();
        message.justTimed = justTimed;
        NetworkHandler.INSTANCE.sendTo(message, player);
    }
}
