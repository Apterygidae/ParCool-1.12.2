package com.alrex.parcool.server.command.impl;

import com.alrex.parcool.ParCool;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

/**
 * {@code /parcool} 命令树的入口（纯树，不含注册/生命周期 —— 那些在
 * {@link com.alrex.parcool.server.command.CommandRegistry}）。
 *
 * <h3>1.12.2 vs 上游</h3>
 * <ul>
 *   <li>上游：{@code Commands.literal("parcool").then(limitation).then(zipline)}
 *       （Brigadier）。1.12.2 没有 Brigadier →
 *       换成 Forge 自带的 {@link CommandTreeBase}（它替我们做了
 *       「按第一个 token 分发子命令 + Tab 补全子命令名 + 子命令权限检查」三件事）。</li>
 * </ul>
 *
 * <h3>⚠️ 权限</h3>
 * <p>{@code CommandBase.getRequiredPermissionLevel()} 在 1.12.2 <b>默认是 4</b> ——
 * 而 Brigadier 的 {@code Commands.literal("parcool")} 没有 {@code requires}，
 * 等于「任何人可执行」。所以根节点必须显式覆写成 0，
 * 真正的权限控制交给各子命令（{@code zipline}=2、{@code limitation set/enable/disable/delete}=2）。</p>
 */
public class ParCoolCommand extends CommandTreeBase {

    public ParCoolCommand() {
        addSubcommand(new ControlLimitationCommand());
        addSubcommand(new ZiplineCommand());
    }

    @Override
    public String getName() {
        return ParCool.MOD_ID;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "parcool.command.parcool.usage";
    }

    /** ⚠️ 必须覆写：{@code CommandBase} 默认 4 会让普通玩家连 {@code /parcool} 都打不开。 */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
