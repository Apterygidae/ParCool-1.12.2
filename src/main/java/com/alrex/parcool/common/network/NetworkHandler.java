package com.alrex.parcool.common.network;

import com.alrex.parcool.ParCool;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * ParCool 网络通道（1.12.2 版）。
 *
 * <p>对应 1.16.5 的 {@code ParCool.CHANNEL_INSTANCE}
 * （{@code NetworkRegistry.newSimpleChannel(...)} 得到 {@code SimpleChannel}，然后
 * {@code registerMessage(id, class, encoder, decoder, handler)}）。</p>
 *
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code SimpleChannel}</td><td>{@link SimpleNetworkWrapper}</td></tr>
 *   <tr><td>编码/解码/处理三件套作为方法引用一次性注册</td>
 *       <td>拆成「消息类 {@code IMessage}」+「处理类 {@code IMessageHandler}」两个类</td></tr>
 *   <tr><td>{@code registerMessage(id, clazz, enc, dec, handler)}</td>
 *       <td>{@code registerMessage(HandlerClass, MessageClass, discriminator, Side)}</td></tr>
 *   <tr><td>{@code @OnlyIn(DEDICATED_SERVER)} / {@code @OnlyIn(CLIENT)} 双 handler 写法</td>
 *       <td><b>用 {@code Side} 参数分流</b>，同一个消息可注册两个不同方向的 handler</td></tr>
 *   <tr><td>{@code PacketDistributor.ALL.noArg()}</td><td>{@code INSTANCE.sendToAll(...)}</td></tr>
 * </table>
 *
 * <p><b>频道名</b>：FML 1.12.2 的频道名有长度限制（会拼进 vanilla 的 custom payload 通道名），
 * 所以用短名 {@code parcool}（7 字符）。</p>
 *
 * <p><b>协议版本校验</b>：1.16.5 用 {@code newSimpleChannel} 的第三个参数做握手校验；
 * 1.12.2 改用最简的 {@code newSimpleChannel(String)}，版本兼容由
 * {@code @Mod(acceptableRemoteVersions = "*")} 放行，实际校验交给 ParCool 自己的
 * {@code SyncClientInformationMessage} / {@code ServerLimitation} 机制 —— 这一点与上游设计一致。</p>
 */
public final class NetworkHandler {

    /** 频道名。1.12.2 下必须短（FML 会把它塞进 vanilla custom payload 的通道名，有长度限制）。 */
    public static final String CHANNEL_NAME = "parcool";

    /**
     * 与上游 1.16.5 完全一致的 discriminator 分配表。
     *
     * <p>保留上游的编号（而不是从 0 连续分配）是为了让两边的代码可以直接对照 ——
     * 移植期间「这个包在对面是几号」这个问题不该占用精力。</p>
     *
     * <p>表里已有的项是<b>预留</b>：对应的消息类要在 P2-B 才接入（依赖见 {@link #registerMessages()}）。</p>
     */
    public static final int ID_BREAKFALL = 3;          // S -> C  StartBreakfallMessage（服务端通知某个玩家「该缓冲了」）
    public static final int ID_STAMINA = 10;           // C -> S  SyncStaminaMessage
    public static final int ID_SERVER_INFO = 12;       // S -> C  SyncServerInfoMessage
    public static final int ID_ACTION_STATE = 15;      // 双向    SyncActionStateMessage
    public static final int ID_CLIENT_INFO = 17;       // C -> S  SyncClientInformationMessage
    public static final int ID_STAMINA_TO_CLIENT = 18; // S -> C  SyncStaminaToClientMessage

    /** P2-A 自检用（不在上游编号表里，P2-B 接通真实包后删除）。 */
    public static final int ID_CHECK_REQUEST = 1;      // C -> S
    public static final int ID_CHECK_RESPONSE = 2;     // S -> C

    public static SimpleNetworkWrapper INSTANCE;

    private NetworkHandler() {
    }

