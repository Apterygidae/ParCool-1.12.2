package com.alrex.parcool.proxy;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.ClientCapabilityHandler;
import com.alrex.parcool.client.ClientInGameCheck;
import com.alrex.parcool.client.ClientModelHandler;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.capability.capabilities.ClientCapabilities;
import com.alrex.parcool.common.network.NetworkCheckResponse;
import com.alrex.parcool.common.network.NetworkHandler;
import com.alrex.parcool.common.network.SyncActionStateMessage;
import com.alrex.parcool.common.network.SyncClientInformationMessage;
import com.alrex.parcool.common.network.SyncServerInfoMessage;
import com.alrex.parcool.common.network.SyncStaminaMessage;
import com.alrex.parcool.common.network.SyncStaminaToClientMessage;
import com.alrex.parcool.common.network.StartBreakfallMessage;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 客户端代理。
 *
 * <p>1.12.2 客户端专属逻辑必须收敛在这个类（或它引用的类）里，
 * 并且用 {@link SideOnly} 标注，以配合 {@code @SidedProxy} 的按侧实例化。</p>
 *
 * <p><b>为什么客户端事件注册放这里而不是 {@code EventBusForgeRegistry}</b>：
 * 「专用服务端不加载客户端类」这件事，靠 {@code @SidedProxy} 是<b>硬保证</b>
 * （服务端根本不会实例化本类）；而靠 {@code @SideOnly} 剥离公共类里的某个方法，
 * 则要依赖 FML SideTransformer 在运行期把方法整个删掉才能避免常量池被解析。
 * 前者不依赖运行时行为，更稳。</p>
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // ── P5：客户端专属的 Capability（Animation） ──────────────────────
        // 注册与挂载都必须在客户端完成 —— 详见 ClientCapabilities 的类注释
        ClientCapabilities.register();
        MinecraftForge.EVENT_BUS.register(ClientCapabilityHandler.class);

        // P0 验证用，P1 之后可删
        MinecraftForge.EVENT_BUS.register(ClientInGameCheck.class);

        // ── 物品模型的显式注册（P6 ✓）────────────────────────────────────
        // ⚠️ 必须在 @SideOnly(CLIENT) 的类里挂 ✗ ——
        //    它引用了 ModelLoader / ModelResourceLocation（纯客户端类 ✗）
        //    实测（2026-10-06）：方块在世界中已正常 ✓ 但物品栏仍粉黑 ✗，
        //    且日志无模型错误 ✗ → 显式写进 ItemModelMesher.simpleShapes 绕过回退链 ✓
        MinecraftForge.EVENT_BUS.register(ClientModelHandler.class);

        // ── 实体渲染器：滑索绳索（P6 ✓）──────────────────────────────────
        // ⚠️ 1.12.2 的实体渲染器**没有 RegistryEvent** ✗ →
        //    走 RenderingRegistry.registerEntityRenderingHandler ✓
        //    （必须在客户端侧注册 ✓，所以放这里而不是 RegistryHandler ✓）
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity.class,
                com.alrex.parcool.client.renderer.entity.ZiplineRopeRenderer::new);

        // ── P5：配置界面改完后的热同步 ────────────────────────────────────
        // ⚠️ 放在**客户端**注册 ✗ —— 事件（ConfigChangedEvent）只在客户端存在 ✓，
        //    而且处理里要读 Minecraft.getMinecraft().player ✓
        MinecraftForge.EVENT_BUS.register(
                com.alrex.parcool.client.config.ConfigSyncHandler.class);

        // ── P5：体力 HUD ──────────────────────────────────────────────────
        // ⚠️ 注册**实例**（不是 .class ✗）—— HUDRegistry 内部持有 StaminaHUDController 实例 ✓，
        //    用单例注册可保证 tick 状态（动画帧/变化检测 ✓）只维护一份 ✓
        MinecraftForge.EVENT_BUS.register(
                com.alrex.parcool.client.hud.HUDRegistry.getInstance());

        // ── 待接入 ────────────────────────────────────────────────────────
        // P4-B: EnableOrDisableParCoolHandler / OpenSettingsParCoolHandler
    }

    @Override
    public void init() {
        super.init();   // 建立网络通道 + 注册「客户端 → 服务端」方向的消息

        // ── 物品染色层（滑索绳 ✓）────────────────────────────────────────
        // ⚠️ 必须放 init 而不是 preInit ✗ ——
        //    它要拿 Minecraft.getMinecraft().getItemColors() ✓，
        //    那个在 Minecraft 实例构造完才有值 ✓（preInit 时还是 null ✗）
        ClientModelHandler.registerItemColors();

        // ── 「服务端 → 客户端」方向的消息 ──────────────────────────────────
        // 这些 handler 都是 @SideOnly(Side.CLIENT) 的，所以注册语句只放在本类里 ——
        // 本类由 @SidedProxy 保证不会在专用服务端被加载（硬保证，不依赖运行期剥离）。
        NetworkHandler.INSTANCE.registerMessage(SyncStaminaMessage.HandlerClient.class,
                SyncStaminaMessage.class, NetworkHandler.ID_STAMINA, Side.CLIENT);
        NetworkHandler.INSTANCE.registerMessage(SyncStaminaToClientMessage.Handler.class,
                SyncStaminaToClientMessage.class, NetworkHandler.ID_STAMINA_TO_CLIENT, Side.CLIENT);
        NetworkHandler.INSTANCE.registerMessage(SyncClientInformationMessage.HandlerClient.class,
                SyncClientInformationMessage.class, NetworkHandler.ID_CLIENT_INFO, Side.CLIENT);
        NetworkHandler.INSTANCE.registerMessage(SyncServerInfoMessage.Handler.class,
                SyncServerInfoMessage.class, NetworkHandler.ID_SERVER_INFO, Side.CLIENT);
        NetworkHandler.INSTANCE.registerMessage(SyncActionStateMessage.HandlerClient.class,
                SyncActionStateMessage.class, NetworkHandler.ID_ACTION_STATE, Side.CLIENT);
        // 落地缓冲：服务端 → 客户端（客户端收到后触发 Roll / Tap）
        NetworkHandler.INSTANCE.registerMessage(StartBreakfallMessage.Handler.class,
                StartBreakfallMessage.class, NetworkHandler.ID_BREAKFALL, Side.CLIENT);
        // P2-A 通道自检（收到的方向）
        NetworkHandler.INSTANCE.registerMessage(NetworkCheckResponse.Handler.class,
                NetworkCheckResponse.class, NetworkHandler.ID_CHECK_RESPONSE, Side.CLIENT);

        ParCool.LOGGER.info("[ParCool] Network channel '{}' established (client-bound: {} messages)",
                NetworkHandler.CHANNEL_NAME, 7);

        // ── P4-B：按键与输入记录 ──────────────────────────────────────────
        KeyBindings.register();
        MinecraftForge.EVENT_BUS.register(KeyRecorder.class);

        // P5 阶段在这里接入：HUD 注册
    }

    @Override
    public boolean parCoolIsActive() {
        // 对应上游 ParCoolConfig.Client.Booleans.ParCoolIsActive.get()
        // 1.12.2 的配置项已由 Forge14ConfigManager 在 preInit 注册完毕，这里可以安全读取
        return ParCoolConfig.Client.Booleans.ParCoolIsActive.get();
    }

    /**
     * 判断「是不是本地玩家」✓。
     *
     * <h3>🚨🚨🚨 绝不能用 {@code player == Minecraft.getMinecraft().player} ✗✗</h3>
     * <p><b>实测 bug（2026-10-12）</b>：体力条 HUD 死活不显示 ✓，日志写着
     * {@code [ParCool] StaminaHUD: render: stamina implementation is OtherStamina} ✗ ——
     * 而配置里明明是 {@code used_stamina=Default} ✗（应当拿到 {@code ParCoolStamina} ✓）。</p>
     *
     * <p><b>根因：调用时机 ✗</b>。本方法的<b>唯一调用点</b>是
     * {@code StaminaProvider} 的构造器 ✓，而它在
     * <b>{@code AttachCapabilitiesEvent}（实体加入世界）</b> 时被创建 ✗ ——
     * <b>那一刻 {@code Minecraft.getMinecraft().player} 还没被赋值</b> ✗
     * → 本地玩家被误判成「其他玩家」✗ → 拿到只接受网络同步的 {@code OtherStamina} ✗
     * → 既不能自己 tick 体力 ✗、也不是 {@code ParCoolStamina} ✗ → HUD 直接跳过 ✓。</p>
     *
     * <h3>✅ 正解：按【实体类型】判断，与时机无关 ✓</h3>
     * <p>1.12.2 里「本地玩家」在客户端**就是** {@code EntityPlayerSP} 这个类 ✓
     * （其他玩家是 {@code EntityOtherPlayerMP} ✓ → 正确地判 false ✓），
     * 客户端**不会**有第二个 {@code EntityPlayerSP} ✓。</p>
     *
     * <p>⚠️ 与之相对，<b>不要</b>把这个判断挪到公共类去做 {@code instanceof EntityPlayerSP} ✗ ——
     * 那样专用服务端会去加载客户端类 ✗（{@code NoClassDefFoundError} ✓）。
     * 放在本类（{@code @SideOnly(CLIENT)}）里是安全的 ✓。</p>
     */
    @Override
    public boolean isLocalPlayer(EntityPlayer player) {
        return player instanceof net.minecraft.client.entity.EntityPlayerSP;
    }
}
