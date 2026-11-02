package com.alrex.parcool.server.command.args;

import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.command.CommandException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 「限制项名」参数的解析与补全（对应 {@code Server.Booleans/Integers/Doubles} 的 {@code Path}）。
 *
 * <p>1.12.2 无 Brigadier → 从 {@code ArgumentType<Object>}（用 {@code clazz.getEnumConstants()}
 * 建路径表）退化为<b>三个按枚举类型分开的静态查找</b>。</p>
 *
 * <p>⚠️ 上游那个 {@code Serializer implements IArgumentSerializer}（把参数类型序列化进网络包）
 * 在 1.12.2 <b>整个不需要</b> —— {@code IArgumentSerializer} 属于 Brigadier 的
 * {@code ArgumentTypes} 注册体系，1.12.2 没有。参数只在服务端本地解析，
 * 不会跨网络传输。</p>
 *
 * <p>路径表仍由枚举自身推导（{@code ConfigItem#getPath()}），
 * 所以配置项增删时命令自动跟上，不会出现"候选列表过期"。</p>
 */
public final class LimitationItemArgumentType {

    private LimitationItemArgumentType() {
    }

    public static ParCoolConfig.Server.Booleans parseBoolean(String name) throws CommandException {
        for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) {
            if (item.getPath().equals(name)) return item;
        }
        throw new CommandException("parcool.command.message.invalidConfigName", name);
    }

    public static ParCoolConfig.Server.Integers parseInteger(String name) throws CommandException {
        for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) {
            if (item.getPath().equals(name)) return item;
        }
        throw new CommandException("parcool.command.message.invalidConfigName", name);
    }

    public static ParCoolConfig.Server.Doubles parseDouble(String name) throws CommandException {
        for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) {
            if (item.getPath().equals(name)) return item;
        }
        throw new CommandException("parcool.command.message.invalidConfigName", name);
    }

    public static List<String> completeBooleans(String prefix) {
        List<String> list = new ArrayList<>();
        for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) {
            if (item.getPath().startsWith(prefix)) list.add(item.getPath());
        }
        return list;
    }

    public static List<String> completeIntegers(String prefix) {
        List<String> list = new ArrayList<>();
        for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) {
            if (item.getPath().startsWith(prefix)) list.add(item.getPath());
        }
        return list;
    }

    public static List<String> completeDoubles(String prefix) {
        List<String> list = new ArrayList<>();
        for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) {
            if (item.getPath().startsWith(prefix)) list.add(item.getPath());
        }
        return list;
    }

    public static Collection<String> getBooleanExamples() {
        List<String> list = new ArrayList<>();
        for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) {
            list.add(item.getPath());
        }
        return list;
    }

    public static Collection<String> getIntegerExamples() {
        List<String> list = new ArrayList<>();
        for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) {
            list.add(item.getPath());
        }
        return list;
    }

    public static Collection<String> getDoubleExamples() {
        List<String> list = new ArrayList<>();
        for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) {
            list.add(item.getPath());
        }
        return list;
    }
}
