package com.alrex.parcool.common.network;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.stamina.OtherStamina;
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

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 体力上报（客户端 → 服务端，随后由服务端广播给所有人）。
 *
 * <p>对应上游 {@code SyncStaminaMessage}。三个作用合一：</p>
 * <ol>
 *   <li>本地玩家的体力权威值交给服务端（服务端那份是 {@code OtherStamina}）</li>
 *   <li>服务端把它广播出去，让<b>其他客户端</b>能画出该玩家的体力</li>
 *   <li>携带「需要在服务端扣除的量」（{@code HungerStamina} 用饥饿值时）</li>
 * </ol>
 *
 * <h3>1.12.2 的写法差异</h3>
 * <ul>
 *   <li>上游用 {@code @OnlyIn(DEDICATED_SERVER)} / {@code @OnlyIn(CLIENT)} 两个方法；
 *       1.12.2 改为 <b>两个嵌套 Handler 类</b>，注册时用 {@code Side} 参数分流</li>
 *   <li><b>双向注册使用同一个 discriminator</b>。{@code FMLIndexedMessageToMessageCodec} 内部是
 *       {@code Byte2ObjectMap<disc 到 class>} + {@code Object2ByteMap<class 到 disc>}，后者是单值映射；
 *       同一个类用两个编号注册会互相覆盖 —— 用同一个编号才是正解</li>
 *   <li>{@code PacketDistributor.ALL.noArg()} → {@code NetworkHandler.INSTANCE.sendToAll(msg)}</li>
 *   <li>{@code player.getUUID()} → {@code player.getUniqueID()}；
 *       {@code player.isLocalPlayer()} → {@code ParCool.PROXY.isLocalPlayer(player)}</li>
 * </ul>
 */
public class SyncStaminaMessage implements IMessage {

    private int stamina = 0;
    private int max = 0;
    private boolean exhausted = false;
    private boolean imposingPenalty = false;
    private int consumeOnServer = 0;
    /** 客户端当前用的是哪种体力实现（{@code IStamina.Type.ordinal()}），-1 表示未知。 */
    private int staminaType = -1;
    @Nullable
    private UUID playerID = null;

    public SyncStaminaMessage() {
    }

    public void writeTo(PacketBuffer packet) {
        packet.writeInt(this.stamina);
        packet.writeInt(this.max);
        packet.writeBoolean(this.exhausted);
        packet.writeBoolean(this.imposingPenalty);
        packet.writeInt(this.staminaType);
        packet.writeInt(this.consumeOnServer);
        packet.writeLong(this.playerID.getMostSignificantBits());
        packet.writeLong(this.playerID.getLeastSignificantBits());
    }

    public void readFrom(PacketBuffer packet) {
        this.stamina = packet.readInt();
        this.max = packet.readInt();
        this.exhausted = packet.readBoolean();
        this.imposingPenalty = packet.readBoolean();
        this.staminaType = packet.readInt();
        this.consumeOnServer = packet.readInt();
        this.playerID = new UUID(packet.readLong(), packet.readLong());
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

    public static class HandlerServer implements IMessageHandler<SyncStaminaMessage, IMessage> {

        @Override
        public IMessage onMessage(SyncStaminaMessage message, MessageContext ctx) {
            // ★ 本方法运行在 Netty 线程 —— 排到服务端主线程（见 NetworkHandler 的注释）
            final EntityPlayerMP player = ctx.getServerHandler().player;

            NetworkHandler.scheduleServer(() -> {
                // 广播给所有人（含发送者自己）—— 其他客户端据此绘制该玩家的体力
                NetworkHandler.INSTANCE.sendToAll(message);
                if (player == null) return;

                IStamina stamina = IStamina.get(player);
                if (!(stamina instanceof OtherStamina)) return;
                OtherStamina otherStamina = (OtherStamina) stamina;
                otherStamina.setMax(message.max);
                otherStamina.set(message.stamina);
                otherStamina.setImposingPenalty(message.imposingPenalty);
                otherStamina.setExhaustion(message.exhausted);

                if (message.staminaType != -1 && message.consumeOnServer > 0) {
                    IStamina.Type.values()[message.staminaType].handleConsumeOnServer(player, message.consumeOnServer);
                }

                SyncStaminaToClientMessage.requestSync(player);
            });
            return null;
        }
    }

    // ── 客户端接收（单机集成服务端时，自己也会收到广播回来的包） ────────────

    @SideOnly(Side.CLIENT)
    public static class HandlerClient implements IMessageHandler<SyncStaminaMessage, IMessage> {

        @Override
        public IMessage onMessage(SyncStaminaMessage message, MessageContext ctx) {
            // ★ 同上：Netty 线程 → 排到客户端主线程
            NetworkHandler.scheduleClient(() -> {
                World world = Minecraft.getMinecraft().world;
                if (world == null || message.playerID == null) return;
                EntityPlayer player = world.getPlayerEntityByUUID(message.playerID);
                if (player == null || ParCool.PROXY.isLocalPlayer(player)) return;

                IStamina stamina = IStamina.get(player);
                if (!(stamina instanceof OtherStamina)) return;
                OtherStamina otherStamina = (OtherStamina) stamina;
                otherStamina.setMax(message.max);
                otherStamina.set(message.stamina);
                otherStamina.setImposingPenalty(message.imposingPenalty);
                otherStamina.setExhaustion(message.exhausted);
            });
            return null;
        }
    }

    // ── 发送入口 ────────────────────────────────────────────────────────────

    /** 本地客户端上报自己的体力。由 {@code ActionProcessor} 每 tick 调用。 */
    @SideOnly(Side.CLIENT)
    public static void sync(EntityPlayer player) {
        IStamina stamina = IStamina.get(player);
        if (stamina == null || !ParCool.PROXY.isLocalPlayer(player)) return;

        SyncStaminaMessage message = new SyncStaminaMessage();
        message.stamina = stamina.get();
        message.max = stamina.getActualMaxStamina();
        message.exhausted = stamina.isExhausted();
        message.imposingPenalty = stamina.isImposingExhaustionPenalty();
        message.consumeOnServer = stamina.getRequestedValueConsumedOnServer();
        IStamina.Type type = IStamina.Type.getFromInstance(stamina);
        message.staminaType = type != null ? type.ordinal() : -1;
        message.playerID = player.getUniqueID();

        NetworkHandler.INSTANCE.sendToServer(message);
    }
}
