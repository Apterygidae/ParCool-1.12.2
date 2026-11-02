package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.Crawl;
import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 爬行的第三人称肢体动画（1.12.2 版，1:1 移植）。
 *
 * <p>纯数学（{@code sin}/{@code cos} 驱动的四肢摆动），<b>一行都没改</b> —— 只换了类型：
 * {@code PlayerEntity} → {@link EntityPlayer}，加 {@code @SideOnly(Side.CLIENT)}。</p>
 *
 * <h3>★ 它为什么能盖住 Aqua 的游泳动画</h3>
 * <p>Aqua 的 {@code ModelBipedMixin} 注入的是 <b>父类 {@code ModelBiped}</b>，
 * 而 ParCool 的 {@link com.alrex.parcool.mixin.client.ModelPlayerMixin} 注入的是
 * <b>子类 {@code ModelPlayer}</b>。而 {@code ModelPlayer#setRotationAngles} 会先调 {@code super}
 * （即 Aqua 先改），之后才轮到 ParCool（后手）—— <b>所以 ParCool 的动画天然覆盖在 Aqua 之上</b>。</p>
 * <p>这正是当初把注入点选在 {@code ModelPlayer} 而不是 {@code ModelBiped} 的收益：
 * 不需要和 Aqua 抢注入点，顺序自动是对的。</p>
 */
@SideOnly(Side.CLIENT)
public class CrawlAnimator extends Animator {

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(Crawl.class).isDoing();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float factor = (float) Math.sin(transformer.getLimbSwing() / 10 * Math.PI);
        float leftZFactor = (float) Math.cos(transformer.getLimbSwing() / 10 * Math.PI);
        float rightZFactor = -leftZFactor;
        if (leftZFactor < 0) leftZFactor /= 5;
        if (rightZFactor < 0) rightZFactor /= 5;
        transformer
                .translateLeftLeg(
                        0,
                        -0.1f + 0.8f * factor,
                        -0.6f + 0.6f * factor
                )
                .translateRightLeg(
                        0,
                        -0.1f - 0.8f * factor,
                        -0.6f - 0.6f * factor
                )
                .rotateLeftArm((float) Math.toRadians(-15 + 45 * leftZFactor), 0, (float) Math.toRadians(-130 - 30 * factor))
                .rotateRightArm((float) Math.toRadians(-15 + 45 * rightZFactor), 0, (float) Math.toRadians(130 - 30 * factor))
                .rotateLeftLeg((float) Math.toRadians(-11 + 7 * factor), 0, (float) Math.toRadians(-5 + 5 * factor))
                .rotateRightLeg((float) Math.toRadians(-11 - 7 * factor), 0, (float) Math.toRadians(5 + 5 * factor))
                .end();
    }
}
