package com.alrex.parcool.common.block;

import net.minecraft.block.properties.PropertyBool;

/**
 * 方块状态属性 ✓ —— 1.12.2 版。
 *
 * <p>对应上游的 {@code net.minecraft.state.BooleanProperty} ✗ ——
 * <b>1.12.2 的对应是 {@code PropertyBool}</b> ✓（在 {@code net.minecraft.block.properties} ✓，
 * 且用 {@code create(name)} 而非 {@code create(name)} 的泛型工厂 ✗）。</p>
 *
 * <h3>为什么放在公共常量类里</h3>
 * <p>1.16 的 {@code BlockStateProperties} 是**原版提供的共享常量池** ✓ ——
 * 多个方块可以复用同一个属性实例 ✓。
 * 1.12.2 <b>没有这个池</b> ✗，但属性实例本身可以共享 ✓
 * （{@code PropertyBool} 是不可变的 ✓，同一实例挂到多个方块上是安全的 ✓）</p>
 *
 * <p>目前只有铁钩用得到它 ✓（决定挂钩横竖朝向 ✓）。</p>
 */
public class BlockStateProperties {
    /**
     * 铁钩的「正交」朝向 ✓ —— 挂在天花板/地面时，钩子本身的横竖方向 ✓。
     */
    public static final PropertyBool ORTHOGONAL = PropertyBool.create("orthogonal");

    private BlockStateProperties() {
    }
}
