package com.alrex.parcool.server.command;

import com.alrex.parcool.server.command.impl.ParCoolCommand;

/**
 * 命令注册 + 服务端生命周期接线（1.12.2 版）。
 *
 * <p>上游是 {@code CommandRegistry.register(CommandDispatcher<CommandSource>)}
 * （由 {@code RegisterCommandsEvent} 驱动）<b>加</b>
 * {@code registerArgumentTypes(FMLCommonSetupEvent)}（向 Brigadier 的
 * {@code ArgumentTypes} 注册自定义参数类型，好让它能跨网络同步）。</p>
 *
 * <p><b>1.12.2 两件事都不存在</b>：</p>
 * <ul>
 *   <li>没有 {@code CommandDispatcher} / {@code RegisterCommandsEvent}
 *       → 这里改成 {@code FMLServerStartingEvent.registerServerCommand(ICommand)}</li>
 *   <li>没有 {@code ArgumentTypes} / {@code IArgumentSerializer}
 *       → 参数只在服务端本地解析，不存在"参数类型要序列化进网络包"这件事
 *       → <b>上游的 {@code registerArgumentTypes} 整段消失</b>
 *       （这是本层唯一一处「上游有、1.12.2 直接不存在」的东西）</li>
 * </ul>
 *
 * <p>顺带把 {@link com.alrex.parcool.server.limitation.Limitations} 的
 * init / save 挂上 —— 上游也是同一个事件里做的（{@code Limitations.init/save}）。
 * ⚠️ 但事件种类从 {@code FMLServerAboutToStartEvent} 换成了
 * {@code FMLServerStartingEvent}，原因见
 * {@link com.alrex.parcool.utilities.ServerUtil}。</p>
 */
public final class CommandRegistry {

    private CommandRegistry() {
    }

    /** 服务端启动：初始化限制仓库 + 注册 {@code /parcool}。 */
    public static void register(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        com.alrex.parcool.server.limitation.Limitations.init(event);
        event.registerServerCommand(new ParCoolCommand());
    }

    /** 服务端关停：限制落盘。 */
    public static void save(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        com.alrex.parcool.server.limitation.Limitations.save();
    }
}
