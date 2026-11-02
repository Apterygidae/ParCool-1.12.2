package com.alrex.parcool.common.action;

import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * 「未移植动作」的占位基类（{@code canStart} 恒 {@code false}）。
 *
 * <h3>为什么要这层</h3>
 * <p>ParCool 的 26 个动作<b>互相引用</b>（{@code Crawl.canStart} 要查
 * {@code Roll}/{@code Tap}/{@code ClingToCliff}/{@code Dive}/{@code Vault}/{@code HideInBlock}/{@code RideZipline}
 * 的 {@code isDoing()}），所以「只移植一个动作」在物理上做不到 ——
 * 它引用的类必须存在。</p>
 *
 * <p>而 {@code canStart}/{@code canContinue} 恒 {@code false} 的占位<b>不会干扰真实动作的判定</b>：
 * 那些 {@code !get(Roll.class).isDoing()} 之类的条件会恒为真。</p>
 *
 * <h3>这不是我发明的过渡手段</h3>
 * <p>上游自己就这么干 —— {@code ClimbPoles}（「爬柱子」）在 1.16.5 里就是一个
 * 三个方法全返回 {@code false}/{@code None} 的空壳（26 行，功能从未实现）。</p>
 *
 * <h3>移植某个动作时怎么做</h3>
 * <p>把它从 {@code extends StubAction} 改成 {@code extends Action}，然后按上游补上
 * {@code canStart}/{@code canContinue}/{@code getStaminaConsumeTiming} 与各钩子。
 * <b>只动那一个文件，其它地方不用改。</b></p>
 */
public abstract class StubAction extends Action {

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        return false;
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }
}
