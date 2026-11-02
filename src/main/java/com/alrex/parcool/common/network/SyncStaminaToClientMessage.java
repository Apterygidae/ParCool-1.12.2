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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 体力下发（服务端 → 所有客户端，批量）。
 *
 * <p>对应上游 {@code SyncStaminaToClientMessage}。设计要点：<b>不是每次都发</b> ——
 * 客户端先登记「想同步谁」，服务端每 10 tick 合并成<b>一个包</b>发出去，
 * 包里用 UUID 索引多份状态。这样多人同屏时不会因为每个玩家的体力各发一包而爆流量。</p>
 *
 * <p><b>线程约束</b>（上游注释原文）：{@code requestSync} 与 {@code tick} 只能在服务端主线程调用，
 * 内部那个 {@code TreeMap} 不是线程安全的。1.12.2 里这条同样成立 —— {@code tick} 挂在
 * {@link TickEvent.ServerTickEvent} 上，天然在服务端主线程 ✓。</p>
 *
 * <h3>1.12.2 的写法差异</h3>
 * <ul>
 *   <li>{@code PacketDistributor.ALL.noArg()} → {@code NetworkHandler.INSTANCE.sendToAll(msg)}</li>
 *   <li>{@code player.getUUID()} → {@code player.getUniqueID()}</li>
 *   <li>{@code Minecraft.getInstance().level} → {@code Minecraft.getMinecraft().world}</li>
 * </ul>
 */
public class SyncStaminaToClientMessage implements IMessage {

    // ── 请求登记（只由服务端主线程调用） ────────────────────────────────────

    public static void requestSync(EntityPlayerMP player) {
        IStamina stamina = IStamina.get(player);
        if (!(stamina instanceof OtherStamina)) return;
        requestSync(player, (OtherStamina) stamina);
    }

    public static void requestSync(EntityPlayerMP player, OtherStamina stamina) {
        requestSync(player.getUniqueID(), stamina);
    }

    public static void requestSync(UUID playerID, OtherStamina stamina) {
        SynchronizeRequestRegistry.getInstance().add(playerID, SynchronizedState.create(stamina));
    }

    private static int syncCooldownTick = 0;

    /** 每 10 tick 把攒下的请求合成一个包发出去。 */
    public static void tick() {
        if (syncCooldownTick > 0) {
            syncCooldownTick--;
        } else if (SynchronizeRequestRegistry.getInstance().isNotEmpty()) {
            syncCooldownTick = 10;
            SyncStaminaToClientMessage message = new SyncStaminaToClientMessage(
                    SynchronizeRequestRegistry.getInstance().getAndClearRequestedStates());
            NetworkHandler.INSTANCE.sendToAll(message);
        }
    }

    /** 由 {@code EventBusForgeRegistry} 注册。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick();
    }

    private static class SynchronizeRequestRegistry {
        @Nullable
        private static SynchronizeRequestRegistry instance = null;

        public static SynchronizeRequestRegistry getInstance() {
            if (instance == null) instance = new SynchronizeRequestRegistry();
            return instance;
        }

        @Nonnull
        private TreeMap<UUID, SynchronizedState> idAndStateMap = new TreeMap<>();

        public void add(UUID playerID, SynchronizedState state) {
            idAndStateMap.put(playerID, state);
        }

        public boolean isNotEmpty() {
            return !idAndStateMap.isEmpty();
        }

        public TreeMap<UUID, SynchronizedState> getAndClearRequestedStates() {
            TreeMap<UUID, SynchronizedState> map = idAndStateMap;
            idAndStateMap = new TreeMap<>();
            return map;
        }
    }

    private static class SynchronizedState {
        private final int staminaValue;
        private final int maxValue;
        private final boolean exhausted;
        private final boolean imposingPenalty;

        SynchronizedState(int staminaValue, int maxValue, boolean exhausted, boolean imposingPenalty) {
            this.staminaValue = staminaValue;
            this.maxValue = maxValue;
            this.exhausted = exhausted;
            this.imposingPenalty = imposingPenalty;
        }

        public void encode(PacketBuffer buffer) {
            buffer.writeInt(this.staminaValue);
            buffer.writeInt(this.maxValue);
            buffer.writeBoolean(this.exhausted);
            buffer.writeBoolean(this.imposingPenalty);
        }

        public static SynchronizedState load(PacketBuffer buffer) {
            return new SynchronizedState(buffer.readInt(), buffer.readInt(), buffer.readBoolean(), buffer.readBoolean());
        }

        public static SynchronizedState create(OtherStamina stamina) {
            return new SynchronizedState(stamina.get(), stamina.getActualMaxStamina(),
                    stamina.isExhausted(), stamina.isImposingExhaustionPenalty());
        }

        public void apply(OtherStamina stamina) {
            stamina.setMax(this.maxValue);
            stamina.set(this.staminaValue);
            stamina.setImposingPenalty(this.imposingPenalty);
            stamina.setExhaustion(this.exhausted);
        }
    }

    // ── 消息本体 ────────────────────────────────────────────────────────────

    private final Map<UUID, SynchronizedState> idAndStateMap;

    public SyncStaminaToClientMessage() {
        this.idAndStateMap = new TreeMap<>();
    }

    private SyncStaminaToClientMessage(Map<UUID, SynchronizedState> stateMap) {
        this.idAndStateMap = stateMap;
    }

    public void writeTo(PacketBuffer packet) {
        for (Map.Entry<UUID, SynchronizedState> entry : idAndStateMap.entrySet()) {
            packet.writeLong(entry.getKey().getMostSignificantBits());
            packet.writeLong(entry.getKey().getLeastSignificantBits());
            entry.getValue().encode(packet);
        }
    }

    public void readFrom(PacketBuffer packet) {
        // 本类的实例由 readFrom 填充时 map 是只读的（构造器创建的空 TreeMap 也能 put）
        while (packet.isReadable()) {
            this.idAndStateMap.put(
                    new UUID(packet.readLong(), packet.readLong()),
                    SynchronizedState.load(packet)
            );
        }
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
    public static class Handler implements IMessageHandler<SyncStaminaToClientMessage, IMessage> {

        @Override
        public IMessage onMessage(SyncStaminaToClientMessage message, MessageContext ctx) {
            // ★ 本方法运行在 Netty 线程 —— 排到客户端主线程（见 NetworkHandler 的注释）
            NetworkHandler.scheduleClient(() -> {
                World world = Minecraft.getMinecraft().world;
                if (world == null) return;
                for (Map.Entry<UUID, SynchronizedState> entry : message.idAndStateMap.entrySet()) {
                    EntityPlayer player = world.getPlayerEntityByUUID(entry.getKey());
                    if (player == null) continue;
                    if (ParCool.PROXY.isLocalPlayer(player)) continue;

                    IStamina stamina = IStamina.get(player);
                    if (!(stamina instanceof OtherStamina)) continue;
                    entry.getValue().apply((OtherStamina) stamina);
                }
            });
            return null;
        }
    }

    /** 便于自检打印。 */
    public boolean isEmpty() {
        return idAndStateMap.isEmpty();
    }
}
