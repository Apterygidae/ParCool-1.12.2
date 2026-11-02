package com.alrex.parcool.server.limitation;

/**
 * 单个动作的限制值（不可变）。
 *
 * <p>与上游 1:1（{@code server/limitation/ActionLimitation.java}，19 行），
 * 只加了类注释。</p>
 */
public class ActionLimitation {
    private final boolean possible;
    private final int staminaConsumption;

    public ActionLimitation(boolean possible, int leastStaminaConsumption) {
        this.possible = possible;
        this.staminaConsumption = leastStaminaConsumption;
    }

    public boolean isPossible() {
        return possible;
    }

    public int getLeastStaminaConsumption() {
        return staminaConsumption;
    }
}
