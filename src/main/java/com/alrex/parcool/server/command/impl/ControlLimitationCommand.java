package com.alrex.parcool.server.command.impl;

import com.alrex.parcool.api.unstable.Limitation;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.ActionList;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.server.command.args.ActionArgumentType;
import com.alrex.parcool.server.command.args.LimitationIDArgumentType;
import com.alrex.parcool.server.command.args.LimitationItemArgumentType;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.server.command.CommandTreeBase;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code /parcool limitation ...} —— 管理员按玩家施加限制。
 *
 * <pre>
 *   /parcool limitation get    &lt;group&gt;:&lt;name&gt; of &lt;player&gt; [&lt;core&gt;]
 *   /parcool limitation get    individual    of &lt;player&gt; [&lt;core&gt;]
 *   /parcool limitation get    global                   [&lt;core&gt;]
 *   /parcool limitation set    &lt;group&gt;:&lt;name&gt; of &lt;players&gt; &lt;core&gt; &lt;value&gt;
 *   /parcool limitation set    individual    of &lt;players&gt; &lt;core&gt; &lt;value&gt;
 *   /parcool limitation enable &lt;group&gt;:&lt;name&gt; of &lt;players&gt;
 *   /parcool limitation enable individual    of &lt;players&gt;
 *   /parcool limitation disable &lt;...&gt;        （同上）
 *   /parcool limitation delete &lt;group&gt;:&lt;name&gt;
 * </pre>
 * {@code <core>} = {@code to_default} | {@code boolean <item>} | {@code integer <item>} |
 * {@code reals <item>} | {@code possibility <action>} | {@code least_stamina_consumption <action>}
 *
 * <h3>为什么是手写解析，而不是继续用 Brigadier 的树</h3>
 * <p>上游用 Brigadier 的 {@code ArgumentBuilder} 层层 {@code .then()} 拼出这棵树
 * （573 行）。<b>1.12.2 没有 Brigadier</b>，{@code CommandTreeBase} 只提供
 * 「第一层 = 子命令名」的分发；剩下那些「不定长的 token 序列」只能自己走。</p>
 * <p>好消息是 <b>1.12.2 的解析其实是确定性的</b>：Brigadier 的
 * {@code EntityArgument.player()/players()} 与 {@code BlockPosArgument} 都<b>只吃一个 token</b>，
 * 所以「第几个 token 是什么」是固定的 —— 不需要真正的树遍历。</p>
 *
 * <p><b>已知（可接受的）偏差</b>：上游 {@code get} 用 {@code EntityArgument.player()}
 * （只接受单个玩家），这里统一走 {@code CommandBase.getPlayers(...)}（接受 {@code @a} 等选择器）。
 * 结果是 {@code /parcool limitation get <id> of @a} 在 1.12.2 上会成功（上游会报错），
 * 取的是列表里第一个玩家。这是<b>放宽</b>而非收紧，不影响任何现有用法。</p>
 */
public class ControlLimitationCommand extends CommandTreeBase {

    public ControlLimitationCommand() {
        addSubcommand(new GetCommand());
        addSubcommand(new SetCommand());
        addSubcommand(new EnableCommand());
        addSubcommand(new DisableCommand());
        addSubcommand(new DeleteCommand());
    }

    @Override
    public String getName() {
        return "limitation";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "parcool.command.limitation.usage";
    }

    /**
     * ⚠️ 必须覆写：{@code CommandBase} 默认 4，而 {@code CommandTreeBase.execute}
     * 会对**子命令**调 {@code checkPermission} —— 不覆写的话
     * {@code /parcool limitation get ...} 就只对 OP 可用
     * （上游的 {@code Commands.literal("limitation")} 没有 {@code requires}，即 0）。
     */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // ────────────────────────────────────────────────────────────────────────
    // 共用解析/回执
    // ────────────────────────────────────────────────────────────────────────

    /** 解析出的「作用域」（上游把这三段分别做成三种 ArgumentBuilder 分支）。 */
    private static final class Scope {
        final List<EntityPlayerMP> players;
        @Nullable
        final Limitation.ID id;
        final boolean global;
        final int nextIndex;

        Scope(List<EntityPlayerMP> players, @Nullable Limitation.ID id, boolean global, int nextIndex) {
            this.players = players;
            this.id = id;
            this.global = global;
            this.nextIndex = nextIndex;
        }
    }

