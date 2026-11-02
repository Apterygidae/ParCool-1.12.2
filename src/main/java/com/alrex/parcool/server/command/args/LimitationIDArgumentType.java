package com.alrex.parcool.server.command.args;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.unstable.Limitation;
import com.alrex.parcool.server.limitation.Limitations;
import net.minecraft.command.CommandException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 限制 ID（{@code <group>:<name>}）参数的解析与补全。
 *
 * <p>1.12.2 无 Brigadier → 从 {@code ArgumentType<Limitation.ID>} 退化为静态工具
 * （原因见 {@link ActionArgumentType}）。</p>
 *
 * <p>⚠️ 这里用的是 {@code api.unstable.Limitation.ID}（命令侧门面的 ID），
 * 而补全读的是 {@code server.limitation.Limitations.getRegisteredIDs()}（数据侧的 ID）——
 * 上游也是这样分开的，不要"顺手统一"。转换由 {@code ID.convert()} 完成。</p>
 */
public final class LimitationIDArgumentType {

    private static final Collection<String> EXAMPLES =
            Arrays.asList("{Your ID}:{Limitation ID}", "{parcool:example_limitation}");

    private LimitationIDArgumentType() {
    }

    /**
     * @throws CommandException 分隔符数量不对时
     */
    public static Limitation.ID parse(String value) throws CommandException {
        String[] split = value.split(":");
        if (split.length != 2) {
            throw new CommandException("parcool.command.message.invalidLimitationID", value);
        }
        return new Limitation.ID(split[0], split[1]);
    }

    /**
     * ⚠️ 上游用 {@code if (id.getGroup().equals(ParCool.MOD_ID)) continue;} 把
     * {@code parcool:global} / {@code parcool:individual} 这类内部 ID 排除在补全之外 ——
     * 它们不该被当成"可引用的限制"。这里保持一致。
     */
    public static List<String> getCompletions(String prefix) {
        List<String> list = new ArrayList<>();
        for (com.alrex.parcool.server.limitation.Limitation.ID id : Limitations.getRegisteredIDs()) {
            if (id.getGroup().equals(ParCool.MOD_ID)) continue;
            String suggestion = id.getGroup() + ":" + id.getName();
            if (suggestion.startsWith(prefix)) list.add(suggestion);
        }
        return list;
    }

    public static Collection<String> getExamples() {
        return EXAMPLES;
    }
}
