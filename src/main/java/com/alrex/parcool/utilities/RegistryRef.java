package com.alrex.parcool.utilities;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 1.12.2 上的 {@code RegistryObject<T>} 替身。
 *
 * <p><b>为什么要造这个类：</b>上游 1.16.5 的注册项都是 {@code RegistryObject<T>}，
 * 调用处统一写 {@code Items.ZIPLINE_ROPE.get()} / {@code SoundEvents.VAULT.get()}。
 * 1.12.2 没有 {@code RegistryObject}，如果直接把字段类型换成裸类型 {@code Item}，
 * 那么 P4（26 个 Action）与 P6（方块物品实体）里<b>每一处调用点都要删掉 {@code .get()}</b>，
 * 是几百处无意义的改动。</p>
 *
 * <p>用一个保持 {@code .get()} 语义的薄包装，可以让这些调用点<b>一行都不用改</b>。</p>
 *
 * <p>用法：注册事件的处理器里创建并在注册前 {@link #set(Object)}，之后即可安全 {@code get()}。
 * 注册前访问会抛异常而不是返回 null —— 早暴露比晚暴露好。</p>
 *
 * @param <T> 被注册的对象类型
 */
public final class RegistryRef<T> implements Supplier<T> {

    @Nullable
    private T value;

    private RegistryRef() {
    }

    public static <T> RegistryRef<T> of() {
        return new RegistryRef<>();
    }

    public static <T> RegistryRef<T> of(T initial) {
        RegistryRef<T> ref = new RegistryRef<>();
        ref.set(initial);
        return ref;
    }

    /** 由注册事件处理器调用。 */
    public void set(T value) {
        this.value = value;
    }

    public boolean isPresent() {
        return value != null;
    }

    @Override
    public T get() {
        T current = this.value;
        if (current == null) {
            throw new IllegalStateException("RegistryRef has not been populated yet. "
                    + "Registration event has not run, or this field was accessed too early.");
        }
        return current;
    }

    /** 与上游 {@code RegistryObject} 行为一致：未注册时返回 null 而不是抛异常。 */
    @Nullable
    public T orElse(@Nullable T fallback) {
        return value == null ? fallback : value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistryRef)) return false;
        return Objects.equals(value, ((RegistryRef<?>) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "RegistryRef[" + (value == null ? "<unregistered>" : value.toString()) + "]";
    }
}
