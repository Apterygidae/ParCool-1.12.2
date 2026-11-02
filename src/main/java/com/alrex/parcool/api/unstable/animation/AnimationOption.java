package com.alrex.parcool.api.unstable.animation;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.EnumMap;

/**
 * 「哪些部位可以被取消动画」的选项袋（1.12.2 版，1:1 移植）。
 *
 * <p>第三方 mod 监听 {@link ParCoolAnimationInfoEvent}，对拿到的 option 调
 * {@link #cancel(AnimationPart)} 就能让 ParCool 不去改某个部位 —— 这是上游留的兼容口子。</p>
 *
 * <p>1.12.2 差异：{@code @OnlyIn(Dist.CLIENT)} → {@code @SideOnly(Side.CLIENT)}。</p>
 */
@SideOnly(Side.CLIENT)
public class AnimationOption {

    private final EnumMap<AnimationPart, Boolean> animationMap = new EnumMap<>(AnimationPart.class);
    private boolean animationCanceled = false;

    public AnimationOption() {
        for (AnimationPart part : AnimationPart.values()) {
            animationMap.put(part, true);
        }
    }

    public void cancel(AnimationPart part) {
        animationMap.put(part, false);
    }

    public void cancelAnimation() {
        for (AnimationPart part : AnimationPart.values()) {
            animationMap.put(part, false);
        }
        animationCanceled = true;
    }

    public boolean isCanceled(AnimationPart part) {
        if (animationCanceled) return true;
        Boolean value = animationMap.get(part);
        return Boolean.FALSE.equals(value);
    }

    public boolean isAnimationCanceled() {
        return animationCanceled;
    }
}
