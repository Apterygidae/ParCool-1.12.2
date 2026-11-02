package com.alrex.parcool.common.network;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.unstable.action.ParCoolActionEvent;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.capability.Parkourability;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * 动作状态同步（客户端 ↔ 服务端，随后广播）。
 *
 * <p>对应上游 {@code SyncActionStateMessage} —— 六个包里最大的一个。
 * 它是动作系统的动脉：把「某个动作开始了 / 结束了 / 状态变了」编码成一个包发出去。</p>
 *
 * <h3>线格式（与上游一致，逐字节可对照）</h3>
 * <pre>
 * [16 字节] senderUUID（两个 long）
 * [4  字节] 载荷长度
 * [N  字节] 载荷：一串条目，每条 =
 *             short  actionID
 *             byte   类型（0=Normal 1=Start 2=Finish）
 *             int    该条目的 buffer 长度
 *             bytes  条目内容
 * </pre>
 * 类型为 {@code Finish} 时长度写 0（没有内容）✓
 *
 * <h3>★ 为什么无参构造器必须是 {@code public}</h3>
 * <p>上游那个构造器是 {@code private} —— 1.16 的 {@code SimpleChannel} 用<b>注册的解码函数</b>造实例，
 * 不靠反射。而 1.12.2 的 FML 走 {@code FMLIndexedMessageToMessageCodec.decode} →
 * {@code msgClazz.newInstance()}，<b>需要 public 无参构造器</b>。
 * 这条是 1.12.2 网络消息的硬约束之一（见 {@code NetworkHandler} 的注释）。</p>
 *
 * <h3>其余 1.12.2 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code @OnlyIn(DEDICATED_SERVER)} / {@code @OnlyIn(CLIENT)} 两个方法</td>
 *       <td><b>两个嵌套 Handler 类</b>，注册时用 {@code Side} 分流</td></tr>
 *   <tr><td>{@code PacketDistributor.ALL.noArg()} / {@code SERVER.noArg()}</td>
 *       <td>{@code NetworkHandler.INSTANCE.sendToAll(msg)} / {@code sendToServer(msg)}</td></tr>
 *   <tr><td>{@code player.getUUID()}</td><td>{@code player.getUniqueID()}</td></tr>
 *   <tr><td>{@code world.getPlayerByUUID(...)}</td><td>{@code world.getPlayerEntityByUUID(...)}</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td><td>{@code ParCool.PROXY.isLocalPlayer(player)}</td></tr>
 * </table>
 */
public class SyncActionStateMessage implements IMessage {

    /** ⚠️ 必须 public —— 见类注释（1.12.2 的 FML 用 {@code newInstance()} 造实例）。 */
    public SyncActionStateMessage() {
    }

    @Nullable
    private UUID senderUUID = null;
    @Nullable
    private byte[] buffer = null;

    public void writeTo(PacketBuffer packetBuffer) {
        packetBuffer.writeLong(senderUUID.getMostSignificantBits());
        packetBuffer.writeLong(senderUUID.getLeastSignificantBits());
        packetBuffer.writeInt(buffer.length);
        packetBuffer.writeBytes(buffer);
    }

    public void readFrom(PacketBuffer packetBuffer) {
        this.senderUUID = new UUID(packetBuffer.readLong(), packetBuffer.readLong());
        int size = packetBuffer.readInt();
        this.buffer = new byte[size];
        packetBuffer.readBytes(this.buffer);
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

    public static class HandlerServer implements IMessageHandler<SyncActionStateMessage, IMessage> {

        @Override
        public IMessage onMessage(SyncActionStateMessage message, MessageContext ctx) {
            // ★ 本方法运行在 Netty 线程 —— 排到服务端主线程（见 NetworkHandler 的注释）
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null || message.buffer == null) return null;

            NetworkHandler.scheduleServer(() -> {
                // 先广播给所有人（含发送者自己）—— 这样「自己」也会走一遍客户端接收路径，
                // 让本地与服务端的动作状态保持一致
                NetworkHandler.INSTANCE.sendToAll(message);

                Parkourability parkourability = Parkourability.get(player);
                if (parkourability == null) return;

                applyTo(player, parkourability, message.buffer);
            });
            return null;
        }
    }

    // ── 客户端接收 ──────────────────────────────────────────────────────────

    @SideOnly(Side.CLIENT)
    public static class HandlerClient implements IMessageHandler<SyncActionStateMessage, IMessage> {

        @Override
        public IMessage onMessage(SyncActionStateMessage message, MessageContext ctx) {
            // ★ 同上：Netty 线程 → 排到客户端主线程
            NetworkHandler.scheduleClient(() -> {
                World world = Minecraft.getMinecraft().world;
                if (world == null || message.senderUUID == null || message.buffer == null) return;
                EntityPlayer player = world.getPlayerEntityByUUID(message.senderUUID);
                if (player == null || ParCool.PROXY.isLocalPlayer(player)) return;

                Parkourability parkourability = Parkourability.get(player);
                if (parkourability == null) return;

                applyTo(player, parkourability, message.buffer);
            });
            return null;
        }
    }

