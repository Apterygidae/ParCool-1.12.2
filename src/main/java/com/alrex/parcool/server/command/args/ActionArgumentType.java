package com.alrex.parcool.server.command.args;

import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.ActionList;
import net.minecraft.command.CommandException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 动作名参数的解析与补全。
 *
 * <h3>1.12.2 的退化</h3>
 * <p>上游是 Brigadier 的 {@code ArgumentType<Class<? extends Action>>}
 * （自带 {@code StringReader} / {@code Message} / {@code SuggestionsBuilder}）。
 * <b>1.12.2 没有 Brigadier</b>（命令走 {@code ICommand} 体系）→
 * 本类退化成<b>静态的「解析 + 补全」工具</b>，由各 {@code CommandBase} 子类直接调用。</p>
 *
 * <p>语义（错误文案 key、可补全的候选集）与上游一致，
 * 所以 {@code /parcool limitation get ... possibility <TAB>} 给出的候选不会变。</p>
 */
public final class ActionArgumentType {

    private ActionArgumentType() {
    }

    /**
     * @throws CommandException 名字不存在时（错误 key 与上游一致，文案在 lang 里）
     */
    public static Class<? extends Action> parseAction(String name) throws CommandException {
        Class<? extends Action> result = ActionList.getByName(name);
        if (result == null) {
            throw new CommandException("parcool.command.message.invalidActionName", name);
        }
        return result;
    }

    /** Tab 补全候选（前缀匹配，与上游 {@code filter(startsWith)} 一致）。 */
    public static List<String> getCompletions(String prefix) {
        List<String> list = new ArrayList<>();
        for (String name : ActionList.NAMES) {
            if (name.startsWith(prefix)) list.add(name);
        }
        return list;
    }

    public static Collection<String> getExamples() {
        return ActionList.NAMES;
    }
}
