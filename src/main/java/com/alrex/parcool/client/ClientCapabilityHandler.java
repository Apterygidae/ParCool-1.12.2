package com.alrex.parcool.client;

import com.alrex.parcool.common.capability.capabilities.ClientCapabilities;
import com.alrex.parcool.common.capability.provider.AnimationProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 客户端专属的 Capability 挂载（1.12.2 版）。
 *
 * <p>只做一件事：给玩家挂上 {@code Animation}。之所以要在客户端单独一个 handler，
 * 是因为 {@code AttachCapabilitiesEvent} 在双端都会触发，而 {@code Animation} 是纯客户端的 ——
 * 见 {@link ClientCapabilities} 的类注释。</p>
 *
 * <p>{@code PARKOURABILITY} 与 {@code IStamina} 的挂载仍在公共的
 * {@code AttachCapabilityHandler} 里（那两个是双端的）。</p>
 */
@SideOnly(Side.CLIENT)
public class ClientCapabilityHandler {

    private ClientCapabilityHandler() {
    }

    @SubscribeEvent
    public static void onAttachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof EntityPlayer)) return;
        event.addCapability(ClientCapabilities.ANIMATION_LOCATION, new AnimationProvider());
    }
}