    /** 服务端与客户端共用同一套「按条目应用」逻辑（上游也是两处重复的同一段代码）。 */
    private static void applyTo(EntityPlayer player, Parkourability parkourability, byte[] buffer) {
        Decoder decoder = new Decoder(buffer, parkourability);
        while (decoder.hasNext()) {
            ActionSyncData item = decoder.getItem();
            if (item == null) continue;
            Action action = item.getAction();
            switch (item.getType()) {
                case Start: {
                    ByteBuffer startInfo = item.getBuffer();
                    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Start.Pre(player, action));
                    action.start(player, parkourability, startInfo, null);
                    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.StartEvent(player, action));
                    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Start.Post(player, action));
                    break;
                }
                case Finish:
                    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Finish.Pre(player, action));
                    action.finish(player);
                    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.StopEvent(player, action));
                    MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Finish.Post(player, action));
                    break;
                case Normal:
                    action.restoreSynchronizedState(item.getBuffer());
                    break;
                default:
                    break;
            }
        }
    }

    // ── 发送入口（客户端） ──────────────────────────────────────────────────

    @SideOnly(Side.CLIENT)
    public static void sync(EntityPlayer player, Encoder builder) {
        ByteBuffer built = builder.build();
        if (built.limit() == 0) return;

        SyncActionStateMessage message = new SyncActionStateMessage();
        message.senderUUID = player.getUniqueID();
        message.buffer = new byte[built.limit()];
        built.get(message.buffer);

        NetworkHandler.INSTANCE.sendToServer(message);
    }

    /** 把本 tick 的动作变化攒进一个包（上游是个单例，{@code ActionProcessor} 每 tick {@code reset()}）。 */
    public static class Encoder {
        private static final Encoder INSTANCE = new Encoder();

        private Encoder() {
        }

        private final ByteBuffer buffer = ByteBuffer.allocate(1024);
        private boolean empty = true;

        public boolean isEmpty() {
            return empty;
        }

        public static Encoder reset() {
            INSTANCE.empty = true;
            INSTANCE.buffer.clear();
            return INSTANCE;
        }

        public Encoder appendSyncData(Parkourability parkourability, Action action, ByteBuffer actionBuffer) {
            empty = false;
            return append(DataType.Normal, parkourability, action, actionBuffer);
        }

        public Encoder appendStartData(Parkourability parkourability, Action action, ByteBuffer actionBuffer) {
            empty = false;
            return append(DataType.Start, parkourability, action, actionBuffer);
        }

        public Encoder appendFinishMsg(Parkourability parkourability, Action action) {
            empty = false;
            short id = parkourability.getActionID(action);
            if (id < 0) return this;
            buffer.putShort(id).put(DataType.Finish.getCode()).putInt(0);
            return this;
        }

        private Encoder append(DataType type, Parkourability parkourability, Action action, ByteBuffer actionBuffer) {
            empty = false;
            short id = parkourability.getActionID(action);
            if (id < 0) return this;
            buffer.putShort(id).put(type.getCode()).putInt(actionBuffer.limit()).put(actionBuffer);
            return this;
        }

        public ByteBuffer build() {
            buffer.flip();
            return buffer;
        }
    }

    private static class Decoder {
        final ByteBuffer buffer;
        final Parkourability parkourability;

        Decoder(byte[] buf, Parkourability parkourability) {
            buffer = ByteBuffer.wrap(buf).asReadOnlyBuffer();
            this.parkourability = parkourability;
        }

        public boolean hasNext() {
            return buffer.position() < buffer.limit();
        }

        @Nullable
        public ActionSyncData getItem() {
            Action action = parkourability.getActionFromID(buffer.getShort());
            DataType type = DataType.getFromCode(buffer.get());
            int bufferSize = buffer.getInt();
            if (bufferSize > 1024) {
                StringBuilder msgBuilder = new StringBuilder();
                msgBuilder.append("Synchronization failed. demanded buffer size is too large\n")
                        .append(action)
                        .append(":Sync_Type")
                        .append(type)
                        .append('\n')
                        .append(buffer);
                if (buffer.limit() < 128) {
                    buffer.rewind();
                    msgBuilder.append("->{");
                    while (buffer.hasRemaining()) {
                        msgBuilder.append(Integer.toHexString(buffer.get())).append(',');
                    }
                    msgBuilder.append('}');
                }
                ParCool.LOGGER.warn(msgBuilder.toString());
                buffer.position(buffer.limit());
                return null;
            }
            ByteBuffer buf = buffer.slice();
            buf.limit(bufferSize);
            // 跳过这段切片占用的区域
            buffer.position(buffer.position() + bufferSize);

            if (action == null) {
                return null;
            }
            return new ActionSyncData(action, buf, type);
        }
    }

    private enum DataType {
        Normal, Start, Finish;

        public byte getCode() {
            return (byte) this.ordinal();
        }

        public static DataType getFromCode(byte code) {
            DataType[] values = DataType.values();
            if (code < 0 || code >= values.length) return Normal;
            return values[code];
        }
    }

    private static class ActionSyncData {
        final Action action;
        final ByteBuffer buffer;
        final DataType type;

        ActionSyncData(Action action, ByteBuffer buffer, DataType type) {
            this.action = action;
            this.buffer = buffer;
            this.type = type;
        }

        public DataType getType() {
            return type;
        }

        public Action getAction() {
            return action;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }
    }
}
