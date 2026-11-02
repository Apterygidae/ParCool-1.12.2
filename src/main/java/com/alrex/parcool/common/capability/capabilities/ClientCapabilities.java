package com.alrex.parcool.common.capability.capabilities;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.provider.AnimationProvider;
import com.alrex.parcool.common.capability.storage.AnimationStorage;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

/**
 * <b>客户端专属</b>的 Capability 注册（1.12.2 版）。
 *
 * <h3>为什么不放进 {@link Capabilities}</h3>
 * <p>上游把 ANIMATION 放在同一个 {@code Capabilities} 类里，只用
 * {@code @OnlyIn(Dist.CLIENT)} 标在 {@code registerClient} 方法上。1.12.2 <b>不能这么写</b>：</p>
 * <ul>
 *   <li>{@code @CapabilityInject} 的处理是「按字段的泛型参数去查已注册的 capability 并注入」，
 *       这个过程需要<b>按类名加载</b>该类型；</li>
 *   <li>而 {@code Animation} 是 {@code @SideOnly(Side.CLIENT)} 的，在专用服务端上
 *       <b>整个类会被 SideTransformer 剥掉</b>；</li>
 *   <li>→ 若把该字段留在公共类 {@code Capabilities} 里，专用服务端注入时会去加载一个不存在的类。
 *       即使不崩也是踩在边缘。</li>
 * </ul>
 *
 * <p>所以本工程把它拆到本类：<b>整个类都是 {@code @SideOnly(Side.CLIENT)}，
 * 只由 {@code ClientProxy} 调用</b> —— 由 {@code @SidedProxy} 硬保证服务端不会加载它，
 * 不依赖运行期剥离。这与 P2-A 起就定下的「客户端专属注册一律走 ClientProxy」是同一条约定。</p>
 */
@SideOnly(Side.CLIENT)
public class ClientCapabilities {

    @CapabilityInject(Animation.class)
    @Nonnull
    public static final Capability<Animation> ANIMATION_CAPABILITY = null;

    public static final ResourceLocation ANIMATION_LOCATION = new ResourceLocation(ParCool.MOD_ID, "capability.animation");

    private ClientCapabilities() {
    }

    public static void register() {
        CapabilityManager.INSTANCE.register(Animation.class, new AnimationStorage(), Animation::new);
    }

    /** 供 {@code ClientCapabilityHandler} 使用，避免两处各写一遍 null 判断。 */
    public static boolean isAnimationCapability(Capability<?> capability) {
        return capability == ANIMATION_CAPABILITY;
    }
}
