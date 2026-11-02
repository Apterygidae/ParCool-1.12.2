package com.alrex.parcool.common.capability.stamina;

import com.alrex.parcool.api.Effects;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 「用饥饿值当体力」的实现（1.12.2 版）。
 *
 * <p>对应上游 {@code HungerStamina}。API 改名：
 * {@code getFoodData().getFoodLevel()} → {@code getFoodStats().getFoodLevel()}，
 * {@code causeFoodExhaustion} → {@code addExhaustion}，{@code hasEffect} → {@code isPotionActive}。</p>
 *
 * <p>{@code consume()} 里的服务端无限体力判定已接回 {@code ActionInfo}。</p>
 */
public class HungerStamina implements IStamina {

    private final EntityPlayer player;
    /** 客户端累积、由 wantToConsumeOnServer() 请求服务端一次性扣除。 */
    private float consumedBuffer = 0;

    public HungerStamina(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public int getActualMaxStamina() {
        return 20;
    }

    @Override
    public int get() {
        return player.getFoodStats().getFoodLevel();
    }

    @Override
    public int getOldValue() {
        return get();
    }

    @Override
    public void consume(int value) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        if (isExhausted()
                || parkourability.getActionInfo().isStaminaInfinite(player.isSpectator() || player.isCreative())
                || player.isPotionActive(Effects.INEXHAUSTIBLE.get())
        ) return;
        consumedBuffer += value / 150f;
    }

    @Override
    public void recover(int value) {
        // 饥饿模式下体力不主动恢复，由食物系统负责
    }

    @Override
    public boolean isExhausted() {
        return get() <= 6;
    }

    @Override
    public void setExhaustion(boolean value) {
        // 饥饿模式下 exhausted 是派生状态，不单独存
    }

    @Override
    public void tick() {
        // 无需每 tick 逻辑
    }

    @Override
    public void set(int value) {
        // 饥饿模式不能直接写饥饿值
    }

    @Override
    public boolean wantToConsumeOnServer() {
        return consumedBuffer != 0f;
    }

    @Override
    public int getRequestedValueConsumedOnServer() {
        int neededValue = (int) (consumedBuffer * 10000f);
        consumedBuffer = 0f;
        return neededValue;
    }

    /**
     * 由 {@code IStamina.Type.Hunger} 注册为「服务端扣除处理器」。
     * 1.12.2 的对应 API 是 {@code EntityPlayer#addExhaustion(float)}。
     */
    public static void consumeOnServer(EntityPlayerMP player, int value) {
        player.addExhaustion(value / 10000f);
    }
}