    private static Scope parseScope(MinecraftServer server, ICommandSender sender, String[] args,
                                    String usage, boolean allowGlobal) throws CommandException {
        if (args.length == 0) throw new WrongUsageException(usage);

        if ("global".equals(args[0])) {
            if (!allowGlobal) throw new WrongUsageException(usage);
            return new Scope(Collections.emptyList(), null, true, 1);
        }
        if ("individual".equals(args[0])) {
            if (args.length < 3 || !"of".equals(args[1])) throw new WrongUsageException(usage);
            return new Scope(CommandBase.getPlayers(server, sender, args[2]), null, false, 3);
        }
        Limitation.ID id = LimitationIDArgumentType.parse(args[0]);
        if (args.length < 3 || !"of".equals(args[1])) throw new WrongUsageException(usage);
        return new Scope(CommandBase.getPlayers(server, sender, args[2]), id, false, 3);
    }

    /** 对应上游的 {@code getLimitationInstance(...)}。 */
    private static List<Limitation> instancesOf(MinecraftServer server, Scope scope) {
        if (scope.global) {
            return Collections.singletonList(Limitation.getGlobal(server));
        }
        List<Limitation> list = new ArrayList<>(scope.players.size());
        for (EntityPlayerMP player : scope.players) {
            list.add(scope.id != null ? Limitation.get(player, scope.id) : Limitation.getIndividual(player));
        }
        return list;
    }

    private static String[] shift(String[] args, int from) {
        if (from >= args.length) return new String[0];
        return Arrays.copyOfRange(args, from, args.length);
    }

    private static void requireLength(String[] args, int expected, String usage) throws CommandException {
        if (args.length != expected) throw new WrongUsageException(usage);
    }