    /**
     * 建立通道并注册<b>双端通用与「客户端 → 服务端」方向</b>的消息。
     * 必须在 {@code FMLInitializationEvent} 期间调用（双端都要，且早于任何发包）。
     *
     * <p><b>「服务端 → 客户端」方向的消息不在这里注册</b>，而在 {@code ClientProxy}。
     * 原因见 {@code EventBusForgeRegistry} 的类注释：那些 handler 是
     * {@code @SideOnly(Side.CLIENT)} 的，把它写进本类会让专用服务端多一层
     * 「依赖 FML 运行期剥离」的风险；走 {@code @SidedProxy} 是硬保证。</p>
     */
    public static void registerMessages() {
        if (INSTANCE != null) return;

        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);

        // ── 客户端 → 服务端 ────────────────────────────────────────────────
        // 注意：同一消息类「双向」时两个方向要用<b>同一个 discriminator</b>。
        // FMLIndexedMessageToMessageCodec 内部是
        //   Byte2ObjectMap<disc → class>  +  Object2ByteMap<class → disc>
        // 后者是单值映射 —— 同一个类用两个编号注册会互相覆盖。
        INSTANCE.registerMessage(SyncStaminaMessage.HandlerServer.class, SyncStaminaMessage.class, ID_STAMINA, Side.SERVER);
        INSTANCE.registerMessage(SyncClientInformationMessage.HandlerServer.class, SyncClientInformationMessage.class, ID_CLIENT_INFO, Side.SERVER);
        INSTANCE.registerMessage(SyncActionStateMessage.HandlerServer.class, SyncActionStateMessage.class, ID_ACTION_STATE, Side.SERVER);
        // P2-A 通道自检
        INSTANCE.registerMessage(NetworkCheckRequest.Handler.class, NetworkCheckRequest.class, ID_CHECK_REQUEST, Side.SERVER);

        // ── 仍未接入 ───────────────────────────────────────────────────────
        //   ID_BREAKFALL  -> 依赖 StartBreakfallMessage（需要一个「破蹲」动作，P4-B 末尾）
        //   只需追加一行 registerMessage 即可，机制已就位。
        //
        // ⚠️ 注册消息的时机与「发包的时机」是两件事：
        //    注册必须在 init 完成；但**发包绝不能在 PLAY 之前**，
        //    否则 vanilla 的 NettyPacketEncoder 会抛
        //    "Can't serialize unregistered packet"。

        ParCool.LOGGER.info("[ParCool] Network channel '{}' established (server-bound: {} messages)",
                CHANNEL_NAME, 4);
    }

    // ────────────────────────────────────────────────────────────────────────
    // ★★ 1.12.2 的网络 handler 运行在 **Netty 线程**，不是主线程
    //
    // 1.16 的 SimpleChannel 由 `context.enqueueWork(...)` 自动把工作切回主线程；
    // **1.12.2 的 SimpleNetworkWrapper 没有这层保护** —— `IMessageHandler#onMessage`
    // 直接在 Netty 线程上被调用。
    //
    // → 任何**触碰游戏状态**（能力、实体字段、动画、粒子、音效）的 handler
    //   都必须用下面两个方法把工作排到主线程，否则就是数据竞争：
    //   轻则偶发失效（本次实测：落地缓冲的 `startRequired` 写读跨线程 → Roll 不触发），
    //   重则 ConcurrentModificationException 或状态损坏。
    //
    // 只做纯计算的 handler 可以不包，但**统一包上**最省心也最安全。
    // ────────────────────────────────────────────────────────────────────────

    /** 把一段工作排到**客户端主线程**执行。 */
    @SideOnly(Side.CLIENT)
    public static void scheduleClient(Runnable task) {
        // 全限定名而不是 import —— Minecraft 是客户端类，公共类里不能出现它的常量池引用
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(task);
    }

    /** 把一段工作排到**服务端主线程**执行。 */
    public static void scheduleServer(Runnable task) {
        // ⚠️ 1.12.2 的 MinecraftServer **没有静态 getServer()**（那是高版本的）
        //    → 走 Forge 的 FMLCommonHandler ✓
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            server.addScheduledTask(task);
        } else {
            // 理论上不该发生（收到包时服务端必然已启动），兜底直接跑以免丢包
            task.run();
        }
    }
}
