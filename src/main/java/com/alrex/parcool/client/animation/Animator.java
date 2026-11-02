package com.alrex.parcool.client.animation;

import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 所有动画器的抽象基类（1.12.2 版，1:1 移植）。
 *
 * <p>典型的渲染帧流程：</p>
 * <ol>
 *   <li>{@code rotatePre/rotatePost} —— 改整个模型的朝向（{@link PlayerModelRotator}）</li>
 *   <li>vanilla 的 {@code setRotationAngles} 跑完</li>
 *   <li>{@code animatePre/animatePost} —— 改各部位的角度（{@link PlayerModelTransformer}）</li>
 * </ol>
 *
 * <p>{@code animatePre} 返回 true 表示「把 vanilla 动画整个取消，由我全权控制」。</p>
 *
 * <p>1.12.2 差异：{@code PlayerEntity} → {@link EntityPlayer}；
 * {@code EntityViewRenderEvent.CameraSetup} 与 {@code TickEvent.RenderTickEvent} <b>同名存在</b>，不用改。</p>
 */
@SideOnly(Side.CLIENT)
public abstract class Animator {

    private int tick = 0;

    public void tick(EntityPlayer player) {
        tick++;
    }

    protected int getTick() {
        return tick;
    }

    /** 动画是否该被移除（条件不再成立）。 */
    public abstract boolean shouldRemoved(EntityPlayer player, Parkourability parkourability);

    /**
     * 在 vanilla 动画之前修改模型。
     *
     * @return true 表示「取消 vanilla 动画，我要全权控制渲染」
     */
    public boolean animatePre(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        return false;
    }

    /** vanilla 动画跑完之后再修改（用于「保留部分 vanilla 动作」的写法）。 */
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
    }

    public boolean rotatePre(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        return false;
    }

    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
    }

    public void onCameraSetUp(EntityViewRenderEvent.CameraSetup event, EntityPlayer clientPlayer, Parkourability parkourability) {
    }

    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
    }
}
