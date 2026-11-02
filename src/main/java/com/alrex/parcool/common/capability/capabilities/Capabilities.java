package com.alrex.parcool.common.capability.capabilities;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.capability.stamina.ParCoolStamina;
import com.alrex.parcool.common.capability.storage.ParkourabilityStorage;
import com.alrex.parcool.common.capability.storage.StaminaStorage;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

import javax.annotation.Nonnull;

/**
 * ParCool 的 Capability 注册（1.12.2 版）。
 *
 * <h3>当前只接入 STAMINA</h3>
 * 上游注册三个：{@code PARKOURABILITY} / {@code IStamina} / {@code ANIMATION}。
 * 本文件目前只做 {@code IStamina}，原因：
 * <ul>
 *   <li>{@code Parkourability} 的构造与生命周期依赖 {@code ActionList}（26 个 Action 类）→ <b>P4</b></li>
 *   <li>{@code Animation} 依赖 {@code client/animation/*}（20+ Animator）→ <b>P5</b></li>
 * </ul>
 * 三者的注册写法完全一样，后两个就位后照抄即可。
 *
 * <h3>⚠️ 修正一条我此前记录错的 API 事实</h3>
 * 我之前写「1.12.2 的 {@code CapabilityManager.register} 只有两参数重载」——<b>这是错的</b>。
 * 反编译源码 {@code CapabilityManager.java} 实际有两个三参重载：
 * <pre>
 * :57  register(Class&lt;T&gt; type, IStorage&lt;T&gt; storage, final Class&lt;? extends T&gt; implementation)
 * :79  register(Class&lt;T&gt; type, IStorage&lt;T&gt; storage, Callable&lt;? extends T&gt; factory)
 * </pre>
 * 也就是说 <b>1.12.2 同样支持传入工厂</b>，上游那句
 * {@code manager.register(IStamina.class, new StaminaStorage(), ParCoolStamina::new)}
 * 在 1.12.2 可以<b>直接照搬</b>（{@code ParCoolStamina::new} 匹配无参构造 → {@code Callable}）。
 */
public class Capabilities {

    @CapabilityInject(IStamina.class)
    @Nonnull
    public static final Capability<IStamina> STAMINA_CAPABILITY = null;

    public static final ResourceLocation STAMINA_LOCATION = new ResourceLocation(ParCool.MOD_ID, "capability.stamina");

    @CapabilityInject(Parkourability.class)
    @Nonnull
    public static final Capability<Parkourability> PARKOURABILITY_CAPABILITY = null;

    public static final ResourceLocation PARKOURABILITY_LOCATION = new ResourceLocation(ParCool.MOD_ID, "capability.parkourability");

    // TODO(P5): ANIMATION_CAPABILITY + ANIMATION_LOCATION（@SideOnly(CLIENT) 注册）

    private Capabilities() {
    }

    /**
     * 注册 Capability 实现。必须在 {@code FMLPreInitializationEvent} 里调用，且<b>早于</b>
     * {@code AttachCapabilitiesEvent}（后者在实体构造时触发）。
     */
    public static void register() {
        CapabilityManager.INSTANCE.register(Parkourability.class, new ParkourabilityStorage(), Parkourability::new);
        CapabilityManager.INSTANCE.register(IStamina.class, new StaminaStorage(), ParCoolStamina::new);
    }
}
