package com.alrex.parcool.config;

import net.minecraftforge.common.config.Property;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * 配置项的抽象接口 —— <b>1:1 移植自 1.16.5 的 {@code ParCoolConfig.Item<T>}</b>。
 *
 * <p>这个接口是配置移植方案的核心：上游 948 行的 {@code ParCoolConfig} 里，
 * 600+ 处调用点使用的都是 {@code .get()} / {@code .set()}，而它们都经由本接口。
 * 只要接口本身不变，把底层从 {@code ForgeConfigSpec} 换成 1.12.2 的
 * {@link net.minecraftforge.common.config.Configuration} 就<b>不需要改任何调用点</b>。</p>
 *
 * <p>与上游的唯一差异：{@code register(...)} 的参数从
 * {@code ForgeConfigSpec.Builder} 换成了 1.12.2 的 {@link Property} 体系
 * （由 {@link Forge14ConfigManager} 承接），以及 {@code getInternalInstance()}
 * 返回 {@link Property} 而非 {@code ForgeConfigSpec.ConfigValue}。</p>
 *
 * <p>{@code writeToBuffer / readFromBuffer} 用于 ParCool 自己的「客户端设置 → 服务端」
 * 同步协议（{@code SyncClientInformationMessage}），与配置后端无关，因此可以原样保留。</p>
 *
 * @param <T> 配置值类型
 */
public interface ConfigItem<T> {

    T get();

    void set(T value);

    String getPath();

    /**
     * 所属分组。
     *
     * <p>注意写全限定名：{@code ConfigGroup} 是 {@link ParCoolConfig} 的嵌套枚举，
     * <b>嵌套类型即使在同一个包内也需要显式限定</b>（Java 的包不传递嵌套类型的可见性）。</p>
     */
    ParCoolConfig.ConfigGroup getGroup();

    /**
     * 底层 Platform 对象，供将来的配置 GUI 做双向绑定。
     * 1.12.2 下是 {@link Property}；未注册时为 null。
     */
    @Nullable
    Property getInternalInstance();

    /**
     * 把自己写进 1.12.2 的 {@link net.minecraftforge.common.config.Configuration}。
     * 幂等：重复调用应只更新内部引用。
     */
    void register(net.minecraftforge.common.config.Configuration configuration);

    void writeToBuffer(ByteBuffer buffer);

    T readFromBuffer(ByteBuffer buffer);
}
