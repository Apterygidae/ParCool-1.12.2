package com.alrex.parcool.common.capability.provider;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.capabilities.Capabilities;
import com.alrex.parcool.common.capability.stamina.OtherStamina;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nullable;

/**
 * 体力的 Capability Provider（1.12.2 版）。
 *
 * <h3>与 1.16.5 的三处差异</h3>
 * <ol>
 *   <li><b>没有 {@code LazyOptional}</b>：直接持有实例，
 *       {@code getCapability} 返回 {@code (T) instance} 或 {@code null}</li>
 *   <li>{@code getCapability} 多一个 {@link EnumFacing} 参数（实体传 {@code null} 即可）</li>
 *   <li>泛型参数从 {@code CompoundNBT} → {@link NBTTagCompound}</li>
 * </ol>
 *
 * <h3>怎么区分「本地玩家」与「其他玩家」</h3>
 * <p>上游用 {@code player.isLocalPlayer()}（1.16 的 {@code PlayerEntity} 有此方法）。
 * <b>1.12.2 没有这个 API</b>，而惯用的 {@code player instanceof EntityPlayerSP} 会让本类
 * （双端加载）去解析客户端类，在专用服务端上有 {@code NoClassDefFoundError} 风险。</p>
 *
 * <p>因此这里走 {@link ParCool#PROXY}：{@code CommonProxy.isLocalPlayer} 恒返回 false，
 * 只有 {@code @SideOnly(Side.CLIENT)} 的 {@code ClientProxy} 才真正比较
 * {@code Minecraft.getMinecraft().player}。<b>由 {@code @SidedProxy} 保证服务端不会加载客户端代理。</b></p>
 *
 * <p><b>TODO(P4)</b>：上游还会按 {@code ParCoolConfig.Client.StaminaType}
 * 选择本地玩家的实现（Default / Hunger）。过渡期固定用 {@code IStamina.Type.Default}，
 * 等配置的 {@code ConfigItem<IStamina.Type>} 就位后再接。</p>
 */
public class StaminaProvider implements ICapabilitySerializable<NBTTagCompound> {

    private final IStamina instance;

    public StaminaProvider(EntityPlayer player) {
        if (ParCool.PROXY.isLocalPlayer(player)) {
            // 本地玩家的实现由客户端配置决定（Default = ParCool 自己的体力条 / Hunger = 用饥饿值）
            this.instance = ParCoolConfig.Client.getStaminaType().newInstance(player);
        } else {
            this.instance = new OtherStamina(player);
        }
    }

    /**
     * ⚠️ <b>1.12.2 特有：{@code ICapabilityProvider} 还要求实现 {@code hasCapability}。</b>
     * 1.13+ 把它删掉了，所以上游的 Provider 里没有这个方法 —— 照抄会编译失败
     * （报「未覆盖抽象方法 hasCapability(Capability&lt;?&gt;, EnumFacing)」）。
     */
    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == Capabilities.STAMINA_CAPABILITY;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == Capabilities.STAMINA_CAPABILITY) {
            return (T) instance;
        }
        return null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return (NBTTagCompound) Capabilities.STAMINA_CAPABILITY.getStorage()
                .writeNBT(Capabilities.STAMINA_CAPABILITY, instance, null);
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        Capabilities.STAMINA_CAPABILITY.getStorage()
                .readNBT(Capabilities.STAMINA_CAPABILITY, instance, null, nbt);
    }
}
