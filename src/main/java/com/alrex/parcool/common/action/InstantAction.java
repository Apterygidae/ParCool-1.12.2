package com.alrex.parcool.common.action;

import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 「瞬时动作」基类（1.12.2 版，1:1 移植）。
 *
 * <p>语义：<b>只执行「开始」这一下，然后立刻结束</b> ——
 * {@code canContinue} 被钉死为 {@code false}，所以 {@code onStart} 跑完，
 * {@code ActionProcessor} 下一 tick 就会 {@code finish()} 它。所有「持续中 / 结束」的钩子也被钉空。</p>
 *
 * <p>典型用途：{@link com.alrex.parcool.common.action.instant.StartSwimByCrawl}
 * （按爬行键 → 进入游泳）这类「一键触发一次状态」的动作。</p>
 *
 * <h3>1.12.2 的注意点：{@code @SideOnly} 要补上</h3>
 * <p>上游 {@code InstantAction} 里这些覆写<b>都没标 {@code @OnlyIn}</b> —— 在 1.16 那样写能过，
 * 但 1.12.2 必须给客户端专属的那几个补 {@code @SideOnly(Side.CLIENT)}，
 * 否则专用服务端会去加载客户端侧的基类签名 / 被剥掉的类。</p>
 */
public abstract class InstantAction extends Action {

    @SideOnly(Side.CLIENT)
    @Override
    public final boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return false;
    }

    @Override
    public final void onWorkingTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @SideOnly(Side.CLIENT)
    @Override
    public final void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @SideOnly(Side.CLIENT)
    @Override
    public final void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @Override
    public final void onWorkingTickInServer(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @Override
    public final void onStop(EntityPlayer player) {
    }

    @Override
    public final void onStopInServer(EntityPlayer player) {
    }

    @SideOnly(Side.CLIENT)
    @Override
    public final void onStopInOtherClient(EntityPlayer player) {
    }

    @SideOnly(Side.CLIENT)
    @Override
    public final void onStopInLocalClient(EntityPlayer player) {
    }
}