    /** 上游 {@code sendSuccess(component, false)}：只回执行者。 */
    private static void reply(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    /** 上游 {@code sendSuccess(component, true)}：回执行者 + 广播给其他 OP。 */
    private static void broadcast(ICommand command, ICommandSender sender, String key, Object... args) {
        CommandBase.notifyCommandListener(sender, command, 0, key, args);
        CommandBase.notifyCommandListener(sender, command, 1, key, args);
    }

    // ────────────────────────────────────────────────────────────────────────
    // get
    // ────────────────────────────────────────────────────────────────────────

    public static class GetCommand extends CommandBase {
        @Override
        public String getName() {
            return "get";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "parcool.command.limitation.get.usage";
        }

        /** 上游的 {@code Commands.literal("get")} 没有 {@code requires} → 0（只读，人人可用）。 */
        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            String usage = getUsage(sender);
            Scope scope = parseScope(server, sender, args, usage, true);
            String[] rest = shift(args, scope.nextIndex);
            List<Limitation> limitations = instancesOf(server, scope);
            Limitation limitation = limitations.get(0);

            if (rest.length == 0) {
                reply(sender, describe(limitation));
                return;
            }
            switch (rest[0]) {
                case "boolean":
                    requireLength(rest, 2, usage);
                    reply(sender, Boolean.toString(limitation.get(LimitationItemArgumentType.parseBoolean(rest[1]))));
                    return;
                case "integer":
                    requireLength(rest, 2, usage);
                    reply(sender, Integer.toString(limitation.get(LimitationItemArgumentType.parseInteger(rest[1]))));
                    return;
                case "reals":
                    requireLength(rest, 2, usage);
                    reply(sender, Double.toString(limitation.get(LimitationItemArgumentType.parseDouble(rest[1]))));
                    return;
                case "possibility":
                    requireLength(rest, 2, usage);
                    reply(sender, Boolean.toString(limitation.isPermitted(ActionArgumentType.parseAction(rest[1]))));
                    return;
                case "least_stamina_consumption":
                    requireLength(rest, 2, usage);
                    reply(sender, Integer.toString(
                            limitation.getLeastStaminaConsumption(ActionArgumentType.parseAction(rest[1]))));
                    return;
                default:
                    throw new WrongUsageException(usage);
            }
        }

        /** 对应上游 {@code getLimitationInfo} 拼的那个多行字符串（一字不改，保持可读性可比对）。 */
        private static String describe(Limitation limitation) {
            StringBuilder builder = new StringBuilder();
            builder.append("- Limitation Info -\n");
            builder.append("Enabled : ").append(limitation.isEnabled()).append('\n');
            for (Class<? extends Action> action : ActionList.ACTIONS) {
                builder.append("  ").append(action.getSimpleName()).append(" : ").append('\n')
                        .append("    ").append("permitted : ").append(limitation.isPermitted(action)).append('\n')
                        .append("    ").append("stamina consumption : ")
                        .append(limitation.getLeastStaminaConsumption(action)).append('\n');
            }
            for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) {
                builder.append("  ").append(item.getPath()).append(" : ").append(limitation.get(item)).append('\n');
            }
            for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) {
                builder.append("  ").append(item.getPath()).append(" : ").append(limitation.get(item)).append('\n');
            }
            for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) {
                builder.append("  ").append(item.getPath()).append(" : ").append(limitation.get(item)).append('\n');
            }
            builder.append("----------");
            return builder.toString();
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                              @Nullable BlockPos targetPos) {
            return LimitationCompletion.complete(args, true);
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            // 只有 `of <player>` 那一格是人名
            return args.length >= 3 && "of".equals(args[1]) && index == 2;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // set
    // ────────────────────────────────────────────────────────────────────────

    public static class SetCommand extends CommandBase {
        @Override
        public String getName() {
            return "set";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "parcool.command.limitation.set.usage";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            String usage = getUsage(sender);
            Scope scope = parseScope(server, sender, args, usage, false);
            String[] rest = shift(args, scope.nextIndex);
            if (rest.length == 0) throw new WrongUsageException(usage);
            List<Limitation> limitations = instancesOf(server, scope);

            switch (rest[0]) {
                case "to_default": {
                    requireLength(rest, 1, usage);
                    int num = 0;
                    for (Limitation limitation : limitations) {
                        limitation.setDefault().apply();
                        limitation.save();
                        num++;
                    }
                    broadcast(this, sender, "parcool.command.message.success.setLimitationToDefault", num);
                    return;
                }
                case "boolean": {
                    requireLength(rest, 3, usage);
                    ParCoolConfig.Server.Booleans item = LimitationItemArgumentType.parseBoolean(rest[1]);
                    boolean value = CommandBase.parseBoolean(rest[2]);
                    int num = 0;
                    for (Limitation limitation : limitations) {
                        limitation.set(item, value).apply();
                        limitation.save();
                        num++;
                    }
                    broadcast(this, sender, "parcool.command.message.success.set",
                            num, item.getPath(), Boolean.toString(value));
                    return;
                }
                case "integer": {
                    requireLength(rest, 3, usage);
                    ParCoolConfig.Server.Integers item = LimitationItemArgumentType.parseInteger(rest[1]);
                    int value = CommandBase.parseInt(rest[2]);
                    // 上游在这里夹到 [Min, Max]
                    if (value < item.Min) value = item.Min;
                    if (value > item.Max) value = item.Max;
                    int num = 0;
                    for (Limitation limitation : limitations) {
                        limitation.set(item, value).apply();
                        limitation.save();
                        num++;
                    }
                    broadcast(this, sender, "parcool.command.message.success.set",
                            num, item.getPath(), Integer.toString(value));
                    return;
                }
                case "reals": {
                    requireLength(rest, 3, usage);
                    ParCoolConfig.Server.Doubles item = LimitationItemArgumentType.parseDouble(rest[1]);
                    double value = CommandBase.parseDouble(rest[2]);
                    if (value < item.Min) value = item.Min;
                    if (value > item.Max) value = item.Max;
                    int num = 0;
                    for (Limitation limitation : limitations) {
                        limitation.set(item, value).apply();
                        limitation.save();
                        num++;
                    }
                    broadcast(this, sender, "parcool.command.message.success.set",
                            num, item.getPath(), Double.toString(value));
                    return;
                }
                case "possibility": {
                    requireLength(rest, 3, usage);
                    Class<? extends Action> action = ActionArgumentType.parseAction(rest[1]);
                    boolean value = CommandBase.parseBoolean(rest[2]);
                    int num = 0;
                    for (Limitation limitation : limitations) {
                        limitation.permit(action, value).apply();
                        limitation.save();
                        num++;
                    }
                    broadcast(this, sender, "parcool.command.message.success.setPermissionOfAction",
                            num, action.getSimpleName(), Boolean.toString(value));
                    return;
                }
                case "least_stamina_consumption": {
                    requireLength(rest, 3, usage);
                    Class<? extends Action> action = ActionArgumentType.parseAction(rest[1]);
                    int value = CommandBase.parseInt(rest[2]);
                    if (value < 0) value = 0;   // 上游 IntegerArgumentType.integer(0, MAX_VALUE)
                    int num = 0;
                    for (Limitation limitation : limitations) {
                        limitation.setLeastStaminaConsumption(action, value).apply();
                        limitation.save();
                        num++;
                    }
                    broadcast(this, sender, "parcool.command.message.success.setStaminaConsumption",
                            num, action.getSimpleName(), Integer.toString(value));
                    return;
                }
                default:
                    throw new WrongUsageException(usage);
            }
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                              @Nullable BlockPos targetPos) {
            return LimitationCompletion.complete(args, false);
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return args.length >= 3 && "of".equals(args[1]) && index == 2;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // enable / disable
    // ────────────────────────────────────────────────────────────────────────

    public abstract static class ToggleCommand extends CommandBase {
        @Override
        public String getUsage(ICommandSender sender) {
            return "parcool.command.limitation." + getName() + ".usage";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        /** {@code true} = enable。 */
        protected abstract boolean enabling();

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            String usage = getUsage(sender);
            Scope scope = parseScope(server, sender, args, usage, false);
            if (scope.nextIndex != args.length) throw new WrongUsageException(usage);
            List<Limitation> limitations = instancesOf(server, scope);
            int num = 0;
            for (Limitation limitation : limitations) {
                if (enabling()) {
                    limitation.enable().apply();
                } else {
                    limitation.disable().apply();
                }
                limitation.save();
                num++;
            }
            broadcast(this, sender,
                    enabling() ? "parcool.command.message.success.enableLimitation"
                            : "parcool.command.message.success.disableLimitation",
                    num);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                              @Nullable BlockPos targetPos) {
            return LimitationCompletion.complete(args, false);
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return args.length >= 3 && "of".equals(args[1]) && index == 2;
        }
    }

    public static class EnableCommand extends ToggleCommand {
        @Override
        public String getName() {
            return "enable";
        }

        @Override
        protected boolean enabling() {
            return true;
        }
    }

    public static class DisableCommand extends ToggleCommand {
        @Override
        public String getName() {
            return "disable";
        }

        @Override
        protected boolean enabling() {
            return false;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // delete
    // ────────────────────────────────────────────────────────────────────────

    public static class DeleteCommand extends CommandBase {
        @Override
        public String getName() {
            return "delete";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "parcool.command.limitation.delete.usage";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            String usage = getUsage(sender);
            requireLength(args, 1, usage);
            Limitation.ID id = LimitationIDArgumentType.parse(args[0]);
            if (Limitation.delete(id)) {
                // 上游：删除后只刷新「另外的玩家」，且只重下发限制（不动体力）
                for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                    com.alrex.parcool.server.limitation.Limitations.updateOnlyLimitation(player);
                }
                broadcast(this, sender, "parcool.command.message.success.deleteLimitation", id.toString());
            } else {
                reply(sender, "Error:deleting folder failed");
            }
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                              @Nullable BlockPos targetPos) {
            if (args.length == 1) return LimitationIDArgumentType.getCompletions(args[0]);
            return Collections.emptyList();
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return false;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Tab 补全：把上游 Brigadier 自动提供的那部分手工补齐
    // ────────────────────────────────────────────────────────────────────────

    static final class LimitationCompletion {
        private LimitationCompletion() {
        }

        /**
         * 作用域 + 核心命令的补全。
         *
         * <p>⚠️ {@code args[0]} 既可能是 {@code global}/{@code individual} 关键字，
         * 也可能是 {@code <group>:<name>}，所以第一格有两种候选 —— 上游 Brigadier
         * 也是两种并存。</p>
         */
        static List<String> complete(String[] args, boolean allowGlobal) {
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                if (allowGlobal) list.add("global");
                list.add("individual");
                list.addAll(LimitationIDArgumentType.getCompletions(args[0]));
                return list;
            }
            if (args.length == 2) {
                return "of".startsWith(args[1]) ? Collections.singletonList("of") : Collections.emptyList();
            }
            if (args.length == 3) {
                return Collections.emptyList();   // 玩家名：交给原版的实体选择器补全
            }
            // args[0..2] = 作用域；args[3] = 核心命令
            int core = 3;
            if (args.length == core + 1) {
                List<String> list = new ArrayList<>();
                for (String name : new String[]{"to_default", "boolean", "integer", "reals",
                        "possibility", "least_stamina_consumption"}) {
                    if (name.startsWith(args[core])) list.add(name);
                }
                return list;
            }
            String sub = args[core];
            String prefix = args[args.length - 1];
            if ("boolean".equals(sub)) return LimitationItemArgumentType.completeBooleans(prefix);
            if ("integer".equals(sub)) return LimitationItemArgumentType.completeIntegers(prefix);
            if ("reals".equals(sub)) return LimitationItemArgumentType.completeDoubles(prefix);
            if ("possibility".equals(sub) || "least_stamina_consumption".equals(sub)) {
                return ActionArgumentType.getCompletions(prefix);
            }
            return Collections.emptyList();
        }
    }
}
