package com.alrex.parcool.api.unstable.animation;

import com.alrex.parcool.client.animation.Animator;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 动画信息事件（1.12.2 版，1:1 移植）。每渲染帧抛出，第三方 mod 可借此取消 ParCool 的动画。
 *
 * <p>1.12.2 差异：{@code AbstractClientPlayerEntity} → {@link AbstractClientPlayer}；
 * 基类 {@code net.minecraftforge.eventbus.api.Event} → {@code net.minecraftforge.fml.common.eventhandler.Event}。</p>
 */
@SideOnly(Side.CLIENT)
public class ParCoolAnimationInfoEvent extends Event {

    private final AbstractClientPlayer player;
    private final Animator animator;
    private final AnimationOption option;

    public ParCoolAnimationInfoEvent(AbstractClientPlayer player, Animator animator) {
        this.animator = animator;
        this.player = player;
        this.option = new AnimationOption();
    }

    public AbstractClientPlayer getPlayer() {
        return player;
    }

    public Animator getAnimator() {
        return animator;
    }

    public AnimationOption getOption() {
        return option;
    }
}
