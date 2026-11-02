package com.alrex.parcool.common.action;

import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 动作可依赖的「衍生计时器」（1.12.2 版，1:1 移植）。
 *
 * <p>把一堆「连续多少 tick 处于某状态」的计数集中起来，动作靠它做条件判断
 * （例如「落地超过 5 tick 才能翻滚」）。纯逻辑，无版本相关 API。</p>
 *
 * <p>1.12.2 的改名：{@code isShiftKeyDown()} → {@code isSneaking()}；
 * {@code abilities.flying} → {@code capabilities.isFlying}；
 * {@code isInWaterOrBubble()} → {@code isInWater()}（「气泡柱」是 1.13 概念）。</p>
 *
 * <p>{@code onTick} 的 {@code Parkourability} 参数上游就没用，这里保留签名以便与上游对照。</p>
 */
public class AdditionalProperties {

    private int sprintingTick = 0;
    private int notLandingTick = 0;
    private int previousNotLandingTick = Integer.MAX_VALUE;
    private int landingTick = 0;
    private int lastSprintingTick = 0;
    private int notSprintingTick = 0;
    private int notCreativeFlyingTick = 0;
    private int sneakingTick = 0;
    private int notSneakingTick = 0;
    private int lastSneakingTick = 0;
    private int inWaterTick = 0;
    private int notInWaterTick = 0;
    private int tickAfterLastJump = 0;

    public void onJump() {
        tickAfterLastJump = 0;
    }

    @SuppressWarnings("unused")
    public void onTick(EntityPlayer player, Parkourability parkourability) {
        tickAfterLastJump++;

        if (player.isSprinting()) {
            notSprintingTick = 0;
            sprintingTick++;
            lastSprintingTick = sprintingTick;
        } else {
            sprintingTick = 0;
            notSprintingTick++;
        }

        if (player.isSneaking()) {
            sneakingTick++;
            notSneakingTick = 0;
            lastSneakingTick = sneakingTick;
        } else {
            sneakingTick = 0;
            notSneakingTick++;
        }

        if (player.onGround) {
            if (notLandingTick > 0) {
                previousNotLandingTick = notLandingTick;
            }
            notLandingTick = 0;
            landingTick++;
        } else {
            notLandingTick++;
            landingTick = 0;
        }

        if (player.capabilities.isFlying) {
            notCreativeFlyingTick = 0;
        } else {
            notCreativeFlyingTick++;
        }

        if (player.isInWater()) {
            inWaterTick++;
            notInWaterTick = 0;
        } else {
            inWaterTick = 0;
            notInWaterTick++;
        }
    }

    public int getSprintingTick() {
        return sprintingTick;
    }

    public int getNotLandingTick() {
        return notLandingTick;
    }

    public int getPreviousNotLandingTick() {
        return previousNotLandingTick;
    }

    public int getLastSprintingTick() {
        return lastSprintingTick;
    }

    public int getLandingTick() {
        return landingTick;
    }

    public int getNotSprintingTick() {
        return notSprintingTick;
    }

    public int getNotCreativeFlyingTick() {
        return notCreativeFlyingTick;
    }

    public int getInWaterTick() {
        return inWaterTick;
    }

    public int getNotInWaterTick() {
        return notInWaterTick;
    }

    public int getTickAfterLastJump() {
        return tickAfterLastJump;
    }

    public int getSneakingTick() {
        return sneakingTick;
    }

    public int getNotSneakingTick() {
        return notSneakingTick;
    }

    public int getLastSneakingTick() {
        return lastSneakingTick;
    }
}
