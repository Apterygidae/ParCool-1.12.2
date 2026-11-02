package com.alrex.parcool;

import com.alrex.parcool.common.capability.capabilities.Capabilities;
import com.alrex.parcool.common.registries.EventBusForgeRegistry;
import com.alrex.parcool.config.Forge14ConfigManager;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.core.ParCoolCore;
import com.alrex.parcool.core.StartupChecks;
import com.alrex.parcool.proxy.CommonProxy;
import com.alrex.parcool.server.command.CommandRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ParCool! 1.12.2 版主类（自 1.16.5 反向移植）。
 *
 * <p>当前进度：P0（Mixin 链路 + 工程骨架）✅ 、P1（平台层基座）进行中。
 * 网络层为 P2，Capability 与 Action 系统为 P3/P4。</p>
 */
@Mod(
        modid = ParCool.MOD_ID,
        name = ParCool.NAME,
        version = ParCool.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        // ParCool 自己实现了协议版本校验（SyncClientInformationMessage），
        // 因此不要求客户端/服务端 mod 版本严格一致
        acceptableRemoteVersions = "*",
        dependencies = ParCool.DEPENDENCIES,
        // ★ 配置界面（P5）：挂上工厂后，「模组列表 → ParCool → Config」按钮即可用 ✓
        //   ⚠️ Forge 是**按类名反射加载**它的 ✓，且只在客户端调用（FMLClientHandler）✓
        //      → 该实现类**不加** @SideOnly ✗（详见其类注释 ✓）
        guiFactory = "com.alrex.parcool.client.gui.ParCoolGuiFactory"
)
public class ParCool {

    public static final String MOD_ID = "parcool";
    public static final String NAME = "ParCool!";

    /** 与 gradle.properties 的 mod_version 保持一致。 */
    public static final String VERSION = "3.4.3.3";

    /**
     * FML 依赖声明 —— 这就是那张「缺失依赖」界面的唯一来源，不需要写任何 GUI 代码。
     *
     * <ul>
     *   <li>{@code required-after:forge@[...]} —— Forge 版本下限</li>
     *   <li>{@code required-after:aquaacrobatics@[...]} —— Aqua 硬依赖。缺失时 FML 抛出
     *       LoaderException 并显示内置的「缺失依赖」屏（含「打开模组文件夹」/「打开latest.log」
     *       两个按钮）；同时保证 Aqua 先于 ParCool 加载</li>
     *   <li>{@code after:patchouli} —— 可选前置，只约束加载顺序</li>
     * </ul>
     *
     * <p>每段必须以 {@code ';'} 结尾，否则 FML 的依赖解析会出问题。</p>
     *
     * <p>注意：没有声明 {@code mixinbootstrap} —— 它有 MixinBootstrap / MixinBooter 两种提供方式、
     * modid 不统一，硬写会造成误报。而 Aqua 本身就依赖 MixinBootstrap，装了 Aqua 即满足。</p>
     */
    public static final String DEPENDENCIES =
            "required-after:forge@[14.23.5.2847,);"
            + "required-after:aquaacrobatics@[1.15.4,);"
            + "after:patchouli";

    public static final Logger LOGGER = LogManager.getLogger("ParCool");

    private static final String CLIENT_PROXY = "com.alrex.parcool.proxy.ClientProxy";
    private static final String SERVER_PROXY = "com.alrex.parcool.proxy.ServerProxy";

    /**
     * 1.12.2 用 {@code @SidedProxy} 取代 1.16.5 的 {@code DistExecutor.unsafeRunForDist}。
     */
    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = SERVER_PROXY)
    public static CommonProxy PROXY;

    public ParCool() {
        // 必须在构造期注册：注册表事件在第一帧 preInit 就会触发，晚了会漏掉。
        EventBusForgeRegistry.register();
        // 1.12.2 的 EntityRegistry.registerModEntity 需要一个「mod 实例」参数 ✗
        //（1.16 用 DeferredRegister 时不需要 ✗）→ 这里留个引用 ✓
        INSTANCE = this;
    }

    /**
     * 本 mod 的实例 ✓ —— 供 {@code EntityRegistry.registerModEntity} 之类的
     * 老式注册 API 使用 ✓（它们要一个 {@code Object mod} 参数 ✗）。
     */
    public static ParCool INSTANCE = null;

    public static boolean isActive() {
        return PROXY.parCoolIsActive();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[ParCool] preInit - version {}, coremod loaded = {}", VERSION, ParCoolCore.isMixinLoaded());

        // 1.12.2 没有 ModLoadingContext.registerConfig，配置要手工载入。
        // 路径用 getSuggestedConfigurationFile()：它返回的正是 config/parcool.cfg
        // （反编译源码 FMLPreInitializationEvent.java:81），这是 1.12.2 的惯例路径。
        // 1.13+ 的「客户端/服务端双配置文件 + serverconfig 目录」在 1.12.2 不存在，
        // 所以两侧设置合并进同一个文件，用 [Client.*] / [Server.*] 区段区分。
        Forge14ConfigManager.load(event.getSuggestedConfigurationFile());
        Forge14ConfigManager.registerAll();

        // Capability 必须在 preInit 注册，且早于任何实体的 AttachCapabilitiesEvent
        Capabilities.register();

        PROXY.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[ParCool] init");
        PROXY.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("[ParCool] postInit");
        // 必须在 preInit 之后：Aqua 在它自己的 preInit 里才给配置字段赋值
        StartupChecks.run();
        PROXY.postInit();
    }

    /**
     * 服务端启动（P6 命令层）。
     *
     * <p>本 mod 唯一一个必须挂 {@code FMLServer*} 事件的地方 —— 原因：</p>
     * <ul>
     *   <li>{@code /parcool} 命令必须在服务端实例存在之后才能注册
     *       （1.12.2 没有 {@code RegisterCommandsEvent}，只能走
     *       {@code FMLServerStartingEvent.registerServerCommand}）</li>
     *   <li>{@code Limitations.init} 要读世界存档目录 —— 而
     *       {@code FMLServerAboutToStartEvent} <b>在世界加载之前</b>触发
     *       （{@code DedicatedServer:271} 早于 {@code :273 loadAllWorlds}），拿不到。
     *       详见 {@code utilities/ServerUtil}。</li>
     * </ul>
     */
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        CommandRegistry.register(event);
        LOGGER.info("[ParCool] serverStarting - /parcool registered, limitations initialized");
    }

    /** 服务端关停：把内存里的限制落盘（1.12.2 不会自动保存）。 */
    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        CommandRegistry.save(event);
        LOGGER.info("[ParCool] serverStopping - limitations saved");
    }

    /**
     * 供需要「读配置后立刻保存」的场景使用（例如命令或 GUI 改值之后）。
     * 1.12.2 不会自动落盘。
     */
    public static void saveConfig() {
        Forge14ConfigManager.save();
    }

    /** 语法糖，避免调用方到处 import ParCoolConfig.Client。 */
    public static boolean isParCoolEnabledOnClient() {
        return ParCoolConfig.Client.Booleans.ParCoolIsActive.get();
    }
}
