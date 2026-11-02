package com.alrex.parcool.common.registries;

import com.alrex.parcool.common.action.ActionProcessor;
import com.alrex.parcool.common.handlers.AddAttributesHandler;
import com.alrex.parcool.common.handlers.LoginLogoutHandler;
import com.alrex.parcool.common.handlers.PlayerDamageHandler;
import com.alrex.parcool.common.handlers.PlayerJumpHandler;
import com.alrex.parcool.common.handlers.AttachCapabilityHandler;
import com.alrex.parcool.common.handlers.PlayerJoinHandler;
import com.alrex.parcool.common.network.SyncStaminaToClientMessage;
import net.minecraftforge.common.MinecraftForge;

/**
 * 事件总线注册（1.12.2 版）。
 *
 * <p>对应上游的 {@code common/registries/EventBusForgeRegistry}。差异在于：</p>
 * <ul>
 *   <li>1.16.5 有<b>两条</b>总线（{@code FMLJavaModLoadingContext.get().getModEventBus()} 与
 *       {@code MinecraftForge.EVENT_BUS}）。注册表事件走 mod 总线，其余走 Forge 总线</li>
 *   <li>1.12.2 <b>只有一条</b> {@code MinecraftForge.EVENT_BUS}，注册表事件也在上面</li>
 *   <li>1.16.5 的 {@code IEventBus.register(Class)} 静态订阅在 1.12.2 写作
 *       {@code MinecraftForge.EVENT_BUS.register(Class)} —— 用法一致</li>
 * </ul>
 *
 * <p><b>必须在 {@code @Mod} 的构造期完成注册</b>，否则会漏掉注册表事件
 * （它们在第一帧 preInit 期间就触发了）。</p>
 *
 * <p><b>本类必须保持「纯公共侧」——不允许出现任何客户端类的引用。</b>
 * 客户端事件注册放在 {@code ClientProxy}（它带 {@code @SideOnly(Side.CLIENT)}，
 * 由 {@code @SidedProxy} 保证服务端不会加载）。原因见
 * {@link #register()} 的注释。</p>
 */
public class EventBusForgeRegistry {

    private EventBusForgeRegistry() {
    }

    /** 双端通用。在 {@code @Mod} 构造器里调用。 */
    public static void register() {
        // 注册表事件（SoundEvent / Potion / PotionType）
        MinecraftForge.EVENT_BUS.register(RegistryHandler.class);

        // 自定义属性挂载（EntityConstructing）
        MinecraftForge.EVENT_BUS.register(AddAttributesHandler.class);

        // Capability 挂载（AttachCapabilitiesEvent）
        MinecraftForge.EVENT_BUS.register(AttachCapabilityHandler.class);

        // 玩家进服时的限制/体力下发（时机必须晚于连接进入 PLAY）
        MinecraftForge.EVENT_BUS.register(PlayerJoinHandler.class);

        // 玩家登出：卸载并落盘该玩家的限制（P6）
        MinecraftForge.EVENT_BUS.register(LoginLogoutHandler.class);

        // 服务端每 tick 把「体力同步请求」合并成一个包发出去
        MinecraftForge.EVENT_BUS.register(SyncStaminaToClientMessage.class);

        // 动作系统的每 tick 驱动（实例方法，所以注册实例）
        MinecraftForge.EVENT_BUS.register(ActionProcessor.INSTANCE);

        // 跳跃 / 落地瞬间的接线（ChargeJump 的 onJump / onLand）
        MinecraftForge.EVENT_BUS.register(PlayerJumpHandler.class);

        // 伤害处理（目前只做「闪避免疫伤害」✓ —— 上游在同一个类里还含破坠减伤，见该类注释 ✓）
        MinecraftForge.EVENT_BUS.register(PlayerDamageHandler.class);

        // ── 待接入（对应阶段） ────────────────────────────────────────────
        // P2: LoginLogoutHandler / PlayerCloneHandler
        // P3: PlayerVisibilityHandler（需要替代 1.12.2 不存在的 LivingVisibilityEvent）
        // P4: ActionProcessor（TickEvent.PlayerTickEvent）/ PlayerJumpHandler / PlayerDamageHandler
        //
        // ⚠️ 客户端专属的注册一律放 ClientProxy，不要在这里加 @SideOnly(Side.CLIENT) 方法。
        //    理由：本类在服务端一定会被加载（ParCool 构造器要调它），
        //    把客户端类写进本类的常量池会多一层依赖 —— 依赖 FML 的 SideTransformer
        //    把方法「整个删掉」才能保证不被解析。而走 ClientProxy 是
        //    @SidedProxy 的硬保证，更稳。两者都是 1.12.2 的既有手段，但后者不依赖运行时剥离。
    }
}
